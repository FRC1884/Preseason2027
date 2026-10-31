"""Offline adapter smoke tests; fake gates are not real authorization evidence."""

from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
RUNTIME = ROOT / "tools/robotics_harness/runtime"
sys.path.insert(0, str(RUNTIME if RUNTIME.is_dir() else ROOT))

from harness.policy.session_cli import _assert_human_input  # noqa: E402
from harness.policy.session_hooks import authorize_tool, is_explicit_agreement  # noqa: E402
from harness.risk.classifier import RiskClassifier  # noqa: E402
from harness.synchronization.provider_bundle import check_bundle  # noqa: E402


class AdapterSmokeTests(unittest.TestCase):
    def test_author_and_review_entry_points_import(self) -> None:
        launcher = ROOT / "tools/robotics_harness/entry.py"
        for route in ("author", "review"):
            result = subprocess.run(
                [sys.executable, str(launcher), route, "--help"],
                cwd=launcher.parent,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_bundle_integrity(self) -> None:
        self.assertEqual(check_bundle(ROOT), [])

    def test_unapproved_edit_captures_attempt(self) -> None:
        attempted: list[tuple[str, str | None]] = []

        def simulated_locked_gate(action: str, path: str | None) -> None:
            attempted.append((action, path))
            raise ValueError("SIMULATED: no human plan approval")

        result = authorize_tool(
            {"tool_name": "Write", "tool_input": {"file_path": str(ROOT / "fixture.txt")}},
            root=ROOT,
            gate=simulated_locked_gate,
        )
        self.assertFalse(result.allowed)
        self.assertEqual(attempted, [("implement", "fixture.txt")])
        self.assertIn("SIMULATED", result.reason)

    def test_injection_and_publication_shell_never_execute(self) -> None:
        for command in ("cat README.md; gh api secrets", "git push", "gh pr merge 1"):
            result = authorize_tool(
                {"tool_name": "Bash", "tool_input": {"command": command}},
                root=ROOT,
                gate=lambda action, path: None,
            )
            self.assertFalse(result.allowed, command)

    def test_packaged_risk_rules_load(self) -> None:
        self.assertIsNotNone(RiskClassifier().classify([]))

    def test_student_agreement_words(self) -> None:
        # Failure this guards: a student says "ok" and the plan bounces back (seen in audit).
        for text in ("yes", "ok", "yep", "sure", "yes please", "sounds good", "go for it", "Yes, that works, thanks!"):
            self.assertTrue(is_explicit_agreement(text), text)
        for text in ("no", "yes but rename it first", "ok so what about the gyro", "", "y" * 61):
            self.assertFalse(is_explicit_agreement(text), text)

    def test_web_fetch_limited_to_robotics_docs(self) -> None:
        # Failure this guards: any URL becomes fetchable from a school laptop.
        def fetch(url: str) -> bool:
            return authorize_tool(
                {"tool_name": "WebFetch", "tool_input": {"url": url}},
                root=ROOT,
                gate=lambda action, path: None,
            ).allowed

        self.assertTrue(fetch("https://docs.wpilib.org/en/stable/"))
        self.assertTrue(fetch("https://github.com/wpilibsuite/allwpilib"))
        self.assertFalse(fetch("https://github.com/someone-else/repo"))
        self.assertFalse(fetch("https://pastebin.com/x"))
        self.assertFalse(fetch("http://docs.wpilib.org/"))

    def test_learning_answers_may_span_recent_messages(self) -> None:
        # Failure this guards: a quiz answered across two messages is refused (seen in audit).
        import hashlib

        def captured(text: str, ref: str) -> dict[str, str]:
            return {"digest": hashlib.sha256(text.encode()).hexdigest(), "reference": ref}

        first, second = "The gyro resets heading to zero when Y is pressed.", "I ran it in simulation and the heading read 0."
        context = {"interactions": [captured(first, "p1"), captured(second, "p2")], "last_interaction": captured(second, "p2")}
        data = {"interaction_texts": [first, second], "answers": ["resets heading to zero"], "verification_response": "ran it in simulation"}
        self.assertTrue(_assert_human_input(data, context).startswith("human-interactions:"))
        with self.assertRaises(ValueError):
            _assert_human_input({**data, "interaction_texts": [first, "never typed"]}, context)


if __name__ == "__main__":
    unittest.main()
