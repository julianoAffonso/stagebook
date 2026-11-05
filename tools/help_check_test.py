#!/usr/bin/env python3
"""Tests of help_check.py: python3 -I tools/help_check_test.py"""
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("help_check", pathlib.Path(__file__).with_name("help_check.py"))
help_check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(help_check)

NOTE = ["Title\n\nRelease-Note: One · Um\n"]
SCREEN = "app/src/main/java/br/app/stagebook/ui/songs/SongsScreen.kt"
LOGIC = "app/src/main/java/br/app/stagebook/domain/Chords.kt"
HELP = {"docs/ajuda/index.html", "docs/help/index.html"}
SHOTS = {"docs/ajuda/img/a.jpg", "docs/help/img/a.jpg"}


class Problems(unittest.TestCase):
    def test_a_range_outside_the_app_needs_nothing(self):
        self.assertEqual([], help_check.problems(NOTE, {"tools/help_pages.py"}))

    def test_a_change_nobody_notices_needs_nothing(self):
        self.assertEqual([], help_check.problems(["Title\n\nRelease-Note: none\n"], {SCREEN}))

    def test_a_noticed_change_needs_the_help(self):
        self.assertEqual(1, len(help_check.problems(NOTE, {LOGIC})))

    def test_both_languages_are_needed(self):
        self.assertEqual(1, len(help_check.problems(NOTE, {LOGIC, "docs/ajuda/index.html"})))

    def test_logic_with_the_help_passes_without_screenshots(self):
        self.assertEqual([], help_check.problems(NOTE, {LOGIC} | HELP))

    def test_a_screen_needs_screenshots_too(self):
        self.assertEqual(1, len(help_check.problems(NOTE, {SCREEN} | HELP)))

    def test_a_text_counts_as_a_screen(self):
        self.assertEqual(1, len(help_check.problems(NOTE, {"app/src/main/res/values/strings.xml"} | HELP)))

    def test_a_screen_with_help_and_screenshots_passes(self):
        self.assertEqual([], help_check.problems(NOTE, {SCREEN} | HELP | SHOTS))

    def test_a_commit_can_say_neither_is_needed(self):
        self.assertEqual([], help_check.problems(NOTE + ["Fix\n\nHelp: none\nScreens: none\n"], {SCREEN}))

    def test_mentioning_the_line_in_a_sentence_is_not_saying_it(self):
        self.assertEqual(2, len(help_check.problems(NOTE + ["About Help: none in the docs"], {SCREEN})))


class Pages(unittest.TestCase):
    PAGE = '<h2 id="a"></h2><a href="#a"></a><img src="img/x.jpg"><figure data-phone="img/y.jpg">'

    def test_a_good_page_has_no_problems(self):
        self.assertEqual([], help_check.page_problems(self.PAGE, {"x.jpg", "y.jpg"}))

    def test_a_broken_link_is_found(self):
        self.assertEqual(["broken link: #b"], help_check.page_problems(self.PAGE + '<a href="#b">', {"x.jpg", "y.jpg"}))

    def test_a_missing_image_is_found(self):
        self.assertEqual(["missing image: y.jpg"], help_check.page_problems(self.PAGE, {"x.jpg"}))

    def test_an_image_left_over_is_found(self):
        self.assertEqual(["image left over: z.jpg"], help_check.page_problems(self.PAGE, {"x.jpg", "y.jpg", "z.jpg"}))


if __name__ == "__main__":
    unittest.main()
