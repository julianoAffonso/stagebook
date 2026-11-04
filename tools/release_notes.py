#!/usr/bin/env python3
"""What's new in a version, for the update window. Every change people will notice carries, in its commit message, a line

    Release-Note: English sentence · Frase em português

A change to the app that people won't notice says so with "Release-Note: none", which never reaches the file.

  check RANGE   for pull requests: fails if the range changes the app and has no note, or if a note is malformed
  build RANGE --code N --name X --previous FILE --out FILE
                for the Release workflow: writes stagebook-version.json with this version's notes on top of the last
                ones (someone who skipped versions sees what they missed). A version without notes isn't published.
"""
import argparse
import json
import re
import subprocess
import sys

MAX_VERSIONS = 10   # the file the app reads on every launch stays small
MAX_LENGTH = 120    # a sentence in a window on a phone
LINE = re.compile(r"^Release-Note:[ \t]*(.*)$", re.MULTILINE)
HOW = "Add to a commit: Release-Note: English sentence · Frase em português (or Release-Note: none, if nobody will notice)"
INTERNAL = "none"
# Say what people will notice; a change they won't notice is INTERNAL, not one of these.
GENERIC = {"bug fixes", "fixes", "improvements", "internal fixes", "internal improvements", "ajustes", "ajustes internos",
           "correções", "correções de bugs", "correções internas", "melhorias", "melhorias internas"}


def parse(message):
    """The (en, pt) notes of a commit message. Raises ValueError on a malformed one."""
    notes = []
    for line in LINE.findall(message):
        if line.strip() == INTERNAL:
            continue
        parts = [p.strip() for p in line.split(" · ")]
        if len(parts) != 2 or not all(parts):
            raise ValueError(f"needs English · Portuguese: {line!r}")
        if max(map(len, parts)) > MAX_LENGTH:
            raise ValueError(f"longer than {MAX_LENGTH} characters: {line!r}")
        if any(part.rstrip(".!").casefold() in GENERIC for part in parts):
            raise ValueError(f"too generic, say what people will notice or use {INTERNAL}: {line!r}")
        notes.append(tuple(parts))
    return notes


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def messages_of(commits):
    return git("log", "--no-merges", "--reverse", "--format=%B%x00", commits).split("\0")


def notes_in(commits):
    """Notes of the range, oldest first, each sentence once."""
    return list(dict.fromkeys(note for message in messages_of(commits) for note in parse(message)))


def marked_internal(commits):
    return any(line.strip() == INTERNAL for message in messages_of(commits) for line in LINE.findall(message))


def app_changed(commits):
    return bool(git("log", "--no-merges", "--format=", "--name-only", commits, "--", "app/src/main").strip())


def with_history(code, name, notes, previous):
    """The file's content: [notes] first, then the versions before it, up to MAX_VERSIONS."""
    version = {
        "versionCode": code,
        "versionName": name.split()[0],
        "en": [en for en, _ in notes],
        "pt": [pt for _, pt in notes],
    }
    older = [v for v in previous.get("notes", []) if v.get("versionCode", code) < code]
    return {"versionCode": code, "versionName": name, "notes": [version, *older][:MAX_VERSIONS]}


def check(args):
    notes = notes_in(args.commits)
    if not notes and app_changed(args.commits) and not marked_internal(args.commits):
        sys.exit(f"The app changed and no commit says what's new. {HOW}")
    print(f"{len(notes)} release notes")


def build(args):
    notes = notes_in(args.commits)
    if not notes:
        sys.exit(f"No release notes since the last version: nothing to tell people. {HOW}")
    with open(args.previous, encoding="utf-8") as f:
        previous = json.load(f)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(with_history(args.code, args.name, notes, previous), f, ensure_ascii=False)
    print(f"{len(notes)} release notes")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(required=True)
    p = commands.add_parser("check")
    p.add_argument("commits")
    p.set_defaults(run=check)
    p = commands.add_parser("build")
    p.add_argument("commits")
    p.add_argument("--code", type=int, required=True)
    p.add_argument("--name", required=True)
    p.add_argument("--previous", required=True)
    p.add_argument("--out", required=True)
    p.set_defaults(run=build)
    args = parser.parse_args()
    try:
        args.run(args)
    except ValueError as e:
        sys.exit(f"Bad release note, {e}")


if __name__ == "__main__":
    main()
