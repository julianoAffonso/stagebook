// The client against the fake tablet: keys, pairing and addresses (audit 03/11).
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { StagebookClient, StagebookError } from "../dist/client.js";
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
