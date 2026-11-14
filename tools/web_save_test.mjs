// The editor page's setlist save (app/src/main/assets/web/index.html, the <save-logic> block), run without a browser.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const page = readFileSync(new URL("../app/src/main/assets/web/index.html", import.meta.url), "utf8");
const block = page.split("// <save-logic>")[1]?.split("// </save-logic>")[0];
assert.ok(block, "the page has no <save-logic> block");
const breakNorm = page.match(/^const breakNorm = .*$/m)[0];   // the page's own rule for which breaks stay
const load = () => vm.runInNewContext(`${breakNorm}\n${block}\n({ runOnce, setBody, saveProblem })`);
const L = load;

test("two saves before the first answer make one request", async () => {
  const { runOnce } = load();
  let posts = 0, release;
  const gate = new Promise((r) => (release = r));
  const run = async () => { posts++; await gate; };
  const states = [];
  const a = runOnce(run, (b) => states.push(b));
  const b = runOnce(run, (b2) => states.push(b2));
  release();
  await Promise.all([a, b]);
  assert.equal(posts, 1);
  assert.deepEqual(states, [true, false]);   // the button off, then on again
  await runOnce(run, () => {});
  assert.equal(posts, 2);                    // and a later save goes through
});

test("the button comes back when the save fails", async () => {
  const { runOnce } = load();
  const states = [];
  await assert.rejects(runOnce(async () => { throw new Error("x"); }, (b) => states.push(b)));
  assert.deepEqual(states, [true, false]);
  assert.equal(await runOnce(async () => 7, () => {}), 7);
});

const set = { songs: [{ id: "a" }, { id: "b" }, { id: "c" }], breaks: [2, 9] };

test("renaming without touching the breaks does not send them, so the tablet keeps its own", () => {
  const body = L().setBody("New name", set, false);
  assert.deepEqual(JSON.parse(JSON.stringify(body)), { name: "New name", items: ["a", "b", "c"] });
  assert.ok(!("breaks" in body));
});

test("touching the breaks sends them, normalised", () => {
  assert.deepEqual(JSON.parse(JSON.stringify(L().setBody("N", set, true).breaks)), [2]);
  assert.deepEqual(JSON.parse(JSON.stringify(L().setBody("N", { songs: [{ id: "a" }], breaks: [] }, true).breaks)), []);
});

test("the page flags the breaks as touched in the buttons and on a new setlist", () => {
  assert.match(page, /b\.onclick = \(\) => \{ onclick\(\); breaksTouched = true;/);
  assert.match(page, /\$\("set-break-add"\)\.onclick = \(\) => \{ breaksTouched = true;/);
  assert.match(page, /breaksTouched = false;\n  drawSet\(\);/);
});

test("a 409 says why; any other error says it was not saved, not 'offline'", () => {
  const texts = { saveFailed: "not saved" };
  const known = { bad_breaks: "breaks do not fit" };
  assert.equal(L().saveProblem(409, "bad_breaks", texts, known), "breaks do not fit");
  assert.equal(L().saveProblem(409, "other", texts, known), "not saved");
  for (const s of [400, 404, 500, 503]) assert.equal(L().saveProblem(s, undefined, texts, known), "not saved");
});
