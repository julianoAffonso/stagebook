// End to end: a real MCP client calls the tools; the fake tablet answers like Stagebook does.
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { StagebookClient } from "../dist/client.js";
import { buildServer } from "../dist/server.js";
import { fakeTablet } from "./fake-tablet.mjs";

let tablet, mcp, dir;
const call = async (name, args = {}) => {
  const r = await mcp.callTool({ name, arguments: args });
  const body = r.content[0].text;
  return { error: !!r.isError, body, json: (() => { try { return JSON.parse(body); } catch { return body; } })() };
};

before(async () => {
  dir = mkdtempSync(join(tmpdir(), "sbmcp-"));
  process.env.STAGEBOOK_CONFIG = join(dir, "config.json");
  tablet = await fakeTablet();
  const server = buildServer(new StagebookClient(undefined, undefined));
  const [a, b] = InMemoryTransport.createLinkedPair();
  await server.connect(a);
  mcp = new Client({ name: "test", version: "1" });
  await mcp.connect(b);
});
after(() => tablet.server.close());

test("lists every tool, with deletes marked destructive", async () => {
  const { tools } = await mcp.listTools();
  const names = tools.map((t) => t.name);
  for (const n of ["stagebook_connect", "list_songs", "get_song", "create_song", "update_song", "delete_song", "transpose_song", "set_capo", "import_song", "list_setlists", "setlist_order", "create_setlist", "update_setlist", "delete_setlist", "receive_by_code", "band_inbox", "restore_backup", "set_backing_track"]) assert.ok(names.includes(n), n);
  assert.equal(tools.find((t) => t.name === "delete_song").annotations.destructiveHint, true);
});

test("before connecting, a call explains what to do instead of failing", async () => {
  const r = await call("list_songs");
  assert.equal(r.error, true);
  assert.match(r.body, /Edit on the computer/);
});

test("connect pairs with the tablet and saves the key", async () => {
  const r = await call("stagebook_connect", { url: tablet.url });
  assert.equal(r.error, false, r.body);
  assert.match(r.body, /4821/);
  // the next call waits for the approval (the fake tablet approves on the second poll)
  const s = await call("stagebook_status");
  assert.equal(s.json.device, "TCL");
});

test("songs: list with filters, read without and with the text", async () => {
  assert.equal((await call("list_songs")).json.count, 2);
  assert.equal((await call("list_songs", { query: "made up" })).json.songs[0].id, "s2");
  assert.equal((await call("list_songs", { tag: "Nacional" })).json.count, 1);
  const song = (await call("get_song", { id: "s1" })).json;
  assert.equal(song.text, undefined);
  assert.deepEqual(song.chords, ["G", "D"]);
  assert.match((await call("get_song", { id: "s1", include_text: true })).json.text, /Linha um/);
});

test("songs: create, refuse a repeat, update a field, transpose", async () => {
  const id = (await call("create_song", { title: "Nova", author: "Banda", text: "[C]la la" })).json.created;
  assert.ok(id);
  const again = await call("create_song", { title: "Nova", author: "Banda", text: "[C]la" });
  assert.equal(again.error, true);
  assert.match(again.body, /already a song/);
  await call("update_song", { id, tempo: "98" });
  const s = (await call("get_song", { id, include_text: true })).json;
  assert.equal(s.tempo, "98");
  assert.equal(s.text, "[C]la la");
  const t = (await call("transpose_song", { id: "s1", semitones: 2 })).json;
  assert.equal(t.to, "A");
  assert.match(tablet.songs.get("s1").text, /\[A\]Linha um/);
});

test("deleting needs confirm=true", async () => {
  const no = await mcp.callTool({ name: "delete_song", arguments: { id: "s2" } });
  assert.equal(no.isError, true);
  assert.ok(tablet.songs.has("s2"));
  const yes = await call("delete_song", { id: "s2", confirm: true });
  assert.equal(yes.error, false);
  assert.ok(!tablet.songs.has("s2"));
});

test("setlists: create, read, update keeps what wasn't passed", async () => {
  const id = (await call("create_setlist", { name: "Sunday", song_ids: ["s1"] })).json.created;
  assert.equal((await call("get_setlist", { id })).json.songs[0].title, "Morning Road");
  await call("update_setlist", { id, name: "Sunday night" });
  assert.deepEqual(tablet.sets.get(id).items, ["s1"]);
  assert.equal(tablet.sets.get(id).name, "Sunday night");
});

test("setlists: breaks are read, created, replaced and kept", async () => {
  const id = (await call("create_setlist", { name: "Two sets", song_ids: ["s1", "s1", "s1"], breaks: [2] })).json.created;
  assert.deepEqual(tablet.sets.get(id).breaks, [2]);
  assert.deepEqual((await call("get_setlist", { id })).json.breaks, [2]);
  // without breaks: the existing ones stay, and they are not sent
  await call("update_setlist", { id, name: "Renamed" });
  assert.deepEqual(tablet.sets.get(id).breaks, [2]);
  await call("update_setlist", { id, breaks: [1, 2] });
  assert.deepEqual(tablet.sets.get(id).breaks, [1, 2]);
  await call("update_setlist", { id, breaks: [] });
  assert.deepEqual(tablet.sets.get(id).breaks, []);
  // created without breaks: none
  const plain = (await call("create_setlist", { name: "Plain", song_ids: ["s1", "s1"] })).json.created;
  assert.deepEqual(tablet.sets.get(plain).breaks, []);
});

