#!/usr/bin/env node
// Stagebook MCP server over stdio: what Claude Code, Claude Desktop, Cursor, VS Code, Gemini CLI, Codex and other MCP
// clients start as a local command.

import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { StagebookClient } from "./client.js";
import { buildServer } from "./server.js";

const client = await StagebookClient.fromEnv();
const server = buildServer(client);
await server.connect(new StdioServerTransport());
