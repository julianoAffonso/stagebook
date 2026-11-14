// The MCP side: tools, resources and prompts over the tablet's API (client.ts). Every write goes through the same rules
// as the app's editor (the tablet applies them), and the stage on the tablet follows right away.

import { readFileSync } from "node:fs";
import { readFile, stat } from "node:fs/promises";
import { homedir } from "node:os";
import { basename, isAbsolute, join } from "node:path";
import { McpServer, ResourceTemplate } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { StagebookClient, StagebookError, answers, discoverAll, save } from "./client.js";

// the one in package.json (it ships beside dist/), so the two never disagree
export const VERSION: string = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8")).version;

type Song = {
  id: string; title: string; author: string; year: string; tempo: string; text: string;
  track: string; trackName: string; key: string; capo: number; tags: string[];
};
type SongItem = { id: string; title: string; author: string; year: string; key: string; tags: string[]; bpm?: number; capo?: number; hasTrack?: boolean };

const text = (value: unknown) => ({ content: [{ type: "text" as const, text: typeof value === "string" ? value : JSON.stringify(value, null, 2) }] });

// every tool answers a StagebookError as a readable message instead of failing the call. [signal]: the assistant gave up
// on the call (it waits ~60 s); long calls pass it on, so an upload or a restore stops instead of finishing unseen
// after the assistant was told it failed (audit 03/11)
function safe<A>(run: (args: A, signal?: AbortSignal) => Promise<ReturnType<typeof text>>) {
  return async (args: A, extra?: { signal?: AbortSignal }) => {
    try {
      return await run(args, extra?.signal);
    } catch (e) {
      const msg = e instanceof StagebookError ? e.message : `Unexpected error: ${(e as Error).message}`;
      return { ...text(msg), isError: true };
    }
  };
}

/** A song for the assistant: without the lyrics unless asked (they are often long). */
function songOut(s: Song, withText: boolean) {
  const { text: lyrics, ...rest } = s;
  return withText ? s : { ...rest, lines: lyrics.split("\n").length, chords: chordsIn(lyrics) };
}

/** The distinct chords of a ChordPro text, in order of appearance. */
export function chordsIn(chordpro: string): string[] {
  const seen = new Set<string>();
  for (const m of chordpro.matchAll(/\[([A-G][^\[\]\s]*)\]/g)) seen.add(m[1]);
  return [...seen];
}

// the tablet's limits (WebEditorServer): a backing track as in the app, a backup with every track
const TRACK_MAX = 200 * 1024 * 1024;
const BACKUP_MAX = 1024 * 1024 * 1024;

/**
 * A file on this computer to send: `~` is the home folder, a relative path is refused (it would resolve against the
 * assistant app's folder, not the person's), and one over the tablet's limit is refused here (the tablet closed the
 * connection, which read as "Couldn't reach"). Read once: the copy into a new array held a backup twice (audit 03/11).
 */
export async function localFile(path: string, max: number): Promise<Uint8Array> {
  const full = path === "~" || path.startsWith("~/") ? join(homedir(), path.slice(1)) : path;
  if (!isAbsolute(full)) throw new StagebookError(`Pass the file's full path (like /Users/name/Music/song.mp3 or ~/Music/song.mp3), not "${path}".`);
  let size: number;
  try {
    size = (await stat(full)).size;
  } catch {
    throw new StagebookError(`There's no file at ${full}.`);
  }
  if (size > max) throw new StagebookError(`The file is ${Math.round(size / 1048576)} MB; the tablet takes up to ${Math.round(max / 1048576)} MB.`);
  return readFile(full);
}

const BREAKS = z.array(z.number()).describe("Interval positions: integers from 1 to the number of songs minus 1, ascending, no repeats (1 = before the second song).");

/** Breaks the setlist can have: the app's rule, said here in words before the tablet is asked. */
export function checkBreaks(breaks: number[] | undefined, songs: number): void {
  if (!breaks) return;
  const bad = breaks.find((p) => !Number.isInteger(p) || p < 1 || p > songs - 1);
  if (bad !== undefined) throw new StagebookError(`Break position ${bad} doesn't fit: a break goes before the second song up to before the last (1 to ${Math.max(songs - 1, 0)} for ${songs} songs).`);
  if (breaks.some((p, i) => i > 0 && p <= breaks[i - 1])) throw new StagebookError("Break positions must be in ascending order, with no repeats.");
}

