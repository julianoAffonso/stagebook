<p align="center"><img src="logo.png" alt="Stagebook" width="64"></p>

# Now Playing (OBS, Resolume…)

[English](now-playing.md) · **Português (Brasil)**

No palco, a cada troca de música (e depois a cada 10 segundos), o Stagebook envia o **Now Playing** na rede local: a
música que está tocando. Programas de live e de projeção (OBS, Resolume e outros) podem mostrar o nome da música e o
artista. A letra nunca vai. Para desligar: Ajustes → Banda e transmissão → Avisar a música tocando.

- **Como:** broadcast UDP para o endereço de broadcast de cada rede local em que o aparelho está (por exemplo
  `192.168.1.255`), porta **47800**, só com a rede local ligada (Wi-Fi, cabo ou ponto de acesso do celular). Num celular
  compartilhando o ponto de acesso, chega também aos computadores ligados nele. Escute a porta 47800 em qualquer
  endereço.
- **O que vai** (JSON, UTF-8):

```json
{"app":"stagebook","v":1,"title":"Hazard","artist":"Richard Marx","set":"Show de sábado","index":3,"count":12,"key":"G","bpm":92}
```

`index` é a posição no setlist (a partir de 1; 0 = música avulsa) e `count` o total; `key` e `bpm` vêm vazios/0 quando a
música não tem. Campos novos podem aparecer em versões futuras (`v` aumenta): ignore os que não conhecer.

## Exemplo para o OBS

Este script grava a música da vez num arquivo de texto. No OBS, crie uma fonte **Texto (GDI+/FreeType)**, marque
**Ler de arquivo** e aponte para `now-playing.txt`.

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
