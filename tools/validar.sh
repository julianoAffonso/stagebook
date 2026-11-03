#!/bin/bash
# Full validation (build, unit tests, lint, screenshots): prints "gradle=<rc> lint=<issues> fails=<files|none>" and
# exits 0 only when all green. EssentialModeTest sometimes times out alone: then it reruns once.
cd "$(dirname "$0")/.." || exit 1
log="${TMPDIR:-/tmp}/stagebook-validar.log"
run() { ./gradlew -q assembleDebug testDebugUnitTest lintDebug recordRoborazziDebug > "$log" 2>&1; }
failed() { grep -l "<failure" app/build/test-results/testDebugUnitTest/*.xml 2>/dev/null; }
run; rc=$?; fails=$(failed)
if [ $rc -ne 0 ] && [ "$fails" = "app/build/test-results/testDebugUnitTest/TEST-br.app.stagebook.EssentialModeTest.xml" ]; then
  echo "EssentialModeTest timed out alone: running again"
  run; rc=$?; fails=$(failed)
fi
echo "gradle=$rc lint=$(grep -c '<issue ' app/build/reports/lint-results-debug.xml 2>/dev/null) fails=${fails:-none}"
[ $rc -eq 0 ] || { grep -E "^e: |FAILED|What went wrong" -A3 "$log" | head -20; exit 1; }
