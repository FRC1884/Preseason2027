#!/usr/bin/env python3
"""Laptop readiness check for the FRC 1884 Season2027 harness.

Standard library only. Works on macOS, Windows and Linux. Prints a PASS/FAIL
table and, with --json, a machine-readable report that mentors can collect.

Usage (from anywhere):
    python3 tools/laptop/doctor.py [--json] [--repo PATH]
"""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

EXPECTED_ORIGIN = re.compile(r"github\.com[:/]FRC1884/(Season2027|Preseason2027)(\.git)?/?$")
MIN_PYTHON = (3, 12)
JAVA_MAJOR = 17
GRADLE_VERSION = "8.11"
WPILIB_YEAR = "2026"


@dataclass
class Check:
    name: str
    ok: bool
    detail: str
    fix: str = ""


def run(*command: str, cwd: Path | None = None, timeout: int = 20) -> tuple[int, str]:
    try:
        completed = subprocess.run(
            list(command),
            cwd=cwd,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError) as error:
        return 127, str(error)
    return completed.returncode, (completed.stdout + completed.stderr).strip()


def which(name: str) -> str | None:
    return shutil.which(name) or shutil.which(f"{name}.cmd") or shutil.which(f"{name}.exe")


def check_python() -> Check:
    version = sys.version_info[:3]
    ok = version >= MIN_PYTHON
    return Check(
        "Python",
        ok,
        f"{platform.python_implementation()} {'.'.join(map(str, version))} at {sys.executable}",
        "" if ok else f"Install Python {MIN_PYTHON[0]}.{MIN_PYTHON[1]} or newer (the harness hooks need it).",
    )


def check_java() -> Check:
    code, out = run("java", "-version")
    match = re.search(r'version "(\d+)', out)
    major = int(match.group(1)) if match else None
    ok = code == 0 and major == JAVA_MAJOR
    return Check(
        f"Java {JAVA_MAJOR}",
        ok,
        out.splitlines()[0] if out else "java not found",
        "" if ok else f"Install Temurin JDK {JAVA_MAJOR} (the robot build and CI use exactly Java {JAVA_MAJOR}).",
    )


def check_gradle(repo: Path | None) -> Check:
    if repo and (repo / ("gradlew.bat" if os.name == "nt" else "gradlew")).exists():
        return Check("Gradle", True, "Gradle wrapper present in repository", "")
    code, out = run("gradle", "--version")
    match = re.search(r"Gradle (\d+\.\d+(\.\d+)?)", out)
    found = match.group(1) if match else None
    ok = code == 0 and found == GRADLE_VERSION
    return Check(
        f"Gradle {GRADLE_VERSION}",
        ok,
        f"gradle {found}" if found else "gradle not found and no gradlew in repository",
        "" if ok else f"Install Gradle {GRADLE_VERSION}, or add the Gradle wrapper to the repository so no separate install is needed.",
    )


def check_wpilib() -> Check:
    home = Path.home()
    candidates = [home / "wpilib" / WPILIB_YEAR, Path("C:/Users/Public/wpilib") / WPILIB_YEAR]
    found = next((c for c in candidates if c.exists()), None)
    return Check(
        f"WPILib {WPILIB_YEAR}",
        found is not None,
        str(found) if found else "not installed",
        "" if found else f"Run the WPILib {WPILIB_YEAR} installer (needed to deploy to the roboRIO).",
    )


def check_tool(name: str, label: str, fix: str, version_flag: str = "--version") -> Check:
    path = which(name)
    if not path:
        return Check(label, False, f"{name} not found on PATH", fix)
    _, out = run(name, version_flag)
    return Check(label, True, out.splitlines()[0] if out else path, "")


def check_gh_auth() -> Check:
    if not which("gh"):
        return Check("GitHub login", False, "gh not installed", "Install GitHub CLI, then run: gh auth login")
    code, out = run("gh", "api", "user", "--jq", ".login")
    ok = code == 0 and out and not out.startswith("gh:")
    return Check(
        "GitHub login",
        bool(ok),
        f"logged in as {out}" if ok else "not logged in",
        "" if ok else "Run: gh auth login   (use the student's own GitHub account)",
    )


