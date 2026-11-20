// The editor page's song rows (app/src/main/assets/web/index.html, the <rows-logic> block), tested without a browser:
// the block is cut out of the page and run here, so what is tested is what the page runs. The rule is the device's
// (ui/songs/RowFacts.kt read by ui/components/SongFacts.kt): key, tempo, capo and the wave of a backing track, only what
// the song has. The texts are the app's own, read from its strings.xml in both languages.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const read = (p) => readFileSync(new URL(p, import.meta.url), "utf8");
const page = read("../app/src/main/assets/web/index.html");
const block = page.split("// <rows-logic>")[1]?.split("// </rows-logic>")[0];
assert.ok(block, "the page has no <rows-logic> block");
const fill = page.match(/^const fill = .*$/m)[0];
const L = vm.runInNewContext(`${fill}\n${block}\n({ rowFacts, rowLabel })`);

const strings = (dir) => {
  const xml = read(`../app/src/main/res/${dir}/strings.xml`);
  const get = (name) => xml.match(new RegExp(`<string name="${name}">([^<]*)</string>`))[1].replace(/\\'/g, "'");
  return { factBpm: get("set_bpm"), factCapo: get("editor_data_capo"), track: get("stage_backing_track"), pdf: get("song_is_pdf") };
};
const en = strings("values");
const pt = strings("values-pt-rBR");
const song = (o = {}) => ({ title: "Evidências", author: "Chitãozinho & Xororó", key: "", bpm: 0, capo: 0, hasTrack: false, ...o });

test("key, tempo and capo in that order, joined with a dot", () => {
  assert.equal(L.rowFacts(song({ key: "G", bpm: 72, capo: 2 }), en).text, "G · 72 bpm · capo 2");
  assert.equal(L.rowFacts(song({ key: "Em", bpm: 120, capo: 2, hasTrack: true }), pt).text, "Em · 120 bpm · capo 2");
});

test("a PDF song says so in the label, before the backing track", () => {
  const f = L.rowFacts(song({ key: "G", hasPdf: true, hasTrack: true }), en);
  assert.equal(f.hasPdf, true);
  assert.equal(f.label, "G · PDF · Backing track");
  assert.equal(L.rowFacts(song({ key: "G" }), en).hasPdf, false);
  assert.equal(L.rowFacts(song({ hasPdf: true }), pt).label, "PDF");
});

test("only what the song has is shown", () => {
  assert.equal(L.rowFacts(song({ key: "D", bpm: 96 }), en).text, "D · 96 bpm");
  assert.equal(L.rowFacts(song({ key: "A" }), en).text, "A");
  assert.equal(L.rowFacts(song({ bpm: 84 }), en).text, "84 bpm");   // no key: the tempo leads
  assert.equal(L.rowFacts(song({ key: "G", capo: 3 }), en).text, "G · capo 3");
  assert.deepEqual(JSON.parse(JSON.stringify(L.rowFacts(song({ capo: 4 }), en).parts)), ["capo 4"]);
});

test("a capo of 0 and a tempo of 0 are none; a song with nothing has no facts at all", () => {
  const f = L.rowFacts(song({ key: "G", bpm: 0, capo: 0 }), en);
  assert.equal(f.text, "G");
  const none = L.rowFacts(song(), en);
  assert.deepEqual(JSON.parse(JSON.stringify(none)), { key: "", parts: [], hasTrack: false, hasPdf: false, text: "", label: "" });
});

test("a backing track alone still shows its wave, and says it", () => {
  const f = L.rowFacts(song({ hasTrack: true }), en);
  assert.equal(f.text, "");
  assert.equal(f.hasTrack, true);
  assert.equal(f.label, "Backing track");
  assert.equal(L.rowFacts(song({ key: "G", hasTrack: true }), pt).label, "G · Backing track");
});

test("the row's accessible name has the title, the artist and the facts", () => {
  assert.equal(L.rowLabel(song({ key: "G", bpm: 72, capo: 2, hasTrack: true }), en), "Evidências, Chitãozinho & Xororó, G · 72 bpm · capo 2 · Backing track");
  assert.equal(L.rowLabel(song(), pt), "Evidências, Chitãozinho & Xororó");
  assert.equal(L.rowLabel({ title: "", author: "" }, en), "–");   // a song the library doesn't have
  assert.equal(L.rowLabel({ title: "Veio", artist: "D", key: "A" }, en), "Veio, D, A");   // a review row names its artist "artist"
});

test("every list draws its songs with the one row, and the song's year and tags are not in it", () => {
  const fn = (name) => page.split(new RegExp(`^(?:async )?function ${name}\\(`, "m"))[1]?.split(/^}/m)[0] ?? "";
  for (const name of ["drawList", "drawSet", "drawSetAdd", "showReview"]) assert.match(fn(name), /songRow\(/, `${name} doesn't use songRow`);
  assert.doesNotMatch(fn("drawList"), /s\.year|s\.tags\)\.join|\.tags \|\| \[\]\)\.join/);
});

