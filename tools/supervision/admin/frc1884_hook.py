#!/usr/bin/env python3
"""Machine-wide hook wrapper installed under the protected admin account.

Managed settings for Claude Code and Codex call this instead of the repository's
own hook command. It decides where the student is working, then hands the hook
to the admin-owned harness copy:

- inside an approved FRC1884 checkout: run the real harness hook from PREFIX;
- anywhere else: SessionStart and UserPromptSubmit explain the restriction and
  PreToolUse denies every tool call, so the agent cannot do unrelated work on a
  school laptop.

    python3 frc1884_hook.py --provider claude --hook-event PreToolUse

PREFIX defaults to the parent of this file's directory (…/frc1884). The approved
repository list lives in PREFIX/etc/approved_repos.txt, one "owner/name" per line.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

PREFIX = Path(os.environ.get("FRC1884_PREFIX") or Path(__file__).resolve().parents[1])
ENTRY = PREFIX / "tools" / "robotics_harness" / "entry.py"
APPROVED = PREFIX / "etc" / "approved_repos.txt"
REMOTE = re.compile(r"(?:https://github\.com/|git@github\.com:)([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?/?$")


def approved_repositories() -> set[str]:
    try:
        return {line.strip().lower() for line in APPROVED.read_text().splitlines() if line.strip() and not line.startswith("#")}
    except OSError:
        return {"frc1884/season2027", "frc1884/preseason2027"}


def repository_root(cwd: Path) -> tuple[Path | None, str]:
    try:
        root = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=cwd, capture_output=True, text=True, timeout=5, check=False).stdout.strip()
        if not root:
            return None, ""
        url = subprocess.run(["git", "remote", "get-url", "origin"], cwd=root, capture_output=True, text=True, timeout=5, check=False).stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return None, ""
    match = REMOTE.match(url)
    return Path(root), (match.group(1).lower() if match else "")


def outside_response(event: str, reason: str) -> dict:
    if event == "PreToolUse":
        return {"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny", "permissionDecisionReason": reason}}
    if event in {"SessionStart", "UserPromptSubmit"}:
        return {"hookSpecificOutput": {"hookEventName": event, "additionalContext": reason}}
    return {}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--provider", required=True, choices=["claude", "codex"])
    parser.add_argument("--hook-event", required=True)
    args = parser.parse_args()

    raw = sys.stdin.read()
    try:
        payload = json.loads(raw or "{}")
    except json.JSONDecodeError:
        payload = {}
    cwd = Path(str(payload.get("cwd") or os.getcwd()))
    root, repository = repository_root(cwd)

    if root is None or repository not in approved_repositories():
        where = repository or str(cwd)
        reason = ("This laptop only allows AI-assisted work inside the FRC 1884 robot repository. "
                  f"Current location: {where}. Open a terminal in the robot checkout and start again.")
        print(json.dumps(outside_response(args.hook_event, reason)))
        return 0

    if not ENTRY.exists():
        print(json.dumps(outside_response(args.hook_event, f"Harness missing at {ENTRY}; tell a mentor. Tool calls are blocked until it is reinstalled.")))
        return 0

    completed = subprocess.run(
        [sys.executable, str(ENTRY), "hook", "--provider", args.provider, "--root", str(root), "--hook-event", args.hook_event],
        input=raw, capture_output=True, text=True, timeout=25, check=False,
    )
    sys.stdout.write(completed.stdout)
    sys.stderr.write(completed.stderr)
    if completed.returncode != 0 and args.hook_event == "PreToolUse":
        # The harness hook crashed: fail closed instead of letting the tool run.
        print(json.dumps(outside_response("PreToolUse", "Harness hook failed; tool call blocked. Tell a mentor.")))
        return 0
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
