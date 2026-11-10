#!/usr/bin/env bash
# Which app trees passed tools/validate.sh (observation 0029, user 09/11): the pre-commit hook records the tree it
# validated, and pre-push refuses to send a commit whose app tree never passed (a clean cherry-pick, a rebase or a merge
# makes commits without running pre-commit). The key covers only what validate.sh builds, so a docs-only commit on top
# of a validated tree keeps its key.
#   tools/validated.sh key <commit|tree>   prints the key of that tree's app part
#   tools/validated.sh has <key>           exits 0 if it was validated
#   tools/validated.sh add <key>           records it
#   tools/validated.sh dirty               lists the files of that part the working tree has and the index doesn't
set -euo pipefail
PATHS=(app gradle build.gradle.kts settings.gradle.kts gradle.properties)
LIST="$(git rev-parse --git-common-dir)/stagebook-validated"
case "${1:-}" in
  key) git ls-tree "$2" -- "${PATHS[@]}" | git hash-object --stdin ;;
  has) [ -f "$LIST" ] && grep -qx "$2" "$LIST" ;;
  add) echo "$2" >> "$LIST"; tail -n 300 "$LIST" > "$LIST.tmp" && mv "$LIST.tmp" "$LIST" ;;
  dirty) git diff --name-only -- "${PATHS[@]}"; git ls-files --others --exclude-standard -- "${PATHS[@]}" ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac
