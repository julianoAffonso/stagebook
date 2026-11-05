#!/usr/bin/env python3
"""The help follows the app. A change people will notice (it has a Release-Note sentence) comes with the help rebuilt,
and if it touches a screen, with new screenshots too. A change that needs neither says so in a commit message:

    Help: none
    Screens: none

  check RANGE   for pull requests: fails if the range changes the app and leaves the help or the screenshots behind
  pages         the help as it is now: no broken link or image, no image left over, the same images in both languages
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PAGES = ("docs/ajuda", "docs/help")
APP = "app/src/main/"
SCREEN = re.compile(r"^app/src/main/(java/.*/ui/|res/|assets/web/)")
NOTE = re.compile(r"^Release-Note:[ \t]*(.*)$", re.MULTILINE)


def waived(messages, what):
    return any(re.search(rf"^{what}:[ \t]*none[ \t]*$", m, re.MULTILINE) for m in messages)


def noticed(messages):
    return any(note.strip() != "none" for m in messages for note in NOTE.findall(m))


def problems(messages, files):
    """What the range left behind, given its commit messages and changed files."""
    if not noticed(messages) or not any(f.startswith(APP) for f in files):
        return []
    found = []
    if not waived(messages, "Help") and not all(f"{p}/index.html" in files for p in PAGES):
        found.append("The app changed and the help didn't: edit tools/help_pages.py and run it, or add to a commit: Help: none")
    if any(SCREEN.match(f) for f in files) and not waived(messages, "Screens") \
            and not all(any(f.startswith(f"{p}/img/") for f in files) for p in PAGES):
        found.append("A screen changed and no screenshot did: run tools/screenshots.sh and tools/help_pages.py, or add to a commit: Screens: none")
    return found


def page_problems(page, images):
    """Broken links and images of one page, given its html and the files in its img folder."""
    ids = set(re.findall(r' id="([^"]+)"', page))
    used = set(re.findall(r'(?:src|data-tablet|data-phone)="img/([^"]+)"', page))
    found = [f"broken link: #{a}" for a in sorted(set(re.findall(r'href="#([^"]+)"', page)) - ids)]
    found += [f"missing image: {i}" for i in sorted(used - images)]
    return found + [f"image left over: {i}" for i in sorted(images - used)]


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def check(commits):
    messages = git("log", "--no-merges", "--format=%B%x00", commits).split("\0")
    files = set(git("diff", "--name-only", commits.replace("..", "...")).split("\n"))
    found = problems(messages, files)
    sys.exit("\n".join(found)) if found else print("help ok")


def pages():
    images = {p: {f.name for f in (ROOT / p / "img").iterdir()} for p in PAGES}
    found = [f"{p}: {x}" for p in PAGES for x in page_problems((ROOT / p / "index.html").read_text(encoding="utf-8"), images[p])]
    if len({frozenset(i) for i in images.values()}) > 1:
        found.append("the two languages have different images")
    sys.exit("\n".join(found)) if found else print("pages ok")


if __name__ == "__main__":
    if sys.argv[1:2] == ["check"] and len(sys.argv) == 3:
        check(sys.argv[2])
    elif sys.argv[1:] == ["pages"]:
        pages()
    else:
        sys.exit(__doc__)
