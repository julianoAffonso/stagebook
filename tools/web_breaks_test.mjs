// The editor page's setlist breaks (app/src/main/assets/web/index.html, the <breaks-logic> block), tested without a
// browser: the block is cut out of the page and run here, so what is tested is what the page runs. The cases are the
// same ones as SetlistBreaksTest.kt (the tablet's rule), because the page repeats that rule for the screen.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const page = readFileSync(new URL("../app/src/main/assets/web/index.html", import.meta.url), "utf8");
const block = page.split("// <breaks-logic>")[1]?.split("// </breaks-logic>")[0];
assert.ok(block, "the page has no <breaks-logic> block");
const fill = page.match(/^const fill = .*$/m)[0];
const L = vm.runInNewContext(`${fill}\n${block}\n({ breakNorm, breakRows, breakSplit, breakMoveRow, breakMoveSong, breakAdd, breakAfterRemoving, setView, setMoveSong, setMoveBreak })`);
const plain = (x) => JSON.parse(JSON.stringify(x));

// the page's texts as the tablet sends them (English here; pt-BR has the same keys)
const t = {
  songCount0: "%d songs", songCount1: "%d song", songCountN: "%d songs", breakCount0: "%d breaks", breakCount1: "%d break", breakCountN: "%d breaks",
  setCountBreaks: "%1$s · %2$s", breakLabel: "BREAK", breakNeedsTwo: "Needs at least 2 songs",
};
const ptBr = { ...t, songCount0: "%d música", songCount1: "%d música", songCountN: "%d músicas", breakCount0: "%d intervalo", breakCount1: "%d intervalo", breakCountN: "%d intervalos", breakLabel: "INTERVALO" };
const songs = (n) => Array.from({ length: n }, (_, i) => ({ id: "s" + i, title: "S" + i, author: "a" }));
const set = (n, breaks) => ({ songs: songs(n), breaks });
const ids = (s) => s.songs.map((x) => x.id).join(" ");

test("only breaks between two songs stay, sorted and one per place", () => {
  assert.deepEqual(plain(L.breakNorm([3, 0, 1, 3, 4, -2, 9], 4)), [1, 3]);
  assert.deepEqual(plain(L.breakNorm([1], 1)), []);
  assert.deepEqual(plain(L.breakNorm([0, 1], 0)), []);
});

test("songs and breaks as rows, and back", () => {
  const rows = L.breakRows(5, [3, 1]);
  assert.deepEqual(plain(rows), [0, -1, 1, 2, -1, 3, 4]);
  assert.deepEqual(plain(L.breakSplit(rows)), { order: [0, 1, 2, 3, 4], breaks: [1, 3] });
});

test("dragging a break moves only the break", () => {
  assert.deepEqual(plain(L.breakMoveRow(4, [2], 2, 1)), { order: [0, 1, 2, 3], breaks: [1] });
  assert.deepEqual(plain(L.breakMoveRow(4, [2], 2, 0).breaks), [1]);   // to the very top: after the first song
  assert.deepEqual(plain(L.breakMoveRow(4, [2], 2, 4).breaks), [3]);   // to the very bottom: before the last
});

test("a song across a break changes its block", () => {
  assert.deepEqual(plain(L.breakMoveRow(4, [2], 4, 1)), { order: [0, 3, 1, 2], breaks: [3] });
  assert.deepEqual(plain(L.breakMoveRow(4, [2], 0, 2)), { order: [1, 0, 2, 3], breaks: [1] });
  assert.deepEqual(plain(L.breakMoveRow(4, [2], 9, 0)), { order: [0, 1, 2, 3], breaks: [2] });
});

test("moving a song by its place among the songs keeps the break between its neighbours", () => {
  assert.deepEqual(plain(L.breakMoveSong(4, [2], 0, 3)), { order: [1, 2, 3, 0], breaks: [1] });
  assert.deepEqual(plain(L.breakMoveSong(4, [2], 3, 0)), { order: [3, 0, 1, 2], breaks: [3] });
  assert.deepEqual(plain(L.breakMoveSong(4, [2], 0, 4)), { order: [0, 1, 2, 3], breaks: [2] });
});

