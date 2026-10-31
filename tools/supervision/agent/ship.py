#!/usr/bin/env python3
"""FRC 1884 laptop shipper.

Runs as a system service under the protected admin account. Every cycle it
reads new harness events from every student home on the laptop, redacts text
fields, posts them to the collector, and sends a heartbeat with fingerprints of
the protected harness files. Standard library only.

    python3 ship.py --config /etc/frc1884/shipper.json            # loop forever
    python3 ship.py --config ... --once                           # one cycle, then exit
    python3 ship.py enroll --server https://... --laptop-name X   # mentor, uses gh token

Config keys (JSON): server, token, laptop_id, interval_seconds, heartbeat_seconds,
state_dir, homes_glob (list), fingerprint_paths (list), version_commands (object).
"""

from __future__ import annotations

import argparse
import getpass
import glob
import hashlib
import json
import os
import platform
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

EVENTS_SUBPATH = ".local/state/frc1884-harness"
TEXT_BEARING_EVENTS = {
    "student_request_submitted",
    "student_answer_recorded",
    "clarification_answer_recorded",
    "clarification_question_asked",
    "post_implementation_question_asked",
    "prompt_recorded",
}
DEFAULT_HOMES = ["C:/Users/*"] if os.name == "nt" else ["/Users/*", "/home/*"]
BATCH = 500


def log(message: str) -> None:
    sys.stderr.write(f"{time.strftime('%Y-%m-%dT%H:%M:%S')} {message}\n")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(65536), b""):
                digest.update(chunk)
    except OSError:
        return "missing"
    return digest.hexdigest()


def post(server: str, path: str, token: str, payload: dict[str, Any]) -> dict[str, Any]:
    request = urllib.request.Request(
        server.rstrip("/") + path,
        data=json.dumps(payload).encode(),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json",
                 "User-Agent": "frc1884-shipper"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read() or b"{}")


# --- identity of the student who owns a home directory ------------------------

def github_login_for_home(home: Path) -> str:
    """Read the GitHub CLI login from the student's gh config. Attribution only."""
    candidates = [home / ".config/gh/hosts.yml", home / "AppData/Roaming/GitHub CLI/hosts.yml"]
    for path in candidates:
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        for line in text.splitlines():
            stripped = line.strip()
            if stripped.startswith("user:"):
                return stripped.split(":", 1)[1].strip().strip("'\"")
    return ""


def redact(event: dict[str, Any]) -> dict[str, Any]:
    result = event.get("result")
    if event.get("event_type") in TEXT_BEARING_EVENTS and isinstance(result, str) and result:
        event = dict(event)
        event["result"] = f"[redacted {len(result)} chars]"
    event.pop("metadata", None)  # free-form; never ship it
    return event


# --- cursor state --------------------------------------------------------------

class Cursors:
    def __init__(self, state_dir: Path) -> None:
        self.path = state_dir / "cursors.json"
        try:
            self.data: dict[str, int] = json.loads(self.path.read_text())
        except (OSError, json.JSONDecodeError):
            self.data = {}

    def get(self, key: str) -> int:
        return int(self.data.get(key, 0))

    def set(self, key: str, offset: int) -> None:
        self.data[key] = offset
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_suffix(".tmp")
        tmp.write_text(json.dumps(self.data, indent=0, sort_keys=True))
        os.replace(tmp, self.path)


def event_files(homes_glob: list[str]) -> list[tuple[Path, Path]]:
    found: list[tuple[Path, Path]] = []
    for pattern in homes_glob:
        for home_str in glob.glob(pattern):
            home = Path(home_str)
            root = home / EVENTS_SUBPATH
            if not root.is_dir():
                continue
            for events in root.rglob("events.jsonl"):
                found.append((home, events))
    return found


def ship_events(config: dict[str, Any], cursors: Cursors) -> int:
    shipped = 0
    for home, path in event_files(config.get("homes_glob") or DEFAULT_HOMES):
        key = str(path)
        offset = cursors.get(key)
        try:
            size = path.stat().st_size
        except OSError:
            continue
        if size < offset:  # file truncated or replaced: start over, note it
            log(f"events file shrank, restarting cursor: {path}")
            offset = 0
        if size == offset:
            continue
        login = github_login_for_home(home) or "unknown"
        os_user = home.name
        with path.open("rb") as stream:
            stream.seek(offset)
            while True:
                batch: list[dict[str, Any]] = []
                consumed = offset
                for raw in stream:
                    consumed += len(raw)
                    if not raw.endswith(b"\n"):
                        consumed -= len(raw)  # partial last line, wait for the writer
                        break
                    try:
                        event = json.loads(raw)
                    except json.JSONDecodeError:
                        continue
                    if isinstance(event, dict):
                        event = redact(event)
                        event["github_login"] = login
                        event["os_user"] = os_user
                        batch.append(event)
                    if len(batch) >= BATCH:
                        break
                if not batch:
                    cursors.set(key, consumed)
                    break
                try:
                    reply = post(config["server"], "/api/events", config["token"], {"laptop_id": config["laptop_id"], "events": batch})
                except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
                    log(f"post failed, will retry: {error}")
                    return shipped
                shipped += int(reply.get("stored", 0))
                offset = consumed
                cursors.set(key, offset)
                if len(batch) < BATCH:
                    break
    return shipped