test("setlists: breaks that don't fit are refused with a clear message", async () => {
  const id = (await call("create_setlist", { name: "Short", song_ids: ["s1", "s1", "s1"], breaks: [1] })).json.created;
  for (const bad of [[0], [3], [1.5], [-1], [2, 1], [1, 1]]) {
    const r = await call("update_setlist", { id, breaks: bad });
    assert.equal(r.error, true, JSON.stringify(bad));
    assert.match(r.body, /[Bb]reak/);
  }
  // checked against the new songs when they come with it
  assert.equal((await call("update_setlist", { id, song_ids: ["s1", "s1"], breaks: [2] })).error, true);
  assert.equal((await call("create_setlist", { name: "Lone", song_ids: ["s1"], breaks: [1] })).error, true);
  assert.deepEqual(tablet.sets.get(id).breaks, [1]);
});

test("backing track upload sends the file with its name", async () => {
  const f = join(dir, "faixa.ogg");
  writeFileSync(f, Buffer.alloc(1024, 1));
  const r = await call("set_backing_track", { id: "s1", path: f });
  assert.equal(r.error, false, r.body);
  assert.equal(tablet.songs.get("s1").trackName, "faixa.ogg");
});

test("resources: the song list and a song as ChordPro", async () => {
  const list = await mcp.readResource({ uri: "stagebook://songs" });
  assert.ok(JSON.parse(list.contents[0].text).length >= 1);
  const song = await mcp.readResource({ uri: "stagebook://songs/s1" });
  assert.match(song.contents[0].text, /\{title: Morning Road\}/);
  assert.match(song.contents[0].text, /\{key: A\}/);
});

test("prompts are listed", async () => {
  const { prompts } = await mcp.listPrompts();
  assert.deepEqual(prompts.map((p) => p.name).sort(), ["build_setlist", "fix_chords", "transpose_for_singer"]);
});

test("restoring settings needs confirm=true: they replace the tablet's", async () => {
  const file = join(dir, "backup.stagebook");
  writeFileSync(file, "zip");
  const refused = await call("restore_backup", { path: file, parts: ["settings"] });
  assert.equal(refused.error, true);
  assert.match(refused.body, /confirm=true/);
  assert.equal(tablet.restores.length, 0);
  const done = await call("restore_backup", { path: file, parts: ["settings"], confirm: true });
  assert.equal(done.error, false);
  assert.deepEqual(tablet.restores.at(-1).parts, ["settings"]);
  const adding = await call("restore_backup", { path: file, parts: ["library"] });   // adding songs needs no confirm
  assert.equal(adding.error, false);
});

test("emptying a setlist needs confirm=true", async () => {
  const refused = await call("update_setlist", { id: "t1", song_ids: [] });
  assert.equal(refused.error, true);
  assert.ok(tablet.sets.get("t1").items.length > 0);
  const done = await call("update_setlist", { id: "t1", song_ids: [], confirm: true });
  assert.equal(done.error, false);
  assert.equal(tablet.sets.get("t1").items.length, 0);
});

test("upload paths: ~ is the home folder, relative paths and oversize files are refused", async () => {
  const { localFile } = await import("../dist/server.js");
  const home = process.env.HOME;
  process.env.HOME = dir;
  try {
    writeFileSync(join(dir, "song.mp3"), "abc");
    assert.equal((await localFile("~/song.mp3", 10)).length, 3);
    await assert.rejects(localFile("song.mp3", 10), /full path/);
    await assert.rejects(localFile("~/song.mp3", 2), /up to/);
    await assert.rejects(localFile("~/missing.mp3", 10), /no file/);
  } finally { process.env.HOME = home; }
});

test("capo is saved with the song", async () => {
  // s1: s2 was deleted by an earlier test
  const r = await call("set_capo", { id: "s1", capo: 3 });
  assert.equal(r.error, false, r.body);
  assert.equal(r.json.capo, 3);
  assert.equal(tablet.songs.get("s1").capo, 3);
});

test("setlists filter by name and their order reads and changes", async () => {
  const made = await call("create_setlist", { name: "Sábado à noite" });
  assert.equal(made.error, false);
  const found = JSON.parse((await call("list_setlists", { query: "SABADO" })).body);
  assert.deepEqual(found.map((s) => s.name), ["Sábado à noite"]);
  assert.equal(JSON.parse((await call("setlist_order")).body).order, "newest");
  assert.equal(JSON.parse((await call("setlist_order", { order: "abc" })).body).order, "abc");
  assert.equal(JSON.parse((await call("setlist_order")).body).order, "abc");
});