const norm = (s: string) => s.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();

export function buildServer(client: StagebookClient): McpServer {
  const server = new McpServer(
    { name: "stagebook", version: VERSION },
    {
      instructions:
        "Stagebook is an Android app for lyrics, chords (ChordPro), setlists and backing tracks used live by musicians. " +
        "These tools edit the library on the person's tablet over the local network; the stage updates right away. " +
        "First use: call stagebook_connect (the person approves on the tablet). Song text is ChordPro: chords in brackets " +
        "before the syllable ([G]Here I [D]go), sections as [Verse 1]/[Chorus] lines (Portuguese names work too) or " +
        "{start_of_chorus}; key, capo and " +
        "BPM are separate fields. Ask before deleting anything. Song lyrics are copyrighted: don't reproduce them in full " +
        "to the person unless they ask for their own song.",
    },
  );

  /**
   * Transpose or capo: the tablet reworks the song's text with its own rules ([route]), then the song is saved with
   * the new text, key and capo (the two tools were the same three calls).
   */
  async function rework(id: string, route: string, change: Record<string, number>) {
    const cur = (await client.get(`/api/songs/${encodeURIComponent(id)}`)) as Song;
    const out = await client.send("POST", route, { text: cur.text, ...change, meta: { key: cur.key, capo: cur.capo } });
    const key = out.meta?.key ?? cur.key;
    const capo = out.meta?.capo ?? change.capo ?? cur.capo;
    await client.send("PUT", `/api/songs/${encodeURIComponent(id)}`, { ...cur, text: out.text, key, capo });
    return { cur, key: out.meta?.key as string | undefined, capo: out.meta?.capo as number | undefined };
  }

  // ---------- connection ----------

  server.registerTool(
    "stagebook_connect",
    {
      title: "Connect to the Stagebook tablet",
      description:
        "Finds Stagebook on the local network (or uses the given address) and asks the tablet to let this computer in. " +
        "The tablet shows a request with a code: this tool returns at once with the code; tell the person and ask them to tap " +
        "Allow on the tablet, then call stagebook_status, which waits for it. Needs Stagebook open with Settings → Edit on " +
        "the computer turned on. The key is saved for next time.",
      inputSchema: {
        url: z.string().optional().describe("Address shown in Stagebook, e.g. http://192.168.0.10:8090. Omit to search the network."),
        name: z.string().optional().describe("How this computer shows on the tablet (default: 'AI assistant')."),
      },
    },
    safe(async ({ url, name }: { url?: string; name?: string }) => {
      if (url) client.url = url;
      // no address given: the saved one if it still answers, otherwise search again (the tablet's address can change
      // with the Wi-Fi; the saved one was used forever, audit 03/11)
      else if (!client.url || !(await answers(client.url))) {
        const found = await discoverAll();
        if (found.length === 0) {
          throw new StagebookError(
            "Stagebook wasn't found on this network. On the tablet: open Stagebook and turn on Settings → Edit on the computer; " +
              "it shows the address to pass here. On a Mac, also check System Settings → Privacy & Security → Local Network " +
              "for the app that runs this assistant.",
          );
        }
        if (found.length > 1) {
          throw new StagebookError(
            `Stagebook answers on more than one tablet here: ${found.join(", ")}. Ask the person which address their tablet ` +
              "shows in Settings → Edit on the computer and pass it as url.",
          );
        }
        client.url = found[0];
      }
      await save({ url: client.url, key: client.key });
      const code = await client.startPair(name ?? "AI assistant");
      return text(
        `Stagebook found at ${client.url}. The tablet now shows a request with the code ${code}: ask the person to check it ` +
          "and tap Allow on the tablet. Then call stagebook_status (it waits for the approval); the key is saved for next time.",
      );
    }),
  );

  server.registerTool(
    "stagebook_status",
    {
      title: "Stagebook connection status",
      description: "Checks the connection: the tablet's address, its name and how this computer is known there.",
      inputSchema: {},
      annotations: { readOnlyHint: true },
    },
    safe(async () => {
      const s = await client.get("/api/session");
      return text({ url: client.url, device: s?.app, thisComputer: s?.name });
    }),
  );

  // ---------- songs ----------

  server.registerTool(
    "list_songs",
    {
      title: "List songs",
      description: "The library's songs (id, title, artist, year, key, tags, bpm, capo, hasTrack), in the app's order: bpm and capo are 0 when the song has none, hasTrack says a backing track is attached. Optional filters.",
      inputSchema: {
        query: z.string().optional().describe("Part of the title or artist (accents and case ignored)."),
        tag: z.string().optional().describe("Only songs with this tag."),
        key: z.string().optional().describe("Only songs in this key, e.g. G or Am."),
      },
      annotations: { readOnlyHint: true },
    },
    safe(async ({ query, tag, key }: { query?: string; tag?: string; key?: string }) => {
      let songs = (await client.get("/api/songs")) as SongItem[];
      if (query) songs = songs.filter((s) => norm(`${s.title} ${s.author}`).includes(norm(query)));
      if (tag) songs = songs.filter((s) => s.tags.some((t) => norm(t) === norm(tag)));
      if (key) songs = songs.filter((s) => s.key.toLowerCase() === key.toLowerCase());
      return text({ count: songs.length, songs });
    }),
  );

  server.registerTool(
    "get_song",
    {
      title: "Get a song",
      description: "One song's details: title, artist, year, BPM, key, capo, tags, backing track and its chords. With include_text, the full ChordPro text.",
      inputSchema: {
        id: z.string(),
        include_text: z.boolean().optional().describe("Also return the lyrics and chords (ChordPro). Default false."),
      },
      annotations: { readOnlyHint: true },
    },
    safe(async ({ id, include_text }: { id: string; include_text?: boolean }) => text(songOut((await client.get(`/api/songs/${encodeURIComponent(id)}`)) as Song, !!include_text))),
  );

  const songFields = {
    title: z.string().optional(),
    author: z.string().optional().describe("Artist."),
    year: z.string().optional(),
    tempo: z.string().optional().describe("BPM, e.g. '120'."),
    text: z.string().optional().describe("Lyrics and chords in ChordPro (chords above the lyrics are converted too)."),
    key: z.string().optional().describe("The song's key, e.g. G."),
    capo: z.number().int().min(0).max(12).optional(),
    tags: z.array(z.string()).optional(),
  };

  server.registerTool(
    "create_song",
    {
      title: "Create a song",
      description: "Adds a song to the library. Title and text are required; the tablet refuses a repeated title+artist or text that isn't a song.",
      inputSchema: { ...songFields, title: z.string(), text: z.string() },
    },
    safe(async (a: Partial<Song> & { title: string; text: string }) => {
      const r = await client.send("POST", "/api/songs", { title: a.title, author: a.author ?? "", year: a.year ?? "", tempo: a.tempo ?? "", text: a.text, key: a.key ?? "", capo: a.capo ?? 0, tags: a.tags ?? [] });
      return text({ created: r?.id });
    }),
  );

  server.registerTool(
    "update_song",
    {
      title: "Update a song",
      description: "Changes some fields of a song; the others stay as they are.",
      inputSchema: { id: z.string(), ...songFields },
    },
    safe(async ({ id, ...changes }: Partial<Song> & { id: string }) => {
      const cur = (await client.get(`/api/songs/${encodeURIComponent(id)}`)) as Song;
      const next = { ...cur, ...Object.fromEntries(Object.entries(changes).filter(([, v]) => v !== undefined)) };
      await client.send("PUT", `/api/songs/${encodeURIComponent(id)}`, next);
      return text({ updated: id });
    }),
  );

  server.registerTool(
    "delete_song",
    {
      title: "Delete a song",
      description: "Deletes a song from the library (and its backing track). It can't be undone: ask the person first and pass confirm=true.",
      inputSchema: { id: z.string(), confirm: z.literal(true).describe("Must be true: the person agreed to delete it.") },
      annotations: { destructiveHint: true },
    },
    safe(async ({ id }: { id: string }) => {
      await client.send("DELETE", `/api/songs/${encodeURIComponent(id)}`);
      return text({ deleted: id });
    }),
  );

  server.registerTool(
    "transpose_song",
    {
      title: "Transpose a song",
      description: "Moves every chord of a song up or down by semitones (and the key with it), then saves.",
      inputSchema: { id: z.string(), semitones: z.number().int().min(-11).max(11).describe("+2 = up a whole tone, -1 = down a half tone.") },
    },
    safe(async ({ id, semitones }: { id: string; semitones: number }) => {
      const { cur, key } = await rework(id, "/api/transpose", { semitones });
      return text({ id, from: cur.key, to: key });
    }),
  );

  server.registerTool(
    "set_capo",
    {
      title: "Set a song's capo",
      description: "Puts the capo on a fret (0 = none). Like the app: the chord shapes stay, and the song sounds higher.",
      inputSchema: { id: z.string(), capo: z.number().int().min(0).max(12) },
    },
    safe(async ({ id, capo }: { id: string; capo: number }) => {
      const r = await rework(id, "/api/capo", { capo });
      return text({ id, capo: r.capo ?? capo, key: r.key });
    }),
  );

  server.registerTool(
    "import_song",
    {
      title: "Import a song",
      description:
        "Turns pasted lyrics/chords (any common format, chords above the lyrics included) or a song page address into a " +
        "Stagebook song, with the app's own importer. With save=true it's added to the library; otherwise it's returned to review.",
      inputSchema: {
        text: z.string().optional().describe("Pasted lyrics and chords."),
        url: z.string().optional().describe("Address of a song page (needs the tablet's internet on)."),
        title: z.string().optional().describe("Title when pasting text without one."),
        with_tab: z.boolean().optional().describe("Keep tablature when the song has it (otherwise the tool asks)."),
        save: z.boolean().optional().describe("Add it to the library right away. Default false."),
      },
    },
    safe(async ({ text: pasted, url, title, with_tab, save: keep }: { text?: string; url?: string; title?: string; with_tab?: boolean; save?: boolean }, signal) => {
      if (!pasted && !url) throw new StagebookError("Pass the text or the page address.");
      const r = await client.send("POST", "/api/import", { text: pasted ?? "", url: url ?? "", title: title ?? "", withTab: with_tab ?? null }, undefined, 60000, signal);
      if (r?.status === "askTab") return text("The song has tablature: call again with with_tab true (keep it) or false (chords only).");
      if (!keep) return text({ toReview: { title: r.title, author: r.author, year: r.year, tempo: r.tempo, key: r.key, capo: r.capo, chords: chordsIn(r.text ?? ""), text: r.text } });
      const created = await client.send("POST", "/api/songs", { title: r.title, author: r.author, year: r.year, tempo: r.tempo, text: r.text, key: r.key, capo: r.capo, tags: [] });
      return text({ created: created?.id, title: r.title, author: r.author });
    }),
  );

  server.registerTool(
    "set_backing_track",
    {
      title: "Set a song's backing track",
      description: "Uploads an audio file from this computer (OGG with any number of channels, MP3 or WAV) as the song's backing track, replacing any previous one.",
      inputSchema: { id: z.string(), path: z.string().describe("Path of the audio file on this computer.") },
    },
    safe(async ({ id, path }: { id: string; path: string }, signal) => {
      const bytes = await localFile(path, TRACK_MAX);
      const r = await client.send("PUT", `/api/songs/${encodeURIComponent(id)}/track`, bytes, { name: basename(path) }, 300000, signal);
      return text({ id, track: r?.trackName ?? basename(path) });
    }),
  );

  server.registerTool(
    "remove_backing_track",
    {
      title: "Remove a song's backing track",
      description: "Takes the backing track off a song (the audio file is deleted from the tablet). Ask first.",
      inputSchema: { id: z.string(), confirm: z.literal(true) },
      annotations: { destructiveHint: true },
    },
    safe(async ({ id }: { id: string }) => {
      await client.send("DELETE", `/api/songs/${encodeURIComponent(id)}/track`);
      return text({ id, track: null });
    }),
  );

  // ---------- setlists ----------

  server.registerTool(
    "list_setlists",
    {
      title: "List setlists",
      description: "The setlists (id, name, number of songs), in the order the app's Setlists tab shows them. Optional filter.",
      inputSchema: { query: z.string().optional().describe("Part of the name (accents and case ignored).") },
      annotations: { readOnlyHint: true },
    },
    safe(async ({ query }: { query?: string }) => {
      const sets = (await client.get("/api/sets")) as { name: string }[];
      return text(query ? sets.filter((s) => norm(s.name).includes(norm(query))) : sets);
    }),
  );

  server.registerTool(
    "setlist_order",
    {
      title: "Setlists order",
      description: "Reads the order of the app's Setlists tab or, with order, changes it there: newest first or A to Z.",
      inputSchema: { order: z.enum(["newest", "abc"]).optional().describe("Leave it out to only read the current order.") },
    },
    safe(async ({ order }: { order?: "newest" | "abc" }) => {
      const now = order ? await client.send("PUT", "/api/sets-order", { abc: order === "abc" }) : await client.get("/api/sets-order");
      return text({ order: now.abc ? "abc" : "newest" });
    }),
  );

  server.registerTool(
    "get_setlist",
    {
      title: "Get a setlist",
      description: "A setlist with its songs in order (a song deleted from the library shows with an empty title) and its breaks: \"breaks\" are the positions where the show stops for an interval, 1 = before the second song.",
      inputSchema: { id: z.string() },
      annotations: { readOnlyHint: true },
    },
    safe(async ({ id }: { id: string }) => text(await client.get(`/api/sets/${encodeURIComponent(id)}`))),
  );

  server.registerTool(
    "create_setlist",
    {
      title: "Create a setlist",
      description: "Creates a setlist with these songs, in this order (song ids from list_songs). Without a name it's named after the date and time. Optional breaks: the positions of the intervals (1 = before the second song, up to the number of songs minus 1, in order, no repeats); without it there are none.",
      inputSchema: { name: z.string().optional(), song_ids: z.array(z.string()).optional(), breaks: BREAKS.optional() },
    },
    safe(async ({ name, song_ids, breaks }: { name?: string; song_ids?: string[]; breaks?: number[] }) => {
      const items = song_ids ?? [];
      checkBreaks(breaks, items.length);
      return text({ created: (await client.send("POST", "/api/sets", { name: name ?? "", items, ...(breaks ? { breaks } : {}) }))?.id });
    }),
  );

  server.registerTool(
    "update_setlist",
    {
      title: "Update a setlist",
      description: "Renames a setlist and/or replaces its songs (the full list, in order) and/or its breaks (the full list of interval positions: 1 = before the second song, up to the number of songs minus 1, in order, no repeats; [] takes them all out). Omitted fields stay: without breaks, the existing ones stay before the same songs. Emptying it (song_ids []) needs confirm=true.",
      inputSchema: {
        id: z.string(), name: z.string().optional(), song_ids: z.array(z.string()).optional(), breaks: BREAKS.optional(),
        confirm: z.boolean().optional().describe("Required true to leave the setlist with no songs."),
      },
    },
    safe(async ({ id, name, song_ids, breaks, confirm }: { id: string; name?: string; song_ids?: string[]; breaks?: number[]; confirm?: boolean }) => {
      const cur = await client.get(`/api/sets/${encodeURIComponent(id)}`);
      checkBreaks(breaks, (song_ids ?? cur.songs).length);
      // an empty list takes every song out: like deleting, it asks first (audit 03/11)
      if (song_ids?.length === 0 && cur.songs.length > 0 && !confirm) {
        throw new StagebookError(`This takes all ${cur.songs.length} songs out of "${cur.name}": ask the person and pass confirm=true.`);
      }
      await client.send("PUT", `/api/sets/${encodeURIComponent(id)}`, { name: name ?? cur.name, items: song_ids ?? cur.songs.map((s: SongItem) => s.id), ...(breaks ? { breaks } : {}) });
      return text({ updated: id });
    }),
  );

  server.registerTool(
    "delete_setlist",
    {
      title: "Delete a setlist",
      description: "Deletes a setlist (its songs stay in the library). Ask first and pass confirm=true.",
      inputSchema: { id: z.string(), confirm: z.literal(true) },
      annotations: { destructiveHint: true },
    },
    safe(async ({ id }: { id: string }) => {
      await client.send("DELETE", `/api/sets/${encodeURIComponent(id)}`);
      return text({ deleted: id });
    }),
  );

  // ---------- receiving and the band ----------

  server.registerTool(
    "receive_by_code",
    {
      title: "Receive a setlist by its 3 words",
      description:
        "Fetches what someone sent over the internet with Stagebook's 3-word code (e.g. 47-violao-lua) and returns the " +
        "review: each song found in the library, new, or with candidates. Then call receive_create with the token.",
      inputSchema: { code: z.string() },
    },
    safe(async ({ code }: { code: string }, signal) => text(await client.send("POST", "/api/receive", { code }, undefined, 60000, signal))),
  );

  server.registerTool(
    "receive_create",
    {
      title: "Create the received setlist",
      description:
        "After receive_by_code or open_band_item: creates the setlist from the review's token. choices maps a row index " +
        "to the chosen library song id where the review offered candidates.",
      inputSchema: { token: z.string(), name: z.string().optional(), choices: z.record(z.string(), z.string()).optional() },
    },
    safe(async ({ token, name, choices }: { token: string; name?: string; choices?: Record<string, string> }) => {
      const r = await client.send("POST", "/api/receive/create", { token, name: name ?? "", choices: choices ?? {} });
      return text({ setlist: r?.setlist });
    }),
  );

  server.registerTool(
    "band_inbox",
    {
      title: "What the band sent",
      description: "Setlists and songs band members sent to this tablet that weren't opened yet.",
      inputSchema: {},
      annotations: { readOnlyHint: true },
    },
    safe(async () => text(await client.get("/api/band"))),
  );

  server.registerTool(
    "band_sync",
    {
      title: "Sync with the band",
      description: "Asks the band (same Wi-Fi) and the internet inbox for anything new.",
      inputSchema: {},
    },
    safe(async () => {
      await client.send("POST", "/api/band/sync");
      return text("Asked the band for anything new; check band_inbox in a few seconds.");
    }),
  );

  server.registerTool(
    "open_band_item",
    {
      title: "Open something the band sent",
      description: "Reviews an item from band_inbox (by id), like receive_by_code; then create it with receive_create.",
      inputSchema: { id: z.string() },
    },
    safe(async ({ id }: { id: string }) => text(await client.send("POST", "/api/band/open", { code: id }))),
  );

  // ---------- backup ----------

  server.registerTool(
    "restore_backup",
    {
      title: "Restore a backup file",
      description:
        "Sends a .stagebook file from this computer to the tablet. Without parts it only inspects (what the file has). " +
        "With parts (library, tracks, settings) it restores them: replace=true swaps the library for the file's, " +
        "replace=false only adds the songs, setlists and tracks that are missing. Settings always replace the tablet's " +
        "(stage look, pedals, USB audio interface, band). Replacing the library or restoring settings: ask first and pass confirm=true.",
      inputSchema: {
        path: z.string(),
        parts: z.array(z.enum(["library", "tracks", "settings"])).optional(),
        replace: z.boolean().optional(),
        confirm: z.boolean().optional().describe("Required true with replace=true or with settings in parts."),
      },
      annotations: { destructiveHint: true },
    },
    safe(async ({ path, parts, replace, confirm }: { path: string; parts?: string[]; replace?: boolean; confirm?: boolean }, signal) => {
      const bytes = await localFile(path, BACKUP_MAX);
      const info = await client.send("POST", "/api/backup/inspect", bytes, undefined, 600000, signal);
      if (!parts?.length) return text({ inspected: info });
      if (replace && !confirm) throw new StagebookError("Replacing swaps the whole library for the file's: ask the person and pass confirm=true.");
      // the tablet's settings are always replaced by the file's, with replace or not (audit 03/11: it went without asking)
      if (parts.includes("settings") && !confirm) {
        throw new StagebookError("Restoring settings replaces the tablet's (stage look, pedals, USB audio interface, band): ask the person and pass confirm=true.");
      }
      // given up while the file went over: the restore doesn't start
      if (signal?.aborted) throw new StagebookError("The call was cancelled before restoring: nothing changed on the tablet.");
      const r = await client.send("POST", "/api/backup/restore", { token: info.token, parts, replace: !!replace }, undefined, 600000, signal);
      return text({ restored: parts, replace: !!replace, skipped: r?.skipped ?? 0 });
    }),
  );

  // ---------- resources ----------

  server.registerResource(
    "songs",
    "stagebook://songs",
    { title: "Stagebook songs", description: "The library's songs (id, title, artist, year, key, tags).", mimeType: "application/json" },
    async (uri) => ({ contents: [{ uri: uri.href, mimeType: "application/json", text: JSON.stringify(await client.get("/api/songs"), null, 2) }] }),
  );

  server.registerResource(
    "setlists",
    "stagebook://setlists",
    { title: "Stagebook setlists", description: "The setlists (id, name, count).", mimeType: "application/json" },
    async (uri) => ({ contents: [{ uri: uri.href, mimeType: "application/json", text: JSON.stringify(await client.get("/api/sets"), null, 2) }] }),
  );

  server.registerResource(
    "song",
    new ResourceTemplate("stagebook://songs/{id}", { list: undefined }),
    { title: "A Stagebook song (ChordPro)", description: "One song as ChordPro, with its details as directives.", mimeType: "text/plain" },
    async (uri, { id }) => {
      const s = (await client.get(`/api/songs/${encodeURIComponent(String(id))}`)) as Song;
      const head = [`{title: ${s.title}}`, s.author && `{artist: ${s.author}}`, s.year && `{year: ${s.year}}`, s.key && `{key: ${s.key}}`, s.capo ? `{capo: ${s.capo}}` : "", s.tempo && `{tempo: ${s.tempo}}`].filter(Boolean);
      return { contents: [{ uri: uri.href, mimeType: "text/plain", text: [...head, "", s.text].join("\n") }] };
    },
  );

  // ---------- prompts ----------

  server.registerPrompt(
    "build_setlist",
    {
      title: "Build a setlist",
      description: "Picks songs from the library for a gig and creates the setlist.",
      argsSchema: { occasion: z.string().describe("The gig: length, mood, audience…"), count: z.string().optional() },
    },
    ({ occasion, count }) => ({
      messages: [{
        role: "user",
        content: { type: "text", text: `Build a Stagebook setlist for: ${occasion}. ${count ? `About ${count} songs. ` : ""}Use list_songs (and get_song for BPM/key when useful), balance energy and keys, avoid the same artist back to back, show me the list with a short reason, and create it with create_setlist after I agree.` },
      }],
    }),
  );

  server.registerPrompt(
    "fix_chords",
    {
      title: "Check a song's chords",
      description: "Reviews a song's chords and sections and proposes fixes.",
      argsSchema: { song: z.string().describe("Title or id.") },
    },
    ({ song }) => ({
      messages: [{
        role: "user",
        content: { type: "text", text: `Find the Stagebook song "${song}" (list_songs), read it with get_song include_text, and check the chords (misplaced, wrong key, typos) and section titles. Propose the changes and only save with update_song after I agree.` },
      }],
    }),
  );

  server.registerPrompt(
    "transpose_for_singer",
    {
      title: "Transpose songs for a singer",
      description: "Moves several songs to keys that suit a singer.",
      argsSchema: { which: z.string().describe("Which songs (artist, tag, setlist…)."), range: z.string().describe("The singer's comfortable range or target keys.") },
    },
    ({ which, range }) => ({
      messages: [{
        role: "user",
        content: { type: "text", text: `In Stagebook, for these songs: ${which}. The singer's range/target: ${range}. Propose a new key per song (with semitones), then transpose_song each one after I agree.` },
      }],
    }),
  );

  return server;
}
