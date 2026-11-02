#!/usr/bin/env python3
"""
Captures of the editor on the computer (T39): the real page (app/src/main/assets/web/index.html) served by a small
local stand-in for the tablet, with sample data, and photographed by Chrome headless in Portuguese and in English.
The texts come from the app's own strings (the same map the tablet sends), so the page says exactly what it says on
the tablet.
    tools/capturas_web.py <folder>        (writes <folder>/pt-BR/*.png and <folder>/en/*.png)
"""
import html
import json
import re
import subprocess
import sys
import tempfile
import threading
import struct
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PAGE = ROOT / "app/src/main/assets/web/index.html"
SERVER = ROOT / "app/src/main/java/br/app/stagebook/data/web/WebEditorServer.kt"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
LANGS = {"pt-BR": ROOT / "app/src/main/res/values-pt-rBR/strings.xml", "en": ROOT / "app/src/main/res/values/strings.xml"}


def app_strings(xml):
    """name → text of an Android strings.xml (plain strings; the page only uses those)."""
    out = {}
    for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', xml.read_text(encoding="utf-8"), re.S):
        out[m.group(1)] = html.unescape(m.group(2)).replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    return out


def page_strings(lang):
    """The texts the tablet sends the page (WEB_STRINGS), in [lang], with the chords profile's lyrics label."""
    kt = SERVER.read_text(encoding="utf-8")
    block = kt[kt.index("val WEB_STRINGS = mapOf("):]
    block = block[:block.index("\n        )")]
    names = dict(re.findall(r'"(\w+)" to R\.string\.(\w+)', block))
    texts = app_strings(LANGS[lang])
    out = {k: texts.get(v, "") for k, v in names.items()}
    out["lyrics"] = texts.get("editor_lyrics_chords", out.get("lyrics", ""))
    return out


# sample library: made-up lyrics (never a real song's), the same names as the app's own captures
SAMPLE = {
    "pt-BR": {
        "tags": ["Nacional", "Internacional", "Rock"],
        "lyrics": "[Intro]\n[G]  [D]  [Em]  [C]\n\n[Verso 1]\n[G]Uma linha de e[D]xemplo\n[Em]Outra linha in[C]ventada\n\n[Refrão]\n[G]O refrão de [D]teste\n[C]Fica aqui de e[D]xemplo",
        "set": "Show de sábado", "from": "Celular da Ana", "app": "Tablet do palco",
    },
    "en": {
        "tags": ["Local", "International", "Rock"],
        "lyrics": "[Intro]\n[G]  [D]  [Em]  [C]\n\n[Verse 1]\n[G]A sample line of [D]words\n[Em]Another made-up [C]line\n\n[Chorus]\n[G]The chorus for [D]testing\n[C]Stays here as a [D]sample",
        "set": "Saturday show", "from": "Ana's phone", "app": "Stage tablet",
    },
}


def library(lang):
    s = SAMPLE[lang]
    nat, intl, rock = s["tags"]
    songs = [
        ("1", "Hazard", "Richard Marx", "1991", "D", [intl]), ("2", "Malandragem", "Cássia Eller", "1994", "C", [nat, rock]),
        ("3", "Tempo Perdido", "Legião Urbana", "1986", "Am", [nat, rock]), ("4", "Wild Horses", "The Rolling Stones", "1971", "G", [intl]),
        ("5", "Por Enquanto", "Legião Urbana", "1985", "E", [nat]),
    ]
    items = [{"id": i, "title": t, "author": a, "year": y, "key": k, "tags": tg} for i, t, a, y, k, tg in songs]
    return items


