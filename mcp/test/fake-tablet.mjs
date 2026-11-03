// A fake Stagebook "Edit on the computer" server with the same routes and rules the MCP relies on.
import { createServer } from "node:http";

export function fakeTablet({ approve = true } = {}) {
  const KEY = "k123";
  const songs = new Map([
    ["s1", { id: "s1", title: "Morning Road", author: "The Example Band", year: "1991", tempo: "", text: "[G]Linha um\n[D]Linha dois", track: "", trackName: "", key: "G", capo: 0, tags: ["internacional"] }],
    ["s2", { id: "s2", title: "Night Train", author: "Made Up Trio", year: "1986", tempo: "120", text: "[Am]Made up [F]words", track: "", trackName: "", key: "Am", capo: 0, tags: ["nacional"] }],
  ]);
  const sets = new Map([["t1", { id: "t1", name: "Saturday", items: ["s1"] }]]);
  let asked = 0;
  const restores = [];
  const server = createServer(async (req, res) => {
    const url = new URL(req.url, "http://x");
    const chunks = []; for await (const c of req) chunks.push(c);
    const body = Buffer.concat(chunks).toString();
    const send = (status, obj, headers = {}) => { res.writeHead(status, { "Content-Type": "application/json", ...headers }); res.end(obj === undefined ? "" : JSON.stringify(obj)); };
    if (req.headers["x-stagebook"] !== "1") return send(403);
    if (url.pathname === "/api/strings") return send(200, { title: "Stagebook" });
    if (url.pathname === "/api/ask" && req.method === "POST") return send(200, { id: "r1", code: "4821" });
    if (url.pathname === "/api/ask" && req.method === "GET") {
      if (!approve) return send(403);
      return ++asked < 2 ? send(202, {}) : send(200, {}, { "Set-Cookie": `stagebook_key=${KEY}; Path=/; HttpOnly` });
    }
    if (req.headers.cookie !== `stagebook_key=${KEY}`) return send(401);
    const item = (s) => ({ id: s.id, title: s.title, author: s.author, year: s.year, key: s.key, tags: s.tags });
    const m = url.pathname.match(/^\/api\/songs\/([^/]+)(\/track)?$/);
    if (url.pathname === "/api/session") return send(200, { name: "AI assistant", app: "TCL" });
    if (url.pathname === "/api/songs" && req.method === "GET") return send(200, [...songs.values()].map(item));
    if (url.pathname === "/api/songs" && req.method === "POST") {
      const d = JSON.parse(body);
      if (!d.title) return send(400, { error: "title_empty" });
      if ([...songs.values()].some((s) => s.title === d.title && s.author === d.author)) return send(400, { error: "title_taken" });
      const id = "s" + (songs.size + 1); songs.set(id, { ...d, id, track: "", trackName: "" }); return send(200, { id });
    }
    if (m && !m[2]) {
      const s = songs.get(m[1]); if (!s) return send(404);
      if (req.method === "GET") return send(200, s);
      if (req.method === "PUT") { songs.set(s.id, { ...JSON.parse(body), id: s.id }); return send(200, { id: s.id }); }
      if (req.method === "DELETE") { songs.delete(s.id); return send(204); }
    }
    if (m && m[2] && req.method === "PUT") { const s = songs.get(m[1]); s.track = "x.ogg"; s.trackName = url.searchParams.get("name"); return send(200, { track: "x.ogg", trackName: s.trackName, bytes: chunks.length }); }
    if (url.pathname === "/api/transpose") {
      const d = JSON.parse(body); const up = { G: "A", D: "E", A: "B", E: "F#" };
      return send(200, { text: d.text.replace(/\[([A-G])\]/g, (_, c) => `[${up[c] ?? c}]`), meta: { key: up[d.meta.key] ?? d.meta.key, capo: d.meta.capo } });
    }
    if (url.pathname === "/api/sets" && req.method === "GET") return send(200, [...sets.values()].map((t) => ({ id: t.id, name: t.name, count: t.items.length })));
    if (url.pathname === "/api/sets" && req.method === "POST") { const d = JSON.parse(body); const id = "t" + (sets.size + 1); sets.set(id, { id, name: d.name || "data", items: d.items }); return send(200, { id }); }
    const t = url.pathname.match(/^\/api\/sets\/([^/]+)$/);
    if (t) {
      const set = sets.get(t[1]); if (!set) return send(404);
      if (req.method === "GET") return send(200, { id: set.id, name: set.name, songs: set.items.map((i) => item(songs.get(i))) });
      if (req.method === "PUT") { const d = JSON.parse(body); set.name = d.name; set.items = d.items; return send(200, {}); }
    }
    if (url.pathname === "/api/backup/inspect" && req.method === "POST") return send(200, { token: "b1", songs: 2, sets: 1, tracks: 0, settings: true });
    if (url.pathname === "/api/backup/restore" && req.method === "POST") { restores.push(JSON.parse(body)); return send(200, { skipped: 0 }); }
    send(404);
  });
  return new Promise((ok) => server.listen(0, "127.0.0.1", () => ok({ server, url: `http://127.0.0.1:${server.address().port}`, songs, sets, restores })));
}
