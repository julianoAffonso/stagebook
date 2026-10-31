<p align="center"><img src="logo.png" alt="Stagebook" width="64"></p>

# Now Playing (OBS, Resolume…)

**English** · [Português (Brasil)](now-playing.pt-BR.md)

On stage, every time the song changes (and every 10 seconds after that), Stagebook sends **Now Playing** on the local
network: the song that's playing. Streaming and projection software (OBS, Resolume and others) can show the title and
artist. The lyrics never go out. To turn it off: Settings → Screen → Now Playing.

- **How:** UDP broadcast to `255.255.255.255`, port **47800**, only while a local network is on (Wi-Fi, cable or a
  phone hotspot).
- **What goes out** (JSON, UTF-8):

```json
{"app":"stagebook","v":1,"title":"Hazard","artist":"Richard Marx","set":"Saturday show","index":3,"count":12,"key":"G","bpm":92}
```

`index` is the position in the setlist (from 1; 0 = a single song) and `count` the total; `key` and `bpm` come empty/0
when the song has none. New fields may show up in future versions (`v` grows): ignore the ones you don't know.

## Example for OBS

This script writes the current song to a text file. In OBS, add a **Text (GDI+/FreeType)** source, check **Read from
file** and point it to `now-playing.txt`.

```python
import json, socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("", 47800))
while True:
    data, _ = s.recvfrom(4096)
    a = json.loads(data.decode("utf-8"))
    if a.get("app") != "stagebook":
        continue
    line = a["title"] + (" — " + a["artist"] if a.get("artist") else "")
    open("now-playing.txt", "w", encoding="utf-8").write(line)
```