class Stand(BaseHTTPRequestHandler):
    lang = "pt-BR"
    scene = "musica"

    def send(self, code, body, ctype="application/json"):
        b = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype + "; charset=utf-8")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def log_message(self, *a):
        pass

    def do_POST(self):
        self.do_GET()

    def do_GET(self):
        lang, s = Stand.lang, SAMPLE[Stand.lang]
        p = self.path.split("?")[0]
        if p == "/":
            # the real page, and a few lines that drive it to the scene (the page itself doesn't change)
            drive = {
                "musica": "await open('2');",
                "setlist": "tab('sets'); await openSet('s1');",
                "importar": "tab('import');",
                "pedido": "",
            }[Stand.scene]
            js = f"<script>setTimeout(async () => {{ {drive} }}, 400);</script></body>"
            return self.send(200, PAGE.read_text(encoding="utf-8").replace("</body>", js), "text/html")
        if p == "/api/strings":
            return self.send(200, json.dumps(page_strings(lang), ensure_ascii=False))
        if p == "/api/session":
            return self.send(401, "{}") if Stand.scene == "pedido" else self.send(200, json.dumps({"app": s["app"]}, ensure_ascii=False))
        if p == "/api/songs":
            return self.send(200, json.dumps(library(lang), ensure_ascii=False))
        if p.startswith("/api/songs/"):
            song = next(x for x in library(lang) if x["id"] == p.rsplit("/", 1)[1])
            return self.send(200, json.dumps({**song, "tempo": "92", "text": s["lyrics"], "track": song["id"] + "-faixa.ogg",
                                              "trackName": "malandragem-multitrack", "capo": 2}, ensure_ascii=False))
        if p == "/api/sets":
            return self.send(200, json.dumps([{"id": "s1", "name": s["set"], "count": 4}, {"id": "s2", "name": "18/10/2026 20:30", "count": 6}], ensure_ascii=False))
        if p.startswith("/api/sets/"):
            return self.send(200, json.dumps({"id": "s1", "name": s["set"], "songs": library(lang)[:4]}, ensure_ascii=False))
        if p == "/api/band":
            return self.send(200, json.dumps([{"id": "b1", "from": s["from"], "name": s["set"], "count": 12, "updated": False}], ensure_ascii=False))
        return self.send(404, "{}")


# each scene's height: the import tab is a long page, the others fit a laptop screen
HEIGHTS = {"pedido": 600, "musica": 940, "setlist": 820, "importar": 1900}
EXTRA = 120   # headless Chrome repeats the top of the page in the last rows: shot taller, then the bottom is cut


def crop_top(path, height):
    """Keeps the top [height] rows of a PNG. Only the first rows stay, so each row's filter (which looks at the row
    above) still holds: the data is cut, never decoded."""
    data = Path(path).read_bytes()
    pos, chunks = 8, []
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        chunks.append((data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]))
        pos += 12 + n
    ihdr = next(d for t, d in chunks if t == b"IHDR")
    w, h, depth, ctype = struct.unpack(">IIBB", ihdr[:10])
    if h <= height: return
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
    stride = 1 + w * channels * depth // 8
    raw = zlib.decompress(b"".join(d for t, d in chunks if t == b"IDAT"))[:stride * height]
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    out = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">II", w, height) + ihdr[8:]) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    Path(path).write_bytes(out)


def shoot(url, out, profile, height):
    subprocess.run([CHROME, "--headless=new", f"--user-data-dir={profile}", "--no-first-run", "--disable-gpu", "--hide-scrollbars",
                    "--force-device-scale-factor=1", f"--window-size=1440,{height + EXTRA}", "--virtual-time-budget=4000",
                    f"--screenshot={out}", url], check=True, capture_output=True, timeout=60)
    crop_top(out, height)


def main(dest):
    server = ThreadingHTTPServer(("127.0.0.1", 0), Stand)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{server.server_port}/"
    with tempfile.TemporaryDirectory() as profile:
        for lang in LANGS:
            folder = Path(dest) / lang
            folder.mkdir(parents=True, exist_ok=True)
            for n, scene in enumerate(["pedido", "musica", "setlist", "importar"], 1):
                Stand.lang, Stand.scene = lang, scene
                shoot(url, str(folder / f"editor_web_{n}_{scene}.png"), profile, HEIGHTS[scene])
    server.shutdown()
    print("capturas do editor web em:", dest)


if __name__ == "__main__":
    main(sys.argv[1])
