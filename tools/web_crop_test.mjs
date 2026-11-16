// The editor page's PDF trim logic (app/src/main/assets/web/index.html, the <crop-logic> block), tested without a browser: the
// block is cut out of the page and run here. The rule is the app's (domain/PdfCrops.kt, ui/editor/PdfCropDialog.kt).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const page = readFileSync(new URL("../app/src/main/assets/web/index.html", import.meta.url), "utf8");
const block = page.split("// <crop-logic>")[1]?.split("// </crop-logic>")[0];
assert.ok(block, "the page has no <crop-logic> block");
const L = vm.runInNewContext(`${block}\n({ cropFor, cropValid, cropSet, cropClear, cropWhole, cropGrab, cropMove, CROP_ALL })`);

const crop = (page, left = 0, top = 0, right = 1, bottom = 1) => ({ page, left, top, right, bottom });

test("a page takes its own trim, else the one for all pages, else none", () => {
  const all = crop(-1, 0.1, 0, 0.9, 1), own = crop(2, 0, 0.3, 1, 1);
  assert.equal(L.cropFor([all, own], 2), own);
  assert.equal(L.cropFor([all, own], 0), all);
  assert.equal(L.cropFor([], 0), null);
});

test("a trim is kept inside the page and at least a tenth on each side", () => {
  const v = L.cropValid(crop(0, -0.5, 0.95, 0.02, 2));
  assert.deepEqual([v.left, v.top, v.right, v.bottom].map((x) => Math.round(x * 100) / 100), [0, 0.9, 0.1, 1]);
});

test("setting a whole page takes the old trim off, setting another replaces the one of its page and leaves the rest", () => {
  const a = crop(0, 0.2), b = crop(1, 0.3);
  assert.deepEqual(L.cropSet([crop(-1, 0.1, 0, 0.9, 1)], crop(-1)), []);
  const out = L.cropSet([a, b], crop(0, 0, 0.4));
  assert.equal(out.length, 2);
  assert.equal(out.find((c) => c.page === 0).top, 0.4);
  assert.deepEqual(L.cropClear([a, b], 0), [b]);
});

test("a touch picks the edges it is on, a corner two, the inside all, the outside none", () => {
  const c = crop(0, 0.2, 0.2, 0.8, 0.8), w = 100, h = 200, slop = 6;
  assert.equal(L.cropGrab(20, 100, c, w, h, slop), 1);          // the left edge
  assert.equal(L.cropGrab(80, 40, c, w, h, slop), 4 | 2);       // the top right corner
  assert.equal(L.cropGrab(50, 100, c, w, h, slop), 15);         // inside
  assert.equal(L.cropGrab(5, 5, c, w, h, slop), 0);             // outside
});

test("dragging an edge leaves the others, and the next drag starts from the new frame", () => {
  let c = crop(0);
  c = L.cropMove(c, 1, 0.3, 0);   // left edge in by 30%
  c = L.cropMove(c, 4, -0.3, 0);  // then the right edge in by 30%: the left one is still there
  assert.deepEqual([c.left, c.right].map((x) => Math.round(x * 100) / 100), [0.3, 0.7]);
});

test("an edge cannot cross the opposite one, and the whole frame stays on the page", () => {
  let c = L.cropMove(crop(0), 1, 5, 0);
  assert.ok(Math.abs(c.right - c.left - 0.1) < 1e-9, "least size kept");
  c = L.cropMove(crop(0, 0.2, 0.2, 0.6, 0.6), 15, 5, -5);
  assert.deepEqual([c.left, c.top, c.right, c.bottom].map((x) => Math.round(x * 100) / 100), [0.6, 0, 1, 0.4]);
});

test("an edge counts only along its own length: a touch in the empty beside a narrow frame is on nothing", () => {
  const c = crop(0, 0.4, 0.4, 0.46, 0.9), w = 1000, h = 1000, slop = 36;
  // the frame is 60 px wide: far above it, level with its left edge's x, is empty page
  assert.equal(L.cropGrab(400, 100, c, w, h, slop), 0);
  // on the edge itself, and at a corner, it still grabs
  assert.equal(L.cropGrab(400, 600, c, w, h, slop), 1);
  assert.equal(L.cropGrab(460, 400, c, w, h, slop), 4 | 2);
  // a frame narrower than twice the slack: the left and right edges are both near, so it is two edges, not a miss
  assert.equal(L.cropGrab(430, 600, c, w, h, slop), 1 | 4);
});
