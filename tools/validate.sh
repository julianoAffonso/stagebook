#!/bin/bash
# Full validation (build, unit tests, lint, screenshots): prints "gradle=<rc> lint=<issues> fails=<files|none>" and
# exits 0 only when all green. A failing test is never rerun here: a test that fails now and then is investigated.
cd "$(dirname "$0")/.." || exit 1
log="${TMPDIR:-/tmp}/stagebook-validar.log"
report=app/build/reports/lint-results-debug.xml
# A lint report left by an earlier run must never stand in for this one: on 14/11 a tree with a lint error in a test file
# looked green in the worktree it was made in and failed in a clean one. The report is removed first and lintDebug is
# forced to run (--rerun), so a missing report means lint did not run, which counts as a failure, never as "lint=0".
rm -f "$report"
run() { ./gradlew -q assembleDebug testDebugUnitTest lintDebug --rerun recordRoborazziDebug > "$log" 2>&1; }
failed() { grep -l "<failure" app/build/test-results/testDebugUnitTest/*.xml 2>/dev/null; }
run; rc=$?; fails=$(failed)
if [ -f "$report" ]; then lint=$(grep -c '<issue ' "$report"); else lint=missing; fi
echo "gradle=$rc lint=$lint fails=${fails:-none}"
[ $rc -eq 0 ] || { grep -E "^e: |FAILED|What went wrong" -A3 "$log" | head -20; exit 1; }
[ "$lint" != missing ] || { echo "lint report missing: lintDebug did not produce $report"; exit 1; }
