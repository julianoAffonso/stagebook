#!/usr/bin/env bash
# Fails when text about to go public carries a session link, a credential or personal data.
# Prints what kind of thing matched, never the matched value.
# Usage: privacy-check.sh message FILE | staged | range BASE..HEAD
set -uo pipefail

found=0
always=(
  'claude\.ai/code/session'
  'Claude-Session[:]'
  'gh[pos]_[A-Za-z0-9]{30,}'
  'github_pat_[A-Za-z0-9_]{30,}'
  'sk-ant-[A-Za-z0-9_-]{20,}'
  'gsk_[A-Za-z0-9]{30,}'
  'npm_[A-Za-z0-9]{30,}'
  'AKIA[0-9A-Z]{16}'
  'AIza[0-9A-Za-z_-]{30,}'
  '-----BEGIN [A-Z ]*PRIVATE KEY'
  '@(gmail|hotmail|outlook|yahoo)\.'
  '/Users/[a-z]{3,}/'
)
prose=('(senha|password|passwd)[ ]*[:=][ ]*[^ ]{4,}')

scan() { # label, patterns...
  local label=$1 text pattern
  shift
  text=$(cat)
  for pattern in "$@"; do
    if printf '%s\n' "$text" | grep -qiE -e "$pattern"; then
      echo "blocked: $label matches $pattern" >&2
      found=1
    fi
  done
}

added_lines() { # git diff arguments
  git diff --unified=0 "$@" -- . ':(exclude)app/src/main/cpp/third_party' | grep -E '^\+' | grep -vE '^\+\+\+'
}

case "${1:-}" in
  message) scan "message" "${always[@]}" "${prose[@]}" < "$2" ;;
  staged) added_lines --cached | scan "staged change" "${always[@]}" ;;
  range)
    git log --format=%B "$2" | scan "commit message" "${always[@]}" "${prose[@]}"
    git log --format='%ae%n%ce' "$2" | scan "commit identity" '@(gmail|hotmail|outlook|yahoo)\.'
    added_lines "${2%%..*}" "${2##*..}" | scan "added line" "${always[@]}"
    ;;
  *) echo "usage: $0 message FILE | staged | range BASE..HEAD" >&2; exit 2 ;;
esac
exit "$found"
