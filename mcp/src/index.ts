#!/usr/bin/env node
// Stagebook MCP server over stdio: what Claude Code, Claude Desktop, Cursor, VS Code, Gemini CLI, Codex and other MCP
// clients start as a local command.

import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { StagebookClient } from "./client.js";
import { buildServer } from "./server.js";

// Run by hand in a terminal it just waits for an assistant, which looks frozen: say so (stderr, never the protocol)
if (process.stdin.isTTY) {
  process.stderr.write(
    "Stagebook MCP server: running and waiting for an AI assistant. Nothing to do here: your assistant starts it by\n" +
      "itself (setup: https://github.com/julianoAffonso/stagebook/tree/main/mcp). Press Ctrl+C to close.\n",
  );
}

const client = await StagebookClient.fromEnv();
const server = buildServer(client);
await server.connect(new StdioServerTransport());
