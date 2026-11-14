#!/usr/bin/env python3
"""Tests of release_notes.py: python3 -I tools/release_notes_test.py"""
import contextlib
import importlib.util
import io
import pathlib
import types
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location("release_notes", pathlib.Path(__file__).with_name("release_notes.py"))
notes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notes)


class Parse(unittest.TestCase):
    def test_reads_every_note_of_a_message(self):
        message = "Title · Título\n\nBody.\n\nRelease-Note: One · Um\nRelease-Note: Two · Dois\nCo-Authored-By: x\n"
        self.assertEqual([("One", "Um"), ("Two", "Dois")], notes.parse(message))

    def test_a_message_without_notes_has_none(self):
        self.assertEqual([], notes.parse("Title · Título\n\nRelease-Notes are mentioned here."))

    def test_one_language_is_refused(self):
        with self.assertRaises(ValueError):
            notes.parse("Release-Note: Only English")

    def test_an_empty_half_is_refused(self):
        with self.assertRaises(ValueError):
            notes.parse("Release-Note: English · ")

    def test_a_long_sentence_is_refused(self):
        with self.assertRaises(ValueError):
            notes.parse(f"Release-Note: {'x' * 121} · curta")


    def test_a_generic_sentence_is_refused(self):
        for line in ("Internal fixes · Correções internas", "Fixed the player · Melhorias.", "Bug Fixes! · Tocador novo"):
            with self.assertRaises(ValueError):
                notes.parse(f"Release-Note: {line}")

    def test_a_sentence_that_only_starts_generic_is_kept(self):
        self.assertEqual([("Fixes for the player", "Correções no tocador")],
                         notes.parse("Release-Note: Fixes for the player · Correções no tocador"))

    def test_an_internal_change_has_no_note(self):
        self.assertEqual([], notes.parse("Title · Título\n\nRelease-Note: none\n"))


def fake_git(*messages):
    """git for the range: each message a commit (hashes aaaaaaa1…, oldest first), the app changed."""
    def git(*args):
        if any(a.startswith("--format=%H") for a in args):
            return "".join(f"{'aaaaaaa' + str(i + 1)}\x01{m}\0" for i, m in enumerate(messages))
        return "app/src/main/Tela.kt\n"
    return git


class Check(unittest.TestCase):
    def run_check(self, *messages):
        with mock.patch.object(notes, "git", fake_git(*messages)):
            notes.check(types.SimpleNamespace(commits="a..b"))

    def test_the_app_changed_without_a_note_fails(self):
        with self.assertRaises(SystemExit):
            self.run_check("Title · Título\n")

    def test_the_app_changed_with_a_note_passes(self):
        self.run_check("Title · Título\n\nRelease-Note: One · Um\n")

    def test_the_app_changed_marked_internal_passes(self):
        self.run_check("Title · Título\n\nRelease-Note: none\n")


class Replaces(unittest.TestCase):
    def notes_of(self, *messages, strict=False):
        with mock.patch.object(notes, "git", fake_git(*messages)):
            return notes.notes_in("a..b", strict)

    def test_a_later_commit_replaces_an_earlier_note(self):
        first = "A · A\n\nRelease-Note: After 10 s it stops · Depois de 10 s para\n"
        later = "B · B\n\nRelease-Note: Within a minute it carries on · Em até 1 minuto continua\nRelease-Note-Replaces: aaaaaaa1\n"
        self.assertEqual([("Within a minute it carries on", "Em até 1 minuto continua")], self.notes_of(first, later))

    def test_without_the_line_both_notes_stay(self):
        first = "A · A\n\nRelease-Note: One · Um\n"
        later = "B · B\n\nRelease-Note: Two · Dois\n"
        self.assertEqual([("One", "Um"), ("Two", "Dois")], self.notes_of(first, later))

    def test_a_replaces_line_for_a_commit_outside_the_range_fails_the_check(self):
        with self.assertRaises(ValueError):
            self.notes_of("B · B\n\nRelease-Note: Two · Dois\nRelease-Note-Replaces: bbbbbbb9\n", strict=True)

    def test_the_replaces_line_is_not_read_as_a_note(self):
        self.assertEqual([], notes.parse("B · B\n\nRelease-Note-Replaces: aaaaaaa1\n"))


class Listing(unittest.TestCase):
    def test_the_check_prints_every_note(self):
        out = io.StringIO()
        with mock.patch.object(notes, "git", fake_git("A · A\n\nRelease-Note: One · Um\n")), contextlib.redirect_stdout(out):
            notes.check(types.SimpleNamespace(commits="a..b"))
        self.assertIn("One · Um", out.getvalue())


class History(unittest.TestCase):
    def test_new_version_goes_first_with_a_short_name(self):
        data = notes.with_history(733, "0.1.733 (abc1234)", [("One", "Um")], {})
        self.assertEqual("0.1.733 (abc1234)", data["versionName"])
        self.assertEqual([{"versionCode": 733, "versionName": "0.1.733", "en": ["One"], "pt": ["Um"]}], data["notes"])

    def test_keeps_older_versions_up_to_the_limit(self):
        previous = {"notes": [{"versionCode": c} for c in range(720, 700, -1)]}
        codes = [v["versionCode"] for v in notes.with_history(733, "0.1.733", [("One", "Um")], previous)["notes"]]
        self.assertEqual([733, *range(720, 711, -1)], codes)

    def test_a_republished_version_doesn_t_repeat(self):
        previous = {"notes": [{"versionCode": 733}, {"versionCode": 729}]}
        codes = [v["versionCode"] for v in notes.with_history(733, "0.1.733", [("One", "Um")], previous)["notes"]]
        self.assertEqual([733, 729], codes)

    # v0.1.1086 and v0.1.1090 went out described by commit titles, and were rewritten by hand
    def test_the_description_says_the_notes_in_english_then_portuguese(self):
        text = notes.body([("One thing", "Uma coisa"), ("Another one.", "Outra.")])
        self.assertEqual("- One thing.\n- Another one.\n- Uma coisa.\n- Outra.\n", text)

    def test_the_file_of_an_old_release_has_no_notes(self):
        self.assertEqual(1, len(notes.with_history(733, "0.1.733", [("One", "Um")], {"versionCode": 729})["notes"]))


if __name__ == "__main__":
    unittest.main()