def check_repo(repo: Path | None) -> list[Check]:
    if repo is None or not (repo / ".git").exists():
        return [Check("Repository", False, "no Season2027 checkout found", "Clone https://github.com/FRC1884/Preseason2027 and rerun with --repo PATH")]
    checks: list[Check] = []
    _, origin = run("git", "remote", "get-url", "origin", cwd=repo)
    ok = bool(EXPECTED_ORIGIN.search(origin))
    checks.append(Check("Repository origin", ok, origin or "no origin", "" if ok else "Origin must be the FRC1884 Season2027 repository."))

    _, name = run("git", "config", "--get", "user.name", cwd=repo)
    _, email = run("git", "config", "--get", "user.email", cwd=repo)
    ok = bool(name and email)
    checks.append(Check("Git identity", ok, f"{name} <{email}>" if ok else "name or email missing",
                        "" if ok else "Run: git config user.name 'First Last' && git config user.email 'you@example.com'"))

    for rel in (".claude/settings.json", ".codex/hooks.json", "tools/robotics_harness/entry.py", "AGENTS.md"):
        exists = (repo / rel).exists()
        checks.append(Check(f"Harness file {rel}", exists, "present" if exists else "missing",
                            "" if exists else "Checkout is incomplete or on the wrong branch; run git pull."))

    code, out = run(sys.executable, str(repo / "tools/robotics_harness/entry.py"), "check", cwd=repo, timeout=60)
    ok = code == 0
    checks.append(Check("Harness integrity (entry.py check)", ok, out.splitlines()[-1] if out else "no output",
                        "" if ok else "Harness bundle failed its own integrity check; reclone or ask a mentor."))

    hook_input = json.dumps({"session_id": "doctor", "hook_event_name": "PreToolUse",
                             "tool_name": "Bash", "tool_input": {"command": "git commit -m doctor"}})
    try:
        completed = subprocess.run(
            [sys.executable, str(repo / "tools/robotics_harness/entry.py"), "hook", "--provider", "claude",
             "--root", str(repo), "--hook-event", "PreToolUse"],
            input=hook_input, capture_output=True, text=True, timeout=30, cwd=repo, check=False,
        )
        denied = '"deny"' in completed.stdout
    except (OSError, subprocess.TimeoutExpired):
        denied = False
    checks.append(Check("Harness blocks raw git commit", denied, "hook returned deny" if denied else "hook did not deny",
                        "" if denied else "The commit guard is not working on this laptop; do not let students use it until fixed."))
    return checks


