#!/bin/bash
# Two simultaneous runs of the log line of tools/validate.sh must never share the same path.
cd "$(dirname "$0")/.." || exit 1
line=$(grep '^log="\$(mktemp' tools/validate.sh) || { echo "FAIL: validate.sh has no per-run mktemp log"; exit 1; }
dir=$(mktemp -d); trap 'rm -rf "$dir"' EXIT
export TMPDIR="$dir"
for i in 1 2 3 4 5 6; do ( eval "$line"; echo "$log" > "$dir/out$i"; sleep 1 ) & done
wait
n=$(cat "$dir"/out* | sort -u | wc -l)
[ "$n" -eq 6 ] && echo "ok: 6 runs, 6 distinct logs" || { echo "FAIL: only $n distinct logs"; exit 1; }
