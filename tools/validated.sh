#!/usr/bin/env bash
# Which app trees passed tools/validate.sh (observation 0029, user 09/11): the pre-commit hook records the tree it
# validated, and pre-push refuses to send a commit whose app tree never passed (a clean cherry-pick, a rebase or a merge
# makes commits without running pre-commit). The key covers only what validate.sh builds, so a docs-only commit on top
# of a validated tree keeps its key.
# The list keeps the last 5000 keys, oldest dropped first, no duplicates (an existing key is not written again, so it
# keeps its place). It used to keep 300: after many validations (agents validate commit by commit) the keys of older
# commits of the same branch fell off and pre-push refused the push twice. The guarantee is the same: a key only enters
# after tools/validate.sh passed (gradle=0 lint=0 fails=none); only how long it is remembered changed. Test:
# tools/validated_test.py.
#   tools/validated.sh key <commit|tree>   prints the key of that tree's app part
#   tools/validated.sh has <key>           exits 0 if it was validated
#   tools/validated.sh add <key>           records it
#   tools/validated.sh dirty               lists the files of that part the working tree has and the index doesn't
#   tools/validated.sh unlike-head         lists the files of that part that differ from HEAD (staged, changed or new)
set -euo pipefail
PATHS=(app gradle build.gradle.kts settings.gradle.kts gradle.properties)
LIST="$(git rev-parse --git-common-dir)/stagebook-validated"
MAX=5000
case "${1:-}" in
  key) git ls-tree "$2" -- "${PATHS[@]}" | git hash-object --stdin ;;
  has) [ -f "$LIST" ] && grep -qxF -- "$2" "$LIST" ;;
  add)
    [ -f "$LIST" ] && grep -qxF -- "$2" "$LIST" && exit 0   # already there: keep its place, no duplicate
    echo "$2" >> "$LIST"
    if [ "$(wc -l < "$LIST")" -gt "$MAX" ]; then tail -n "$MAX" "$LIST" > "$LIST.tmp" && mv "$LIST.tmp" "$LIST"; fi ;;
  dirty) git diff --name-only -- "${PATHS[@]}"; git ls-files --others --exclude-standard -- "${PATHS[@]}" ;;
  unlike-head) git status --porcelain --untracked-files=all -- "${PATHS[@]}" | cut -c4- ;;
  *) sed -n '2,15p' "$0"; exit 2 ;;
esac
