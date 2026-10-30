#!/bin/bash
# Publishes a version on GitHub Releases (julianoAffonso/stagebook), with two fixed-name files:
#   stagebook.apk            the app (shrunk release, signed with the key in ~/.android/stagebook-release.*)
#   stagebook-version.json   versionCode/versionName of stagebook.apk; the app (Settings → Update the app and the
#                            new version notice) reads both from releases/latest/download/, always the newest
# With the repository private the release goes out, but the app can't download it (it needs a login): open the
# repository before distributing.
#
#   ./publish.sh
set -euo pipefail
cd "$(dirname "$0")"
# an audit was requested and is still open (the prompt hook created the marker): don't publish before it's done
if [ -f .claude/audit-pending ]; then
    echo "Audit requested and still open ($(cat .claude/audit-pending)): finish the audit and delete .claude/audit-pending before publishing." >&2
    exit 1
fi
# the version comes from git (commit count): publish only what is committed, otherwise the number means nothing
if [ -n "$(git status --porcelain)" ]; then
    echo "There are uncommitted changes: commit before publishing." >&2
    exit 1
fi
if [ ! -f "$HOME/.android/stagebook-release.properties" ]; then
    echo "No signing key (~/.android/stagebook-release.*): the release can't be published." >&2
    exit 1
fi
./gradlew -q testDebugUnitTest lintDebug assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
TOOLS=$(ls -d "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/build-tools/* | sort -V | tail -1)
# signed with Stagebook's key? (another key = devices refuse the update)
CERT=a0af59f083c430bfcd0aa42ff825f01277f4afc3c3fb0f9aba80225842a1c4a5
if ! "$TOOLS/apksigner" verify --print-certs "$APK" 2>/dev/null | grep -q "SHA-256 digest: $CERT"; then
    echo "The APK isn't signed with Stagebook's key: not publishing." >&2
    exit 1
fi
badging=$("$TOOLS/aapt" dump badging "$APK" | head -1)
code=$(sed -E "s/.*versionCode='([0-9]+)'.*/\1/" <<<"$badging")
name=$(sed -E "s/.*versionName='([^']+)'.*/\1/" <<<"$badging")
tag="v${name%% *}"   # v0.1.<commit count>
out=app/build/publish
rm -rf "$out" && mkdir -p "$out"
cp "$APK" "$out/stagebook.apk"
printf '{"versionCode":%s,"versionName":"%s"}\n' "$code" "$name" > "$out/stagebook-version.json"
# the release points to the commit: it has to be on GitHub
git push -q origin HEAD
# notes: the commits since the last release (titles only)
last=$(git describe --tags --abbrev=0 2>/dev/null || true)
notes=$(git log --format='- %s' ${last:+$last..}HEAD)
gh release create "$tag" "$out/stagebook.apk" "$out/stagebook-version.json" \
    --target "$(git rev-parse HEAD)" --title "Stagebook ${name%% *}" --notes "$notes" --latest
git fetch -q --tags
echo "published: $name ($tag)"