test("a new break goes before the last song, and needs two songs", () => {
  assert.deepEqual(plain(L.breakAdd([], 4)), [3]);
  assert.deepEqual(plain(L.breakAdd([3], 4)), [2, 3]);
  assert.deepEqual(plain(L.breakAdd([], 1)), []);
  assert.deepEqual(plain(L.breakAdd([1], 2)), [1]);
});

test("taking songs out keeps each break before the same song", () => {
  assert.deepEqual(plain(L.breakAfterRemoving([1, 3], 5, [1])), [1, 2]);
  assert.deepEqual(plain(L.breakAfterRemoving([1, 3], 5, [0])), [2]);
  assert.deepEqual(plain(L.breakAfterRemoving([1, 3], 5, [3, 4])), [1]);
  assert.deepEqual(plain(L.breakAfterRemoving([1, 3], 5, [1, 2])), [1]);
});

// ---- what the page draws (setView feeds drawSet) ----
test("the setlist draws a break row between the two songs, with the label and arrows", () => {
  const v = L.setView(set(4, [2]), t);
  assert.deepEqual(plain(v.rows.map((r) => (r.kind === "break" ? r.label : r.n))), [1, 2, "BREAK", 3, 4]);
  const brk = v.rows[2];
  assert.deepEqual(plain({ up: brk.up, down: brk.down }), { up: true, down: true });
  assert.equal(L.setView(set(4, [2]), ptBr).rows[2].label, "INTERVALO");
});

test("a break right after the first song can't go up, and right before the last can't go down", () => {
  assert.equal(L.setView(set(4, [1]), t).rows[1].up, false);
  assert.equal(L.setView(set(4, [3]), t).rows[3].down, false);
});

test("the summary counts songs and breaks, in both languages", () => {
  assert.equal(L.setView(set(4, [2]), t).summary, "4 songs · 1 break");
  assert.equal(L.setView(set(4, [1, 3]), t).summary, "4 songs · 2 breaks");
  assert.equal(L.setView(set(4, [2]), ptBr).summary, "4 músicas · 1 intervalo");
  assert.equal(L.setView(set(5, [1, 3]), ptBr).summary, "5 músicas · 2 intervalos");
  assert.equal(L.setView(set(4, []), t).summary, "4 songs");
  assert.equal(L.setView(set(1, []), ptBr).summary, "1 música");
});

test("the + Break button is off with fewer than 2 songs (and says why), and when every place has one", () => {
  for (const n of [0, 1]) {
    const v = L.setView(set(n, []), t);
    assert.equal(v.addOff, true);
    assert.equal(v.addTip, "Needs at least 2 songs");
  }
  assert.equal(L.setView(set(2, []), t).addOff, false);
  assert.equal(L.setView(set(2, []), t).addTip, "");
  assert.equal(L.setView(set(2, [1]), t).addOff, true);
  assert.equal(L.setView(set(2, [1]), t).addTip, "");
});

test("a song moves by trading places with the neighbour, a break by trading with a song", () => {
  const s = set(4, [2]);   // S0 S1 | S2 S3
  L.setMoveSong(s, 1, 2);   // S1 down: past the break and S2
  assert.equal(ids(s), "s0 s2 s1 s3");
  assert.deepEqual(plain(s.breaks), [1]);
  const b = set(4, [2]);
  L.setMoveBreak(b, 2, 1);   // the break down: after S2
  assert.equal(ids(b), "s0 s1 s2 s3");
  assert.deepEqual(plain(b.breaks), [3]);
  L.setMoveBreak(b, 3, -1);
  L.setMoveBreak(b, 2, -1);
  assert.deepEqual(plain(b.breaks), [1]);
});
