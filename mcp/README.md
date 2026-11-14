# Stagebook MCP

**English** · [Português (Brasil)](README.pt-BR.md)

[![npm](https://img.shields.io/npm/v/stagebook-mcp)](https://www.npmjs.com/package/stagebook-mcp)

Lets AI assistants read and edit the songs and setlists on your Stagebook tablet: "build a 15-song setlist for Saturday
without repeating artists", "move every Skank song down a half tone", "check the chords of Hazard", "import this page".
It's a [Model Context Protocol](https://modelcontextprotocol.io) server that runs on your computer and talks to the
tablet over the local network, through the same connection as **Settings → Edit on the computer**. Nothing goes to the
internet, and the app itself doesn't change: what the assistant saves shows on the stage right away.

Works with Claude Code, Claude Desktop and any assistant that runs local MCP servers (Cursor, VS Code with Copilot,
Windsurf, Gemini CLI, OpenAI Codex CLI…).

## What it can do

| Area | Tools |
|---|---|
| Connection | `stagebook_connect` (finds the tablet and asks to come in), `stagebook_status` |
| Songs | `list_songs` (filters: text, tag, key), `get_song`, `create_song`, `update_song`, `delete_song`, `transpose_song`, `set_capo` |
| Import | `import_song` (pasted lyrics/chords in any common format, or a song page address) |
| Backing tracks | `set_backing_track` (OGG, MP3 or WAV from this computer), `remove_backing_track` |
| Setlists | `list_setlists` (filter: name), `setlist_order` (newest or A to Z, the same as the app's tab), `get_setlist`, `create_setlist`, `update_setlist`, `delete_setlist` |
| Receiving | `receive_by_code` (the 3 words), `receive_create`, `band_inbox`, `band_sync`, `open_band_item` |
| Backup | `restore_backup` (inspect a `.stagebook` file, add what's missing or replace the library) |

A setlist's **breaks** (intervals) come with `get_setlist` as `"breaks": [2]`: the positions where the show stops (1 = before
the second song). `create_setlist` and `update_setlist` take an optional `breaks` list (integers from 1 to the number of
songs minus 1, ascending, no repeats; a position that doesn't fit is refused with the reason). `create_setlist` without
`breaks` makes none; `update_setlist` without `breaks` keeps the existing ones before the same songs; `[]` takes them all out.

Also **resources** (`stagebook://songs`, `stagebook://setlists`, `stagebook://songs/{id}` as ChordPro) and **prompts**
(`build_setlist`, `fix_chords`, `transpose_for_singer`).

Deleting, removing a track, emptying a setlist, replacing the library and restoring settings (they replace the
tablet's stage look, pedals, USB audio interface and band) need `confirm: true`: the assistant has to ask you first.

Files to send (backing tracks, backups) take a full path; `~` is your home folder. The tablet takes tracks up to
200 MB and backups up to 1 GB. If the assistant gives up on a long call, the upload or restore stops too.

## Install

Needs [Node.js](https://nodejs.org) 20 or newer. The server is on npm as
[`stagebook-mcp`](https://www.npmjs.com/package/stagebook-mcp): nothing to download from GitHub.

You don't run it by hand: your assistant starts it by itself with the settings below (`npx` downloads it the first time and
keeps it up to date). Run by hand, it just waits for an assistant: Ctrl+C closes it.

## Connect your assistant

**Claude Code**

```sh
claude mcp add stagebook -- npx -y stagebook-mcp
```

**Claude Desktop** — Settings → Developer → Edit config (`claude_desktop_config.json`):

```json
{ "mcpServers": { "stagebook": { "command": "npx", "args": ["-y", "stagebook-mcp"] } } }
```

**Cursor** (`~/.cursor/mcp.json`) and **Windsurf** (`~/.codeium/windsurf/mcp_config.json`): the same `mcpServers` block.

**VS Code** (`.vscode/mcp.json` in a project, or the user `mcp.json`):

```json
{ "servers": { "stagebook": { "type": "stdio", "command": "npx", "args": ["-y", "stagebook-mcp"] } } }
```

**Gemini CLI** (`~/.gemini/settings.json`): the same `mcpServers` block as Claude Desktop.

**OpenAI Codex CLI** (`~/.codex/config.toml`):

```toml
[mcp_servers.stagebook]
command = "npx"
args = ["-y", "stagebook-mcp"]
```

ChatGPT's own app only connects to remote servers (over the internet), so it can't reach the tablet on your Wi-Fi.

## First use

1. On the tablet: open Stagebook and turn on **Settings → Edit on the computer**. It shows the address
   (e.g. `http://192.168.0.10:8090`). The computer and the tablet must be on the same Wi-Fi.
2. Ask the assistant to connect to Stagebook. It searches the network (or you give it the address) and the tablet shows
   a request with a code. If more than one tablet with Stagebook answers (a band on the same Wi-Fi), it asks which
   address is yours; if your tablet's address changed, it searches again.
3. If the code matches, tap **Allow** on the tablet. The key is saved on this computer
   (`~/.config/stagebook-mcp/config.json`, readable only by you); next time it connects without asking.

The editor turns itself off on the tablet when unused: turn it on again when you want to use the assistant. The key
lasts 90 days without use; **Settings → Edit on the computer → Authorized computers** removes it at any time.

### Settings

| Variable | What for |
|---|---|
| `STAGEBOOK_URL` | The tablet's address, skipping the search (e.g. `http://192.168.0.10:8090`). |
| `STAGEBOOK_KEY` | A key instead of the saved one. |
| `STAGEBOOK_CONFIG` | Another place for the saved address and key. |

## If it doesn't connect

- **"Stagebook wasn't found" / "Couldn't reach"**: the tablet's Stagebook must be open with **Settings → Edit on the
  computer** on, on the same Wi-Fi as the computer (not a guest network). Pass the address shown there to the connect tool.
- **On a Mac, "macOS blocked the connection"**: macOS asks before an app talks to devices on the local network. Open
  **System Settings → Privacy & Security → Local Network** and turn on the app that runs the assistant (Claude, Terminal,
  iTerm, Cursor, VS Code…), then restart that app.
- **"No longer accepts this computer's key"**: it was removed on the tablet or went 90 days unused. Connect again.
- **"Already showing a request, or one was just denied"**: answer the request on the tablet; after a Deny it waits
  5 minutes before taking a new one.
- **"Not found on the tablet, or that part is turned off"**: receiving by code needs the internet on, and the band
  tools need the band on, in the tablet's Settings.

## Privacy

Everything stays on your network: the server talks only to the tablet. The assistant sees what its tools return
(titles, artists, chords and, when it asks for them, the lyrics) and sends that to its AI provider like the rest of your
conversation. Lyrics are copyrighted: the server tells the assistant not to reproduce them in full.

## Development

Only to change the server itself; to use it, [Install](#install) is all you need.

```sh
git clone https://github.com/julianoAffonso/stagebook.git
cd stagebook/mcp
npm install
npm test   # builds and runs the tests against a fake tablet
```

The tools are in `src/server.ts`; the calls to the tablet in `src/client.ts` (the same HTTP API as the computer editor's
page, in the app's `data/web/`).
