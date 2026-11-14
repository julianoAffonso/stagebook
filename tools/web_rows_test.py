#!/usr/bin/env python3
"""Runs tools/web_rows_test.mjs (the editor page's song rows) with node; skipped where node isn't installed."""
import shutil, subprocess, sys, pathlib

node = shutil.which("node")
if not node:
    print("node not found: web_rows_test skipped")
    sys.exit(0)
here = pathlib.Path(__file__).resolve().parent
sys.exit(subprocess.run([node, "--test", str(here / "web_rows_test.mjs")]).returncode)