def heartbeat(config: dict[str, Any]) -> None:
    fingerprints = {p: sha256_file(Path(p)) for p in config.get("fingerprint_paths") or []}
    versions: dict[str, str] = {"os": f"{platform.system()} {platform.release()}", "python": platform.python_version()}
    for name, command in (config.get("version_commands") or {}).items():
        try:
            out = subprocess.run(command, capture_output=True, text=True, timeout=15, check=False).stdout.strip()
            versions[name] = out.splitlines()[0] if out else "not found"
        except (OSError, subprocess.TimeoutExpired):
            versions[name] = "not found"
    try:
        post(config["server"], "/api/heartbeat", config["token"], {"laptop_id": config["laptop_id"], "fingerprints": fingerprints, "versions": versions})
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
        log(f"heartbeat failed: {error}")


def run(config: dict[str, Any], once: bool) -> int:
    state_dir = Path(config.get("state_dir") or ("C:/ProgramData/FRC1884/shipper" if os.name == "nt" else "/var/lib/frc1884/shipper"))
    cursors = Cursors(state_dir)
    interval = int(config.get("interval_seconds", 30))
    hb_every = int(config.get("heartbeat_seconds", 300))
    last_hb = 0.0
    while True:
        shipped = ship_events(config, cursors)
        if shipped:
            log(f"shipped {shipped} events")
        if time.time() - last_hb >= hb_every:
            heartbeat(config)
            last_hb = time.time()
        if once:
            return 0
        time.sleep(interval)


def enroll(server: str, laptop_name: str, config_path: Path) -> int:
    """Mentor-run: prove GitHub mentor identity via gh, receive a laptop token, write the config."""
    try:
        gh_token = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, check=True, timeout=15).stdout.strip()
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        log("gh auth token failed; log in with gh as a mentor first")
        return 1
    try:
        reply = post(server, "/api/enroll", gh_token, {"laptop_name": laptop_name})
    except urllib.error.HTTPError as error:
        log(f"enrolment refused: {error.code} {error.read().decode(errors='replace')}")
        return 1
    except (urllib.error.URLError, TimeoutError) as error:
        log(f"cannot reach collector: {error}")
        return 1
    existing: dict[str, Any] = {}
    if config_path.exists():
        try:
            existing = json.loads(config_path.read_text())
        except json.JSONDecodeError:
            existing = {}
    existing.update({"server": server, "token": reply["token"], "laptop_id": reply["laptop_id"], "laptop_name": laptop_name})
    config_path.parent.mkdir(parents=True, exist_ok=True)
    config_path.write_text(json.dumps(existing, indent=2))
    try:
        os.chmod(config_path, 0o600)
    except OSError:
        pass
    log(f"enrolled laptop {reply['laptop_id']} as {reply['enrolled_by']}; config at {config_path}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", nargs="?", default="run", choices=["run", "enroll"])
    parser.add_argument("--config", default="C:/ProgramData/FRC1884/shipper.json" if os.name == "nt" else "/etc/frc1884/shipper.json")
    parser.add_argument("--once", action="store_true")
    parser.add_argument("--server")
    parser.add_argument("--laptop-name", default=platform.node())
    args = parser.parse_args()
    if args.command == "enroll":
        if not args.server:
            parser.error("--server is required for enroll")
        return enroll(args.server, args.laptop_name, Path(args.config))
    try:
        config = json.loads(Path(args.config).read_text())
    except (OSError, json.JSONDecodeError) as error:
        log(f"cannot read config {args.config}: {error}")
        return 1
    for key in ("server", "token", "laptop_id"):
        if not config.get(key):
            log(f"config missing {key}; run enroll first")
            return 1
    if getpass.getuser() not in {"root", "SYSTEM"} and not config.get("allow_unprivileged"):
        log("warning: not running as the system account; students may be able to stop or edit this process")
    return run(config, args.once)


if __name__ == "__main__":
    sys.exit(main())
