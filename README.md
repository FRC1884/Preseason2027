# FRC 1884 — Season 2027

Season2027 is the clean rollover of Team 1884's Season2026 robot repository with the Robotics Agentic Development Harness built in before student mechanism work begins.

The reusable project/tooling structure has been carried forward and renamed to `frc2027`; completed Season2026 mechanisms, subsystems, mechanism commands, autos, and game-specific field assets are intentionally removed.

> **Temporary toolchain baseline:** WPILib/vendor 2027 releases are not available yet. The repository uses the current 2026-compatible GradleRIO/vendor baseline until an official 2027 infrastructure-upgrade PR replaces it.

## Repository baseline

- mechanism-free `Robot` / `RobotContainer` shell
- reusable Season2026 OI and generic utility patterns under `org.Griffins1884.frc2027`
- Team 1884 WPILib / Gradle tooling baseline
- harness policy files under `.github/robotics-harness/`; the harness code lives in `FRC1884/robotics-agentic-development-harness`
- shared `AGENTS.md` with thin Codex and Claude Code entry points
- one-page `AGENTS.md` with the six-step loop, risk levels and hard lines
- PR template with risk, safety, learning, testing, and rollback evidence
- CODEOWNERS for mentors, Software Lead, three sub-leads, and approved alumni
- CI job `build-and-format`
- safety-critical constants isolated under protected paths

See [`docs/SEASON_ROLLOVER.md`](docs/SEASON_ROLLOVER.md) for the season rollover.

## Branch model

```text
task/*
  -> vision-localisation-lead | core-mechanisms-lead | autonomous-lead
  -> software-leads
  -> main
```

The five long-lived integration branches are:

- `vision-localisation-lead`
- `core-mechanisms-lead`
- `autonomous-lead`
- `software-leads`
- `main`

The desired hosted protection policy is documented in [`docs/BRANCH_PROTECTION_SETUP.md`](docs/BRANCH_PROTECTION_SETUP.md).

## Student and reviewer entry points

Open Codex or Claude Code in this repository and describe the outcome you want.
The agent follows the six-step loop in [`AGENTS.md`](AGENTS.md): look, plan, agree,
build, learn, ship. Edits are locked until you agree to a plan; commits are locked
until you answer a few questions about the change in your own words. The hooks
tell the agent where it is on every prompt.

To review a PR, open a fresh session and ask `review PR #123`. The reviewer
checks your `gh` identity, agrees a short plan, reads the full diff, shows the
report, and posts it as a COMMENT review only after you approve the exact text.

Mentors may merge their own PR once CI is green. Nobody else merges their own
work, and the agent never merges.

Laptop setup and mentor supervision: see the harness repository
`FRC1884/robotics-agentic-development-harness`.
