// What npm publishes: the version the server reports is package.json's, and the packed file starts as a command.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { VERSION } from "../dist/server.js";

const pkg = JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8"));

test("the server reports package.json's version", () => {
  assert.equal(VERSION, pkg.version);
});

test("the command npx runs is an executable script", () => {
  assert.ok(readFileSync(new URL("../" + pkg.bin["stagebook-mcp"], import.meta.url), "utf8").startsWith("#!/usr/bin/env node\n"));
});
