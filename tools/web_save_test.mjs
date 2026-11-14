// The editor page's setlist save (app/src/main/assets/web/index.html, the <save-logic> block), run without a browser.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const page = readFileSync(new URL("../app/src/main/assets/web/index.html", import.meta.url), "utf8");
const block = page.split("// <save-logic>")[1]?.split("// </save-logic>")[0];
assert.ok(block, "the page has no <save-logic> block");
const load = () => vm.runInNewContext(`${block}\n({ runOnce })`);

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
