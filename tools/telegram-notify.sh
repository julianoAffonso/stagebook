#!/usr/bin/env bash
# Sends one plain-text message to the maintainer's Telegram chat. Skips quietly (exit 0) without the two secrets, so
# the workflow stays green until the bot is set up. The text is data: it goes out as plain text (no formatting mode),
# stripped of control characters and cut at 400 characters; the token never reaches the command line or the log.
# Usage: TELEGRAM_BOT_TOKEN=… TELEGRAM_CHAT_ID=… telegram-notify.sh "text"
set -uo pipefail

if [ -z "${TELEGRAM_BOT_TOKEN:-}" ] || [ -z "${TELEGRAM_CHAT_ID:-}" ]; then
  echo "telegram: secrets not set, skipping"
  exit 0
fi

text=$(printf '%s' "${1:-}" | tr -d '\000-\010\013\014\016-\037' | cut -c1-400)
[ -n "$text" ] || exit 0

payload=$(jq -n --arg chat "$TELEGRAM_CHAT_ID" --arg text "$text" \
  '{chat_id: $chat, text: $text, disable_web_page_preview: true}')

# the URL (with the token) is read from stdin as a curl config, never passed as an argument
code=$(printf 'url = "https://api.telegram.org/bot%s/sendMessage"\n' "$TELEGRAM_BOT_TOKEN" |
  curl -sS -o /dev/null -w '%{http_code}' -m 20 --retry 2 -K - \
    -H 'Content-Type: application/json' -d "$payload") || code=000

if [ "$code" != 200 ]; then
  echo "telegram: send failed (HTTP $code)"
  exit 0   # a failed notice never fails the workflow that watches something else
fi
echo "telegram: sent"
