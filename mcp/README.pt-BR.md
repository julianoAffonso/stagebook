# Stagebook MCP

[English](README.md) · **Português (Brasil)**

Deixa assistentes de IA lerem e editarem as músicas e os setlists do seu tablet com o Stagebook: "monta um setlist de
15 músicas para sábado sem repetir artista", "passa todas as do Skank meio tom abaixo", "confere os acordes de Hazard",
"importa esta página". É um servidor [Model Context Protocol](https://modelcontextprotocol.io) que roda no seu computador
e conversa com o tablet pela rede local, pela mesma conexão de **Ajustes → Editar no computador**. Nada vai para a
internet e o app não muda: o que o assistente salva aparece no palco na hora.

Funciona com o Claude Code, o Claude Desktop e qualquer assistente que roda servidores MCP locais (Cursor, VS Code com
Copilot, Windsurf, Gemini CLI, OpenAI Codex CLI…).

## O que ele faz

| Assunto | Ferramentas |
|---|---|
| Conexão | `stagebook_connect` (acha o tablet e pede para entrar), `stagebook_status` |
| Músicas | `list_songs` (filtros: texto, tag, tom), `get_song`, `create_song`, `update_song`, `delete_song`, `transpose_song`, `set_capo` |
| Importar | `import_song` (letra e cifra coladas em qualquer formato comum, ou o endereço de uma página) |
| Backing tracks | `set_backing_track` (OGG, MP3 ou WAV do computador), `remove_backing_track` |
| Setlists | `list_setlists`, `get_setlist`, `create_setlist`, `update_setlist`, `delete_setlist` |
| Receber | `receive_by_code` (as 3 palavras), `receive_create`, `band_inbox`, `band_sync`, `open_band_item` |
| Backup | `restore_backup` (ver o que um arquivo `.stagebook` tem, juntar o que falta ou substituir a biblioteca) |

Tem também **recursos** (`stagebook://songs`, `stagebook://setlists`, `stagebook://songs/{id}` em ChordPro) e **roteiros
prontos** (`build_setlist`, `fix_chords`, `transpose_for_singer`).

Apagar, tirar uma faixa e substituir a biblioteca pedem `confirm: true`: o assistente precisa perguntar a você antes.

## Instalar

Precisa do [Node.js](https://nodejs.org) 20 ou mais novo.

```sh
git clone https://github.com/julianoAffonso/stagebook.git
cd stagebook/mcp
npm install
npm run build
```

O servidor é `node /caminho/completo/stagebook/mcp/dist/index.js`. Use esse caminho nos ajustes do seu assistente, abaixo.

## Ligar no seu assistente

**Claude Code**

```sh
claude mcp add stagebook -- node /caminho/completo/stagebook/mcp/dist/index.js
```

**Claude Desktop** — Configurações → Desenvolvedor → Editar configuração (`claude_desktop_config.json`):

```json
{ "mcpServers": { "stagebook": { "command": "node", "args": ["/caminho/completo/stagebook/mcp/dist/index.js"] } } }
```

**Cursor** (`~/.cursor/mcp.json`) e **Windsurf** (`~/.codeium/windsurf/mcp_config.json`): o mesmo bloco `mcpServers`.

**VS Code** (`.vscode/mcp.json` num projeto, ou o `mcp.json` do usuário):

```json
{ "servers": { "stagebook": { "type": "stdio", "command": "node", "args": ["/caminho/completo/stagebook/mcp/dist/index.js"] } } }
```

**Gemini CLI** (`~/.gemini/settings.json`): o mesmo bloco `mcpServers` do Claude Desktop.

**OpenAI Codex CLI** (`~/.codex/config.toml`):

```toml
[mcp_servers.stagebook]
command = "node"
args = ["/caminho/completo/stagebook/mcp/dist/index.js"]
```

O app do ChatGPT só se conecta a servidores remotos (pela internet), então não alcança o tablet no seu Wi-Fi.

## Primeira vez

1. No tablet: abra o Stagebook e ligue **Ajustes → Editar no computador**. Ele mostra o endereço
   (ex.: `http://192.168.0.10:8090`). O computador e o tablet precisam estar no mesmo Wi-Fi.
2. Peça ao assistente para conectar no Stagebook. Ele procura na rede (ou você passa o endereço) e o tablet mostra um
   pedido com um código.
3. Se o código bater, toque em **Permitir** no tablet. A chave fica guardada neste computador
   (`~/.config/stagebook-mcp/config.json`, que só você lê); da próxima vez ele conecta sem perguntar.

O editor desliga sozinho no tablet quando fica sem uso: ligue de novo quando quiser usar o assistente. A chave dura 90
dias sem uso; **Ajustes → Editar no computador → Computadores autorizados** remove a qualquer hora.

### Ajustes

| Variável | Para quê |
|---|---|
| `STAGEBOOK_URL` | O endereço do tablet, sem procurar (ex.: `http://192.168.0.10:8090`). |
| `STAGEBOOK_KEY` | Uma chave no lugar da guardada. |
| `STAGEBOOK_CONFIG` | Outro lugar para o endereço e a chave guardados. |

## Se não conectar

- **"Stagebook wasn't found" / "Couldn't reach"**: o Stagebook precisa estar aberto no tablet, com **Ajustes → Editar no
  computador** ligado, no mesmo Wi-Fi do computador (não numa rede de convidados). Passe ao assistente o endereço que
  aparece ali.
- **No Mac, "macOS blocked the connection"**: o macOS pergunta antes de um app conversar com aparelhos da rede local.
  Abra **Ajustes do Sistema → Privacidade e Segurança → Rede Local** e ligue o app que roda o assistente (Claude,
  Terminal, iTerm, Cursor, VS Code…). Depois reabra esse app.
- **"No longer accepts this computer's key"**: a chave foi removida no tablet ou passou 90 dias sem uso. Conecte de novo.

## Privacidade

Tudo fica na sua rede: o servidor só conversa com o tablet. O assistente vê o que as ferramentas devolvem (títulos,
artistas, acordes e, quando ele pede, a letra) e manda isso para o provedor de IA dele, como o resto da conversa. Letras
têm direito autoral: o servidor orienta o assistente a não reproduzi-las inteiras.

## Para quem programa

```sh
npm test   # compila e roda os testes contra um tablet de mentira
```

As ferramentas ficam em `src/server.ts`; as chamadas ao tablet em `src/client.ts` (a mesma API HTTP da página do editor
no computador, em `data/web/` no app).
