// The client against the fake tablet: keys, pairing and addresses (audit 03/11).
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { StagebookClient, StagebookError, answers, save } from "../dist/client.js";
import { fakeTablet } from "./fake-tablet.mjs";

let tablet;
before(async () => {
  process.env.STAGEBOOK_CONFIG = join(mkdtempSync(join(tmpdir(), "sbmcp-c-")), "config.json");
  tablet = await fakeTablet();
});
after(() => tablet.server.close());

test("a refused key is dropped, and connecting again waits for the new approval", async () => {
  const c = new StagebookClient(tablet.url, "old-key");
  await assert.rejects(c.get("/api/session"), (e) => e instanceof StagebookError && e.status === 401);
  assert.equal(c.key, undefined);
  const code = await c.startPair("test");
  assert.equal(code, "4821");
  const s = await c.get("/api/session");   // waits for Allow instead of sending the old key
  assert.equal(s.app, "TCL");
  assert.equal(c.key, "k123");
});

test("a saved address that no longer answers is told apart from a live one", async () => {
  assert.equal(await answers(tablet.url), true);
  assert.equal(await answers("http://127.0.0.1:1"), false);   // nothing listens there: connect searches again
});

test("a tablet already showing a request says so in words", async () => {
  const busy = await fakeTablet({ busy: true });
  try {
    const c = new StagebookClient(busy.url, undefined);
    await assert.rejects(c.startPair("test"), (e) => e.status === 429 && /already showing a request/.test(e.message));
  } finally { busy.server.close(); }
});

test("connecting twice waits for one approval, and both calls get in", async () => {
  const fresh = await fakeTablet();
  try {
    const c = new StagebookClient(fresh.url, undefined);
    await Promise.all([c.startPair("a"), c.startPair("b")]);
    const [x, y] = await Promise.all([c.get("/api/session"), c.get("/api/session")]);
    assert.equal(x.app, "TCL");
    assert.equal(y.app, "TCL");
  } finally { fresh.server.close(); }
});

test("the saved key is readable only by this user, even in a file that already existed", async () => {
  const file = join(mkdtempSync(join(tmpdir(), "sbmcp-k-")), "config.json");
  writeFileSync(file, "{}", { mode: 0o644 });
  const before = process.env.STAGEBOOK_CONFIG;
  process.env.STAGEBOOK_CONFIG = file;
  try {
    await save({ url: "http://x", key: "k" });
    assert.equal(statSync(file).mode & 0o777, 0o600);
  } finally { process.env.STAGEBOOK_CONFIG = before; }
});

test("a call the assistant gave up on stops", async () => {
  const c = new StagebookClient(tablet.url, "k123");
  const stop = new AbortController();
  stop.abort();
  await assert.rejects(c.send("GET", "/api/session", undefined, undefined, 15000, stop.signal), /cancelled/);
});