def check_supervision() -> list[Check]:
    """Protected-account install: managed settings, admin-owned harness, shipper service."""
    if os.name == "nt":
        claude_managed = Path("C:/Program Files/ClaudeCode/managed-settings.json")
        codex_req = Path(os.environ.get("ProgramData", "C:/ProgramData")) / "OpenAI/Codex/requirements.toml"
        prefix = Path("C:/Program Files/FRC1884")
        shipper_cfg = Path(os.environ.get("ProgramData", "C:/ProgramData")) / "FRC1884/shipper.json"
    else:
        claude_managed = Path("/Library/Application Support/ClaudeCode/managed-settings.json")
        codex_req = Path("/etc/codex/requirements.toml")
        prefix = Path("/opt/frc1884")
        shipper_cfg = Path("/etc/frc1884/shipper.json")
    checks: list[Check] = []
    for label, path in (("Claude Code managed settings", claude_managed), ("Codex managed requirements", codex_req),
                        ("Admin-owned harness copy", prefix / "tools/robotics_harness/entry.py"),
                        ("Admin-owned hook wrapper", prefix / "bin/frc1884_hook.py")):
        exists = path.exists()
        checks.append(Check(label, exists, str(path) if exists else f"missing: {path}",
                            "" if exists else "Run the admin install (tools/supervision/admin) from the protected account."))
    if claude_managed.exists():
        try:
            data = json.loads(claude_managed.read_text(encoding="utf-8-sig"))
            ok = bool(data.get("allowManagedHooksOnly")) and bool(data.get("hooks", {}).get("PreToolUse"))
            checks.append(Check("Managed hooks locked", ok, "allowManagedHooksOnly + PreToolUse hook" if ok else "managed settings lack the lock or the hook",
                                "" if ok else "Reinstall managed settings from the template."))
        except (OSError, json.JSONDecodeError):
            checks.append(Check("Managed hooks locked", False, "managed-settings.json unreadable", "Reinstall managed settings."))
    try:
        if os.name == "nt":
            code, out = run("schtasks", "/Query", "/TN", "FRC1884 Shipper", "/FO", "LIST")
            running = code == 0 and "Running" in out
        else:
            code, out = run("launchctl", "print", "system/org.frc1884.shipper")
            running = code == 0 and "state = running" in out
    except Exception:  # noqa: BLE001 - diagnostic only
        running = False
    checks.append(Check("Shipper service running", running, "running" if running else "not running",
                        "" if running else "Start it from the admin account (launchctl / Task Scheduler) or rerun the admin install."))
    try:
        readable = shipper_cfg.exists() and os.access(shipper_cfg, os.R_OK)
    except OSError:
        readable = False
    checks.append(Check("Shipper token hidden from this user", not readable,
                        "config not readable by this account" if not readable else f"{shipper_cfg} is readable",
                        "" if not readable else "Tighten permissions: the collector token must be admin-only."))
    if os.name == "nt":
        code, out = run("net", "session")
        is_admin = code == 0
    else:
        code, out = run("id", "-Gn")
        is_admin = "admin" in out.split()
    checks.append(Check("This account is a standard user", not is_admin, "not an administrator" if not is_admin else "ADMINISTRATOR",
                        "" if not is_admin else "Students must use a Standard account; this one can edit the protected files."))
    return checks


def find_repo(explicit: str | None) -> Path | None:
    if explicit:
        return Path(explicit).expanduser().resolve()
    here = Path(__file__).resolve()
    for parent in here.parents:
        if (parent / "AGENTS.md").exists() and (parent / ".git").exists():
            return parent
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--json", action="store_true", help="print a JSON report instead of a table")
    parser.add_argument("--repo", help="path to the Season2027 checkout (default: the one containing this script)")
    parser.add_argument("--supervised", action="store_true",
                        help="also check the protected-account install (managed settings, shipper service, standard user)")
    args = parser.parse_args()

    repo = find_repo(args.repo)
    checks: list[Check] = [
        check_python(),
        check_java(),
        check_gradle(repo),
        check_wpilib(),
        check_tool("git", "Git", "Install Git."),
        check_tool("gh", "GitHub CLI", "Install GitHub CLI (gh)."),
        check_gh_auth(),
        check_tool("claude", "Claude Code", "Install Claude Code, then run: claude   (log in)"),
        check_tool("codex", "Codex CLI", "Install Codex CLI, then run: codex login"),
        *check_repo(repo),
        *(check_supervision() if args.supervised else []),
    ]

    report = {
        "host": platform.node(),
        "os": f"{platform.system()} {platform.release()} {platform.machine()}",
        "repo": str(repo) if repo else None,
        "all_ok": all(c.ok for c in checks),
        "checks": [asdict(c) for c in checks],
    }
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        width = max(len(c.name) for c in checks)
        print(f"FRC 1884 laptop doctor — {report['host']} — {report['os']}")
        print(f"Repository: {report['repo'] or 'not found'}\n")
        for c in checks:
            print(f"{'PASS' if c.ok else 'FAIL'}  {c.name.ljust(width)}  {c.detail}")
            if not c.ok and c.fix:
                print(f"      -> {c.fix}")
        failed = [c for c in checks if not c.ok]
        print(f"\n{'READY' if not failed else f'{len(failed)} problem(s) to fix'}")
    return 0 if report["all_ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
