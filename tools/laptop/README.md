# Student laptop setup

One script installs everything a student laptop needs for the AI harness, then
a doctor confirms it. A mentor should be present for the login steps.

## What a laptop needs

| Tool | Version | Why |
|---|---|---|
| Python | 3.12+ | runs the harness hooks on every agent action |
| Temurin JDK | 17 | robot build; CI uses exactly 17 |
| Gradle | 8.11 | robot build; the repo's `gradlew` wrapper fetches it, no separate install |
| Git, GitHub CLI | current | task branches, PRs, mentor identity checks |
| Claude Code and/or Codex CLI | current | the supervised agent |
| WPILib | 2026 | only to deploy to the roboRIO |

## Steps

1. **macOS**: `bash tools/laptop/bootstrap-macos.sh`
   **Windows**: `Set-ExecutionPolicy -Scope Process Bypass; .\tools\laptop\bootstrap-windows.ps1`
   (or fetch the script from GitHub on a laptop with no checkout yet).
2. Log in with the student watching: `gh auth login`, then `claude`, then `codex login`.
3. Set Git identity in the checkout:
   `git config user.name "First Last"` and `git config user.email "student@example.com"`.
4. Run `python3 tools/laptop/doctor.py` until it prints `READY`.
5. Install WPILib 2026 from the official installer if the laptop will deploy to the robot.

`doctor.py --json` prints the same result as JSON for a mentor to collect.

## What the doctor proves

- the toolchain versions match CI;
- the checkout points at the FRC1884 repository with a Git identity set;
- the harness bundle passes its own integrity check;
- the harness hook really denies a raw `git commit` on this laptop.

It does not prove which Claude or Codex account is logged in, and it cannot stop
a student from editing the hook files. GitHub rulesets remain the hard control.
