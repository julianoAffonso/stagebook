// The editor page's PDF trim logic (app/src/main/assets/web/index.html, the <crop-logic> block), tested without a browser: the
// block is cut out of the page and run here. The rule is the app's (domain/PdfCrops.kt, ui/editor/PdfCropDialog.kt).
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const page = readFileSync(new URL("../app/src/main/assets/web/index.html", import.meta.url), "utf8");
const block = page.split("// <crop-logic>")[1]?.split("// </crop-logic>")[0];
assert.ok(block, "the page has no <crop-logic> block");
const L = vm.runInNewContext(`${block}\n({ cropFor, cropValid, cropSet, cropClear, cropWhole, cropGrab, cropMove, CROP_ALL, cropInk, cropAround, cropSample, CROP_AIR, cropPixels })`);

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

// the automatic trim, the app's rule (domain/PdfAutoCrop.kt, PdfAutoCropTest)
const sheet = (w, h, x0, y0, x1, y1, paper = 255) => {
  const d = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4, ink = x >= x0 && x < x1 && y >= y0 && y < y1, v = ink ? 0 : paper;
    d[i] = d[i + 1] = d[i + 2] = v; d[i + 3] = 255;
  }
  return d;
};
const near = (a, b) => assert.ok(Math.abs(a - b) < 0.001, `${a} is not ${b}`);

test("the automatic frame holds what is drawn, with a little air", () => {
  const f = L.cropInk(sheet(200, 400, 40, 100, 160, 300), 200, 400);
  near(f.left, 0.2 - L.CROP_AIR); near(f.top, 0.25 - L.CROP_AIR); near(f.right, 0.8 + L.CROP_AIR); near(f.bottom, 0.75 + L.CROP_AIR);
});

test("a blank page has no frame; a scan's haze and a speck are not content", () => {
  assert.equal(L.cropInk(sheet(100, 100, 0, 0, 0, 0), 100, 100), null);
  const d = sheet(500, 500, 100, 100, 400, 400, 230);
  const i = (5 * 500 + 5) * 4; d[i] = d[i + 1] = d[i + 2] = 0;
  const f = L.cropInk(d, 500, 500);
  near(f.left, 0.2 - L.CROP_AIR); near(f.top, 0.2 - L.CROP_AIR);
});

test("one frame for several pages holds what each has; a long PDF is read at pages spread from first to last", () => {
  const a = { left: 0.2, top: 0.1, right: 0.8, bottom: 0.6 }, b = { left: 0.1, top: 0.3, right: 0.7, bottom: 0.9 };
  assert.deepEqual({ ...L.cropAround([a, null, b]) }, { left: 0.1, top: 0.1, right: 0.8, bottom: 0.9 });
  assert.equal(L.cropAround([null]), null);
  assert.deepEqual([...L.cropSample(3)], [0, 1, 2]);
  const long = L.cropSample(300);
  assert.equal(long.length, 24); assert.equal(long[0], 0); assert.equal(long[23], 299);
});

test("the preview of a trimmed page takes only the part kept, inside the picture", () => {
  assert.deepEqual({ ...L.cropPixels(crop(0, 0.25, 0.1, 0.75, 0.6), 400, 600) }, { left: 100, top: 60, width: 200, height: 300 });
  const edge = L.cropPixels(crop(0, 0.9, 0.9, 1, 1), 10, 10);
  assert.ok(edge.left + edge.width <= 10 && edge.top + edge.height <= 10 && edge.width >= 1 && edge.height >= 1);
});

test("a scan: grey paper is read by its own paper, and the dark band around the sheet is not content", () => {
  const w = 400, h = 500, d = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4, band = x < 20 || y < 20 || x >= w - 20 || y >= h - 20, ink = x >= 100 && x < 300 && y >= 150 && y < 350;
    d[i] = d[i + 1] = d[i + 2] = band ? 24 : ink ? 0 : 185; d[i + 3] = 255;
  }
  const f = L.cropInk(d, w, h);
  near(f.left, 0.25 - L.CROP_AIR); near(f.top, 0.3 - L.CROP_AIR); near(f.right, 0.75 + L.CROP_AIR); near(f.bottom, 0.7 + L.CROP_AIR);
});

test("a line that reaches the page's edge stays, and a page dark all over keeps the whole page", () => {
  const w = 200, h = 200, d = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4, ink = (x >= 60 && x < 140 && y >= 60 && y < 140) || (y >= 98 && y < 102 && x < 60);
    d[i] = d[i + 1] = d[i + 2] = ink ? 0 : 255; d[i + 3] = 255;
  }
  near(L.cropInk(d, w, h).left, 0);
  const all = L.cropInk(sheet(100, 100, 0, 0, 100, 100), 100, 100);
  assert.deepEqual([all.left, all.top, all.right, all.bottom], [0, 0, 1, 1]);
});
