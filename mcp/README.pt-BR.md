# Stagebook MCP

[English](README.md) · **Português (Brasil)**

[![npm](https://img.shields.io/npm/v/stagebook-mcp)](https://www.npmjs.com/package/stagebook-mcp)

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

Apagar, tirar uma faixa, esvaziar um setlist, substituir a biblioteca e restaurar os ajustes (eles substituem o visual
do palco, os pedais, a interface de áudio USB e a banda do tablet) pedem `confirm: true`: o assistente precisa
perguntar a você antes.

Os arquivos a enviar (backing tracks, backups) vão com o caminho completo; `~` é a sua pasta pessoal. O tablet aceita
faixas de até 200 MB e backups de até 1 GB. Se o assistente desistir de uma chamada longa, o envio ou a restauração
param também.

## Instalar

Precisa do [Node.js](https://nodejs.org) 20 ou mais novo. O servidor está no npm como
[`stagebook-mcp`](https://www.npmjs.com/package/stagebook-mcp): nada para baixar do GitHub.

Não precisa rodar à mão: o assistente abre ele sozinho com os ajustes abaixo (o `npx` baixa da primeira vez e mantém
atualizado). Rodado à mão, ele só fica esperando um assistente: Ctrl+C fecha.

## Ligar no seu assistente

**Claude Code**

```sh
claude mcp add stagebook -- npx -y stagebook-mcp
```

**Claude Desktop** — Configurações → Desenvolvedor → Editar configuração (`claude_desktop_config.json`):

```json
{ "mcpServers": { "stagebook": { "command": "npx", "args": ["-y", "stagebook-mcp"] } } }
```

**Cursor** (`~/.cursor/mcp.json`) e **Windsurf** (`~/.codeium/windsurf/mcp_config.json`): o mesmo bloco `mcpServers`.

**VS Code** (`.vscode/mcp.json` num projeto, ou o `mcp.json` do usuário):

```json
{ "servers": { "stagebook": { "type": "stdio", "command": "npx", "args": ["-y", "stagebook-mcp"] } } }
```

**Gemini CLI** (`~/.gemini/settings.json`): o mesmo bloco `mcpServers` do Claude Desktop.

**OpenAI Codex CLI** (`~/.codex/config.toml`):

```toml
[mcp_servers.stagebook]
command = "npx"
args = ["-y", "stagebook-mcp"]
```

O app do ChatGPT só se conecta a servidores remotos (pela internet), então não alcança o tablet no seu Wi-Fi.

## Primeira vez

1. No tablet: abra o Stagebook e ligue **Ajustes → Editar no computador**. Ele mostra o endereço
   (ex.: `http://192.168.0.10:8090`). O computador e o tablet precisam estar no mesmo Wi-Fi.
2. Peça ao assistente para conectar no Stagebook. Ele procura na rede (ou você passa o endereço) e o tablet mostra um
   pedido com um código. Se mais de um tablet com o Stagebook responder (uma banda no mesmo Wi-Fi), ele pergunta qual
   endereço é o seu; se o endereço do seu tablet mudou, ele procura de novo.
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
- **"Already showing a request, or one was just denied"**: responda o pedido no tablet; depois de um Negar, ele espera
  5 minutos para aceitar outro.
- **"Not found on the tablet, or that part is turned off"**: receber por código precisa da internet ligada, e as
  ferramentas de banda precisam da banda ligada, nos Ajustes do tablet.

## Privacidade

Tudo fica na sua rede: o servidor só conversa com o tablet. O assistente vê o que as ferramentas devolvem (títulos,
artistas, acordes e, quando ele pede, a letra) e manda isso para o provedor de IA dele, como o resto da conversa. Letras
têm direito autoral: o servidor orienta o assistente a não reproduzi-las inteiras.

## Para quem programa

Só para mudar o próprio servidor; para usar, basta o [Instalar](#instalar).

```sh
git clone https://github.com/julianoAffonso/stagebook.git
cd stagebook/mcp
npm install
npm test   # compila e roda os testes contra um tablet de mentira
```

As ferramentas ficam em `src/server.ts`; as chamadas ao tablet em `src/client.ts` (a mesma API HTTP da página do editor
no computador, em `data/web/` no app).
