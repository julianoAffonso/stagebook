# Now Playing (OBS, Resolume…)

## Português

No palco, a cada troca de música (e depois a cada 10 segundos), o Stagebook envia o **Now Playing** na rede local: a
música que está tocando. Programas de live e de projeção (OBS, Resolume e outros) podem mostrar o nome da música e o
artista. A letra nunca vai. Para desligar: Ajustes → Tela → Now Playing.

- **Como:** broadcast UDP para `255.255.255.255`, porta **47800**, só com a rede local ligada (Wi-Fi, cabo ou ponto de
  acesso do celular).
- **O que vai** (JSON, UTF-8):

```json
{"app":"stagebook","v":1,"title":"Hazard","artist":"Richard Marx","set":"Show de sábado","index":3,"count":12,"key":"G","bpm":92}
```

`index` é a posição no setlist (a partir de 1; 0 = música avulsa) e `count` o total; `key` e `bpm` vêm vazios/0 quando a
música não tem. Campos novos podem aparecer em versões futuras (`v` aumenta): ignore os que não conhecer.

### Exemplo para o OBS

Este script grava a música da vez num arquivo de texto. No OBS, crie uma fonte **Texto (GDI+/FreeType)**, marque
**Ler de arquivo** e aponte para `musica.txt`.

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
    open("musica.txt", "w", encoding="utf-8").write(line)
```

## English

On stage, every time the song changes (and every 10 seconds after that), Stagebook sends **Now Playing** on the local
network, so streaming and projection software (OBS, Resolume and others) can show the title and artist. Lyrics are
never sent. To turn it off: Settings → Screen → Now Playing.

- **How:** UDP broadcast to `255.255.255.255`, port **47800**, only while a local network is on (Wi-Fi, cable or a phone
  hotspot).
- **Payload:** the JSON above. `index` is the 1-based position in the setlist (0 = single song), `count` the setlist size;
  `key`/`bpm` are empty/0 when unknown. Ignore fields you don't know: new ones may come in later versions (`v` grows).
- **OBS:** run the Python script above and point a Text source ("Read from file") to `musica.txt`.
