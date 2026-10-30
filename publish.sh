#!/bin/bash
# Publishes a version: checks that main is ready and pushes the version tag (v0.1.<commit count>). GitHub Actions
# (.github/workflows/release.yml) then builds, signs with the key in the repository secrets and creates the release
# with stagebook.apk and stagebook-version.json, which the app reads from releases/latest/download/.
# With the repository private the release goes out, but the app can't download it (it needs a login): open the
# repository before distributing.
#
#   ./publish.sh          (on main, in sync with origin/main)
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
git fetch -q origin main --tags
if [ "$(git rev-parse --abbrev-ref HEAD)" != main ] || [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
    echo "Publish from main, in sync with origin/main (merge dev into main and push first)." >&2
    exit 1
fi
tag="v0.1.$(git rev-list --count HEAD)"
if git rev-parse -q --verify "refs/tags/$tag" > /dev/null; then
    echo "Tag $tag already exists: nothing new to publish." >&2
    exit 1
fi
git tag -a "$tag" -m "Stagebook ${tag#v}"
git push -q origin "$tag"
echo "Tag $tag pushed. GitHub Actions is building and publishing it:"
gh run list --workflow release.yml --limit 1
