#!/usr/bin/env python3
"""Tests of validated.sh's list of validated trees: python3 -I tools/validated_test.py"""
import os
import pathlib
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("validated.sh").resolve()


class List(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dir = tempfile.TemporaryDirectory()
        subprocess.run(["git", "init", "-q", cls.dir.name], check=True)
        cls.keys = [f"{i:040x}" for i in range(6000)]
        cls.env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
        # 6000 keys: the first 5800 are laid in the list file directly (6000 runs of the script take minutes, too long
        # for a hook) and the last 200 go through `add`, which is what trims the list and must keep the latest 5000
        git_dir = pathlib.Path(cls.dir.name) / ".git"
        (git_dir / "stagebook-validated").write_text("".join(k + "\n" for k in cls.keys[:5800]))
        loop = f'for k in {" ".join(cls.keys[5800:])}; do "{SCRIPT}" add "$k" || exit 1; done'
        subprocess.run(["bash", "-c", loop], cwd=cls.dir.name, env=cls.env, check=True)

    @classmethod
    def tearDownClass(cls):
        cls.dir.cleanup()

    def run_sh(self, *args):
        return subprocess.run([str(SCRIPT), *args], cwd=self.dir.name, env=self.env, capture_output=True, text=True)

    def lines(self):
        return (pathlib.Path(self.dir.name) / ".git" / "stagebook-validated").read_text().split()

    def test_keeps_the_latest_5000_in_order(self):
        self.assertEqual(self.keys[-5000:], self.lines())

    def test_a_repeated_key_is_not_duplicated_and_keeps_its_place(self):
        before = self.lines()
        self.assertEqual(0, self.run_sh("add", self.keys[-3]).returncode)
        self.assertEqual(before, self.lines())

    def test_has_finds_the_oldest_kept_and_the_newest(self):
        self.assertEqual(0, self.run_sh("has", self.keys[-5000]).returncode)
        self.assertEqual(0, self.run_sh("has", self.keys[-1]).returncode)

    def test_has_does_not_find_a_dropped_or_never_added_key(self):
        self.assertNotEqual(0, self.run_sh("has", self.keys[0]).returncode)
        self.assertNotEqual(0, self.run_sh("has", "f" * 40).returncode)

    def test_has_without_a_list_fails(self):
        with tempfile.TemporaryDirectory() as d:
            subprocess.run(["git", "init", "-q", d], check=True)
            r = subprocess.run([str(SCRIPT), "has", "a" * 40], cwd=d, env=self.env)
            self.assertNotEqual(0, r.returncode)


if __name__ == "__main__":
    unittest.main()
