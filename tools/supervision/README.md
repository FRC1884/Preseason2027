# Supervision: collector, shipper, protected-account install

Answers three mentor questions without reading student conversations:

| Question | Source | Where it shows |
|---|---|---|
| Are they working? | GitHub webhook (pushes, PRs) | overview page |
| Are they learning? | harness learning events, shipped in batches | overview, daily digest, per-student page |
| Are they using it appropriately? | harness hooks: denials, blocked writes, bypass attempts | overview; hooks enforce on the laptop in real time |

Event text written by students (requests, quiz answers) is redacted twice: by the
shipper before sending and by the collector on receipt. Only counts, types and
pass/fail reach the server.

## Pieces

| Path | Runs where | Account |
|---|---|---|
| `server/collector.py` | VPS or school machine, behind Caddy or Tailscale | service user |
| `agent/ship.py` | every laptop, as a system service | root / SYSTEM |
| `admin/frc1884_hook.py` | every laptop, called by Claude Code and Codex on every action | the student, reading admin-owned files |
| `admin/managed-settings.claude.json` | Claude Code admin settings | installed by admin |
| `admin/requirements.codex.toml` | Codex admin requirements | installed by admin |
| `admin/install-admin-macos.sh`, `admin/install-admin-windows.ps1` | one-time per laptop | protected admin account |

## Server

```bash
python3 tools/supervision/server/collector.py serve --config /etc/frc1884/collector.json
```

Config (JSON): `db_path`, `bind`, `port`, `mentor_password`, `github_webhook_secret`,
`github_org`, `mentor_team`, `retention_days` (default 180), `repository_filter`.
Pages need HTTP basic auth, user `mentor`. `/healthz` is public.

GitHub webhook: repository → Settings → Webhooks → payload URL `https://host/api/github`,
content type JSON, secret = `github_webhook_secret`, events: pushes and pull requests.

Caddyfile example:

```
supervision.example.org {
    reverse_proxy 127.0.0.1:8787
}
```

## Laptop (protected admin account, once)

```bash
sudo COLLECTOR_URL=https://supervision.example.org bash tools/supervision/admin/install-admin-macos.sh
```

```powershell
$env:COLLECTOR_URL = "https://supervision.example.org"; .\tools\supervision\admin\install-admin-windows.ps1
```

Enrolment uses the mentor's own `gh` login; the collector checks active
membership of the mentors team and issues a laptop token that only the admin
account can read. Students must be Standard (non-admin) accounts.

Verify from the student account: `python3 tools/laptop/doctor.py --supervised`.

## What the admin files enforce

- Claude Code: `allowManagedHooksOnly`, bypass-permissions mode disabled, deny
  `sudo` and force pushes, 30-day local transcript retention, harness hooks on
  SessionStart / UserPromptSubmit / PreToolUse.
- Codex: `allow_managed_hooks_only`, approval policies limited to `untrusted` and
  `on-request`, no full-access sandbox, `sudo` and force pushes forbidden, same hooks.
- Hook wrapper: outside an approved FRC1884 checkout every tool call is denied and
  the agent is told why. If the harness hook crashes, tool calls are denied (fail closed).

## Known limits

- A student with a home computer can clone the repo and work unsupervised. The
  webhook flags their pushes as "no harness session".
- The shipper reads the GitHub login from each student's `gh` config for
  attribution; a student who never logs in to `gh` shows as `unknown`.
- Windows scripts are written from the macOS versions and have not run on Windows.
- Python must be installed for all users (python.org installer or winget machine
  scope). A Python in a user folder could be swapped by that user.
