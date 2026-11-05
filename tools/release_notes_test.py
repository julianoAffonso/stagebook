#!/usr/bin/env python3
"""Tests of release_notes.py: python3 -I tools/release_notes_test.py"""
import importlib.util
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


class Check(unittest.TestCase):
    def run_check(self, message):
        def git(*args):
            return f"{message}\0" if "--format=%B%x00" in args else "app/src/main/Tela.kt\n"
        with mock.patch.object(notes, "git", git):
            notes.check(types.SimpleNamespace(commits="a..b"))

    def test_the_app_changed_without_a_note_fails(self):
        with self.assertRaises(SystemExit):
            self.run_check("Title · Título\n")

    def test_the_app_changed_with_a_note_passes(self):
        self.run_check("Title · Título\n\nRelease-Note: One · Um\n")

    def test_the_app_changed_marked_internal_passes(self):
        self.run_check("Title · Título\n\nRelease-Note: none\n")


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

    def test_the_file_of_an_old_release_has_no_notes(self):
        self.assertEqual(1, len(notes.with_history(733, "0.1.733", [("One", "Um")], {"versionCode": 729})["notes"]))


if __name__ == "__main__":
    unittest.main()