test("the server hands the page the app's texts the row needs", () => {
  const server = read("../app/src/main/java/br/app/stagebook/data/web/WebEditorServer.kt");
  assert.match(server, /"factBpm" to R\.string\.set_bpm/);
  assert.match(server, /"factCapo" to R\.string\.editor_data_capo/);
  assert.match(server, /"track" to R\.string\.stage_backing_track/);
});

// ---- most and least played (the <plays-logic> block; domain/PlayStats.kt on the device) ----
const playsBlock = page.split("// <plays-logic>")[1]?.split("// </plays-logic>")[0];
assert.ok(playsBlock, "the page has no <plays-logic> block");
const pluralLine = page.match(/^const plural = .*$/m)[0];
const P = vm.runInNewContext(`${fill}\n${pluralLine}\n${playsBlock}\n({ playsShown, playsSorted, playsLine })`);
const playsTexts = (dir) => {
  const xml = read(`../app/src/main/res/${dir}/strings.xml`);
  const get = (name) => xml.match(new RegExp(`<string name="${name}">([^<]*)</string>`))[1];
  const pl = (name, q) => xml.match(new RegExp(`<plurals name="${name}">[\\s\\S]*?<item quantity="${q}">([^<]*)</item>`))[1];
  const forms = (key, name) => ({ [key + "0"]: pl(name, dir === "values" ? "other" : "one"), [key + "1"]: pl(name, "one"), [key + "N"]: pl(name, "other") });
  return {
    neverPlayed: get("songs_never_played"), playedToday: get("songs_played_today"), playedYesterday: get("songs_played_yesterday"), playsLine: get("songs_plays_line"),
    ...forms("plays", "songs_plays"), ...forms("playedDays", "songs_played_days"), ...forms("playedWeeks", "songs_played_weeks"), ...forms("playedMonths", "songs_played_months"),
  };
};
const pten = playsTexts("values"), ptpt = playsTexts("values-pt-rBR");

test("the play line is the app's, in both languages", () => {
  assert.equal(P.playsLine({ plays: 18, playedDays: 2 }, ptpt), "18 vezes · última há 2 dias");
  assert.equal(P.playsLine({ plays: 1, playedDays: 1 }, ptpt), "1 vez · última ontem");
  assert.equal(P.playsLine({ plays: 3, playedDays: 20 }, pten), "3 times · last 2 weeks ago");
  assert.equal(P.playsLine({ plays: 0, playedDays: -1 }, ptpt), "Nunca tocada");
});

test("show the last 30 days or the never played, and sort by plays", () => {
  const list = [{ title: "A", plays: 2, playedDays: 18 }, { title: "B", plays: 9, playedDays: 9 }, { title: "C", plays: 0, playedDays: -1 }, { title: "D", plays: 2, playedDays: 31 }];
  assert.deepEqual(list.filter((s) => P.playsShown(s, "recent")).map((s) => s.title), ["A", "B"]);
  assert.deepEqual(list.filter((s) => P.playsShown(s, "never")).map((s) => s.title), ["C"]);
  assert.deepEqual([...P.playsSorted(list, "most")].map((s) => s.title), ["B", "A", "D", "C"]);
  assert.deepEqual([...P.playsSorted(list, "least")].map((s) => s.title), ["C", "D", "A", "B"]);
  assert.deepEqual([...P.playsSorted(list, "az")].map((s) => s.title), ["A", "B", "C", "D"]);
});
