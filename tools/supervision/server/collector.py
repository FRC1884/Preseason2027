#!/usr/bin/env python3
"""FRC 1884 supervision collector.

One process, standard library only, SQLite on disk. Receives harness events and
heartbeats from student laptops, GitHub webhooks for the robot repository, and
serves a mentor page plus daily digests. Put it behind Caddy (or Tailscale) for
HTTPS; it speaks plain HTTP on a local port.

    python3 collector.py serve --config /etc/frc1884/collector.json
    python3 collector.py mint-token --config ... --laptop-name "comp-laptop-3"
    python3 collector.py prune --config ...

Config keys (JSON): db_path, bind, port, mentor_password, github_webhook_secret,
github_org, mentor_team, retention_days, repository_filter (optional "owner/name").
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import html
import json
import secrets
import sqlite3
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import UTC, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

DEFAULTS: dict[str, Any] = {
    "db_path": "collector.sqlite3",
    "bind": "127.0.0.1",
    "port": 8787,
    "mentor_password": "",
    "github_webhook_secret": "",
    "github_org": "FRC1884",
    "mentor_team": "mentors",
    "retention_days": 180,
    "repository_filter": "",
    "heartbeat_stale_minutes": 30,
}

# Event types whose `result` field carries free text a student wrote. The shipper
# already redacts these; the collector redacts again so a mis-configured shipper
# cannot leak content.
TEXT_BEARING_EVENTS = {
    "student_request_submitted",
    "student_answer_recorded",
    "clarification_answer_recorded",
    "clarification_question_asked",
    "post_implementation_question_asked",
    "prompt_recorded",
}
LEARNING_PASS = {"learning_review_completed", "learning_loop_completed"}
LEARNING_FAIL = {"learning_review_failed"}
MISUSE = {"scope_check_denied", "write_blocked", "policy_violation", "competition_scope_denied"}

SCHEMA = """
CREATE TABLE IF NOT EXISTS laptops (
  laptop_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  enrolled_by TEXT NOT NULL,
  enrolled_at TEXT NOT NULL,
  last_heartbeat TEXT,
  baseline_fingerprints TEXT,
  last_fingerprints TEXT,
  last_versions TEXT
);
CREATE TABLE IF NOT EXISTS events (
  event_id TEXT PRIMARY KEY,
  laptop_id TEXT NOT NULL,
  github_login TEXT NOT NULL,
  os_user TEXT NOT NULL,
  received_at TEXT NOT NULL,
  timestamp TEXT NOT NULL,
  session_id TEXT NOT NULL,
  event_type TEXT NOT NULL,
  result TEXT NOT NULL,
  policy_status TEXT NOT NULL,
  risk_level TEXT NOT NULL,
  repository TEXT NOT NULL,
  branch TEXT NOT NULL,
  task_identifier TEXT NOT NULL,
  tool_or_command TEXT NOT NULL,
  commit_or_pr_reference TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS events_login_time ON events (github_login, timestamp);
CREATE INDEX IF NOT EXISTS events_time ON events (timestamp);
CREATE TABLE IF NOT EXISTS github_events (
  delivery_id TEXT PRIMARY KEY,
  received_at TEXT NOT NULL,
  kind TEXT NOT NULL,
  action TEXT NOT NULL,
  actor_login TEXT NOT NULL,
  repository TEXT NOT NULL,
  ref TEXT NOT NULL,
  sha TEXT NOT NULL,
  pr_number INTEGER,
  summary TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS github_actor_time ON github_events (actor_login, received_at);
"""


def now() -> datetime:
    return datetime.now(UTC)


def iso(moment: datetime) -> str:
    return moment.astimezone(UTC).isoformat(timespec="seconds").replace("+00:00", "Z")


def parse_time(value: str) -> datetime | None:
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(UTC)
    except (ValueError, AttributeError):
        return None


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode()).hexdigest()


class Store:
    def __init__(self, path: str) -> None:
        self.path = path
        self.lock = threading.Lock()
        with self.connect() as db:
            db.executescript(SCHEMA)

    def connect(self) -> sqlite3.Connection:
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        return db

    # --- laptops -----------------------------------------------------------
    def create_laptop(self, name: str, enrolled_by: str) -> tuple[str, str]:
        token = secrets.token_hex(32)
        laptop_id = secrets.token_hex(8)
        with self.lock, self.connect() as db:
            db.execute(
                "INSERT INTO laptops (laptop_id, name, token_hash, enrolled_by, enrolled_at) VALUES (?,?,?,?,?)",
                (laptop_id, name, sha256(token), enrolled_by, iso(now())),
            )
        return laptop_id, token

    def laptop_for_token(self, token: str) -> sqlite3.Row | None:
        with self.connect() as db:
            return db.execute("SELECT * FROM laptops WHERE token_hash = ?", (sha256(token),)).fetchone()

    def heartbeat(self, laptop_id: str, fingerprints: dict[str, str], versions: dict[str, str]) -> None:
        with self.lock, self.connect() as db:
            row = db.execute("SELECT baseline_fingerprints FROM laptops WHERE laptop_id = ?", (laptop_id,)).fetchone()
            baseline = row["baseline_fingerprints"] if row and row["baseline_fingerprints"] else json.dumps(fingerprints, sort_keys=True)
            db.execute(
                "UPDATE laptops SET last_heartbeat = ?, baseline_fingerprints = ?, last_fingerprints = ?, last_versions = ? WHERE laptop_id = ?",
                (iso(now()), baseline, json.dumps(fingerprints, sort_keys=True), json.dumps(versions, sort_keys=True), laptop_id),
            )

    def rebaseline(self, laptop_id: str) -> None:
        with self.lock, self.connect() as db:
            db.execute("UPDATE laptops SET baseline_fingerprints = last_fingerprints WHERE laptop_id = ?", (laptop_id,))

    def laptops(self) -> list[sqlite3.Row]:
        with self.connect() as db:
            return db.execute("SELECT * FROM laptops ORDER BY name").fetchall()

    # --- events ------------------------------------------------------------
    def insert_events(self, laptop_id: str, events: list[dict[str, Any]]) -> int:
        rows = []
        received = iso(now())
        for event in events:
            event_id = str(event.get("event_id") or "").strip()
            timestamp = parse_time(str(event.get("timestamp", "")))
            if not event_id or timestamp is None:
                continue
            event_type = str(event.get("event_type", "unknown"))
            result = str(event.get("result", ""))
            if event_type in TEXT_BEARING_EVENTS and result:
                result = f"[redacted {len(result)} chars]"
            rows.append((
                event_id, laptop_id,
                str(event.get("github_login") or "unknown"),
                str(event.get("os_user") or "unknown"),
                received, iso(timestamp),
                str(event.get("session_id", "")), event_type, result[:500],
                str(event.get("policy_status", "")), str(event.get("risk_level", "")),
                str(event.get("repository", "")), str(event.get("branch", "")),
                str(event.get("task_identifier", "")), str(event.get("tool_or_command", ""))[:200],
                str(event.get("commit_or_pr_reference", "")),
            ))
        with self.lock, self.connect() as db:
            before = db.total_changes
            db.executemany("INSERT OR IGNORE INTO events VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
            return db.total_changes - before

    def insert_github(self, delivery_id: str, kind: str, action: str, actor: str, repository: str,
                      ref: str, sha: str, pr_number: int | None, summary: str) -> None:
        with self.lock, self.connect() as db:
            db.execute(
                "INSERT OR IGNORE INTO github_events VALUES (?,?,?,?,?,?,?,?,?,?)",
                (delivery_id, iso(now()), kind, action, actor, repository, ref, sha, pr_number, summary[:300]),
            )

    def prune(self, retention_days: int) -> int:
        cutoff = iso(now() - timedelta(days=retention_days))
        with self.lock, self.connect() as db:
            before = db.total_changes
            db.execute("DELETE FROM events WHERE timestamp < ?", (cutoff,))
            db.execute("DELETE FROM github_events WHERE received_at < ?", (cutoff,))
            return db.total_changes - before

    # --- reporting ---------------------------------------------------------
    def overview(self, days: int = 7) -> list[dict[str, Any]]:
        since = iso(now() - timedelta(days=days))
        with self.connect() as db:
            logins = {r[0] for r in db.execute("SELECT DISTINCT github_login FROM events")}
            logins |= {r[0] for r in db.execute("SELECT DISTINCT actor_login FROM github_events")}
            rows = []
            for login in sorted(logins):
                e = db.execute(
                    "SELECT event_type, COUNT(*) AS n, MAX(timestamp) AS last FROM events "
                    "WHERE github_login = ? AND timestamp >= ? GROUP BY event_type", (login, since)).fetchall()
                counts = {r["event_type"]: r["n"] for r in e}
                last_event = max((r["last"] for r in e), default=None)
                sessions = db.execute(
                    "SELECT COUNT(DISTINCT session_id) FROM events WHERE github_login = ? AND timestamp >= ?",
                    (login, since)).fetchone()[0]
                g = db.execute(
                    "SELECT kind, COUNT(*) AS n, MAX(received_at) AS last FROM github_events "
                    "WHERE actor_login = ? AND received_at >= ? GROUP BY kind", (login, since)).fetchall()
                gcounts = {r["kind"]: r["n"] for r in g}
                last_github = max((r["last"] for r in g), default=None)
                dark = self._dark_pushes(db, login, since)
                rows.append({
                    "login": login,
                    "last_event": last_event,
                    "last_github": last_github,
                    "sessions": sessions,
                    "pushes": gcounts.get("push", 0),
                    "prs": gcounts.get("pull_request", 0),
                    "learning_pass": sum(counts.get(t, 0) for t in LEARNING_PASS),
                    "learning_fail": sum(counts.get(t, 0) for t in LEARNING_FAIL),
                    "overrides": counts.get("mentor_learning_override_used", 0),
                    "misuse": {t: counts[t] for t in MISUSE if counts.get(t)},
                    "dark_pushes": dark,
                })
            return rows

    def _dark_pushes(self, db: sqlite3.Connection, login: str, since: str) -> int:
        pushes = db.execute(
            "SELECT received_at FROM github_events WHERE actor_login = ? AND kind = 'push' AND received_at >= ?",
            (login, since)).fetchall()
        dark = 0
        for push in pushes:
            moment = parse_time(push["received_at"])
            if moment is None:
                continue
            lo, hi = iso(moment - timedelta(hours=2)), iso(moment + timedelta(minutes=10))
            seen = db.execute(
                "SELECT 1 FROM events WHERE github_login = ? AND timestamp BETWEEN ? AND ? LIMIT 1",
                (login, lo, hi)).fetchone()
            if not seen:
                dark += 1
        return dark

    def day(self, date: str) -> dict[str, list[sqlite3.Row]]:
        start = f"{date}T00:00:00Z"
        end = f"{date}T23:59:59Z"
        with self.connect() as db:
            events = db.execute(
                "SELECT * FROM events WHERE timestamp BETWEEN ? AND ? ORDER BY github_login, timestamp", (start, end)).fetchall()
            github = db.execute(
                "SELECT * FROM github_events WHERE received_at BETWEEN ? AND ? ORDER BY actor_login, received_at", (start, end)).fetchall()
        return {"events": events, "github": github}

    def student(self, login: str, days: int = 30) -> list[sqlite3.Row]:
        since = iso(now() - timedelta(days=days))
        with self.connect() as db:
            return db.execute(
                "SELECT session_id, MIN(timestamp) AS started, MAX(timestamp) AS ended, COUNT(*) AS n, "
                "SUM(event_type IN ('learning_review_completed','learning_loop_completed')) AS passes, "
                "SUM(event_type = 'learning_review_failed') AS fails, "
                "SUM(event_type IN ('scope_check_denied','write_blocked','policy_violation')) AS misuse, "
                "MAX(branch) AS branch, MAX(task_identifier) AS task "
                "FROM events WHERE github_login = ? AND timestamp >= ? GROUP BY session_id ORDER BY started DESC",
                (login, since)).fetchall()


# --- GitHub identity for enrolment -------------------------------------------

def github_mentor_login(token: str, org: str, team: str) -> str | None:
    def get(url: str) -> dict[str, Any] | None:
        request = urllib.request.Request(url, headers={
            "Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
            "User-Agent": "frc1884-collector"})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                return json.loads(response.read())
        except (urllib.error.URLError, json.JSONDecodeError, TimeoutError):
            return None
    user = get("https://api.github.com/user")
    login = user.get("login") if isinstance(user, dict) else None
    if not login:
        return None
    membership = get(f"https://api.github.com/orgs/{org}/teams/{team}/memberships/{login}")
    if isinstance(membership, dict) and membership.get("state") == "active":
        return str(login)
    return None


# --- HTML --------------------------------------------------------------------

STYLE = """
body{font:15px/1.4 -apple-system,Segoe UI,sans-serif;margin:24px;color:#1b1b1b;background:#fff}
table{border-collapse:collapse;width:100%}th,td{padding:6px 10px;border-bottom:1px solid #ddd;text-align:left;vertical-align:top}
th{background:#f3f3f3}.bad{background:#ffe5e5}.warn{background:#fff4d6}.ok{color:#2a7}
small{color:#666}a{color:#0645ad}
"""


def esc(value: Any) -> str:
    return html.escape("" if value is None else str(value))


def age(value: str | None) -> str:
    moment = parse_time(value or "")
    if moment is None:
        return "never"
    minutes = int((now() - moment).total_seconds() // 60)
    if minutes < 60:
        return f"{minutes} min ago"
    if minutes < 48 * 60:
        return f"{minutes // 60} h ago"
    return f"{minutes // 1440} d ago"


def page(title: str, body: str) -> bytes:
    return f"<!doctype html><meta charset=utf-8><title>{esc(title)}</title><style>{STYLE}</style><h1>{esc(title)}</h1>{body}".encode()


def render_overview(store: Store, config: dict[str, Any]) -> bytes:
    rows = store.overview()
    out = ["<p>Last 7 days. <a href='/digest'>Today's digest</a> · <a href='/laptops'>Laptops</a></p>",
           "<table><tr><th>Student</th><th>Working</th><th>Learning</th><th>Appropriate use</th><th>Last seen</th></tr>"]
    for r in rows:
        misuse = ", ".join(f"{k} ×{v}" for k, v in r["misuse"].items()) or "<span class=ok>clean</span>"
        cls = "bad" if r["dark_pushes"] or r["misuse"] else ""
        dark = f" <b>{r['dark_pushes']} push(es) with no harness session</b>" if r["dark_pushes"] else ""
        out.append(
            f"<tr class='{cls}'><td><a href='/student/{esc(r['login'])}'>{esc(r['login'])}</a></td>"
            f"<td>{r['sessions']} sessions, {r['pushes']} pushes, {r['prs']} PR events{dark}</td>"
            f"<td>{r['learning_pass']} passed, {r['learning_fail']} failed"
            + (f", {r['overrides']} mentor override(s)" if r["overrides"] else "") + "</td>"
            f"<td>{misuse}</td><td>harness {age(r['last_event'])}<br><small>GitHub {age(r['last_github'])}</small></td></tr>")
    out.append("</table>")
    if not rows:
        out.append("<p>No data yet.</p>")
    return page("FRC 1884 supervision", "".join(out))


def render_laptops(store: Store, config: dict[str, Any]) -> bytes:
    stale = timedelta(minutes=int(config["heartbeat_stale_minutes"]))
    out = ["<p><a href='/'>Back</a></p><table><tr><th>Laptop</th><th>Enrolled by</th><th>Heartbeat</th><th>Protected files</th><th>Versions</th></tr>"]
    for lap in store.laptops():
        hb = parse_time(lap["last_heartbeat"] or "")
        hb_cls = "bad" if hb is None or now() - hb > stale else ""
        base, last = lap["baseline_fingerprints"], lap["last_fingerprints"]
        if not last:
            fp, fp_cls = "no heartbeat yet", "warn"
        elif base == last:
            fp, fp_cls = "<span class=ok>unchanged since enrolment</span>", ""
        else:
            changed = [k for k, v in json.loads(last).items() if json.loads(base or "{}").get(k) != v]
            fp = "<b>changed:</b> " + ", ".join(esc(c) for c in changed) + \
                 f" <form method=post action='/laptops/{esc(lap['laptop_id'])}/rebaseline' style='display:inline'><button>accept as new baseline</button></form>"
            fp_cls = "bad"
        versions = ", ".join(f"{esc(k)} {esc(v)}" for k, v in json.loads(lap["last_versions"] or "{}").items())
        out.append(f"<tr><td>{esc(lap['name'])}<br><small>{esc(lap['laptop_id'])}</small></td><td>{esc(lap['enrolled_by'])}<br><small>{esc(lap['enrolled_at'])}</small></td>"
                   f"<td class='{hb_cls}'>{age(lap['last_heartbeat'])}</td><td class='{fp_cls}'>{fp}</td><td><small>{versions}</small></td></tr>")
    out.append("</table>")
    return page("Laptops", "".join(out))


def render_digest(store: Store, date: str) -> bytes:
    data = store.day(date)
    by_login: dict[str, list[str]] = {}
    for e in data["events"]:
        by_login.setdefault(e["github_login"], []).append(
            f"{e['timestamp'][11:16]} {esc(e['event_type'])}" + (f" — {esc(e['result'])}" if e["result"] else ""))
    for g in data["github"]:
        by_login.setdefault(g["actor_login"], []).append(
            f"{g['received_at'][11:16]} GitHub {esc(g['kind'])} {esc(g['action'])} {esc(g['summary'])}")
    prev = (datetime.fromisoformat(date) - timedelta(days=1)).date().isoformat()
    nxt = (datetime.fromisoformat(date) + timedelta(days=1)).date().isoformat()
    out = [f"<p><a href='/'>Back</a> · <a href='/digest?date={prev}'>← {prev}</a> · <a href='/digest?date={nxt}'>{nxt} →</a></p>"]
    if not by_login:
        out.append("<p>Nothing recorded on this day.</p>")
    for login in sorted(by_login):
        lines = by_login[login]
        counts: dict[str, int] = {}
        for e in data["events"]:
            if e["github_login"] == login:
                counts[e["event_type"]] = counts.get(e["event_type"], 0) + 1
        passes = sum(counts.get(t, 0) for t in LEARNING_PASS)
        fails = sum(counts.get(t, 0) for t in LEARNING_FAIL)
        misuse = sum(counts.get(t, 0) for t in MISUSE)
        out.append(f"<h2>{esc(login)}</h2><p>{len(lines)} events · learning {passes} passed / {fails} failed · {misuse} blocked or denied</p>"
                   "<details><summary>Timeline</summary><pre>" + "\n".join(lines) + "</pre></details>")
    return page(f"Digest {date}", "".join(out))


def render_student(store: Store, login: str) -> bytes:
    sessions = store.student(login)
    out = ["<p><a href='/'>Back</a></p><table><tr><th>Session</th><th>Started</th><th>Length</th><th>Branch / task</th><th>Learning</th><th>Blocked</th></tr>"]
    for s in sessions:
        start, end = parse_time(s["started"]), parse_time(s["ended"])
        length = f"{int((end - start).total_seconds() // 60)} min" if start and end else "?"
        cls = "bad" if s["misuse"] else ("warn" if s["fails"] and not s["passes"] else "")
        out.append(f"<tr class='{cls}'><td><small>{esc(s['session_id'][:12])}</small></td><td>{esc(s['started'])}</td><td>{length}</td>"
                   f"<td>{esc(s['branch'])}<br><small>{esc(s['task'])}</small></td><td>{s['passes']} passed / {s['fails']} failed</td><td>{s['misuse']}</td></tr>")
    out.append("</table>")
    return page(f"Student {login} — last 30 days", "".join(out))


# --- HTTP --------------------------------------------------------------------

class Handler(BaseHTTPRequestHandler):
    store: Store
    config: dict[str, Any]
    server_version = "frc1884-collector/1"

    def log_message(self, fmt: str, *args: Any) -> None:  # quieter, no query strings
        sys.stderr.write(f"{iso(now())} {self.address_string()} {self.command} {self.path.split('?')[0]} {args[1] if len(args) > 1 else ''}\n")

    # helpers
    def send(self, status: int, body: bytes, content_type: str = "text/html; charset=utf-8") -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def send_json(self, status: int, payload: dict[str, Any]) -> None:
        self.send(status, json.dumps(payload).encode(), "application/json")

    def body(self) -> bytes:
        length = int(self.headers.get("Content-Length") or 0)
        if length > 5_000_000:
            raise ValueError("body too large")
        return self.rfile.read(length)

    def bearer(self) -> str:
        header = self.headers.get("Authorization", "")
        return header[7:].strip() if header.startswith("Bearer ") else ""

    def mentor_authorized(self) -> bool:
        password = self.config.get("mentor_password") or ""
        header = self.headers.get("Authorization", "")
        if password and header.startswith("Basic "):
            try:
                user, _, given = base64.b64decode(header[6:]).decode().partition(":")
            except (ValueError, UnicodeDecodeError):
                return False
            return user == "mentor" and hmac.compare_digest(given, password)
        return False

    def require_mentor(self) -> bool:
        if self.mentor_authorized():
            return True
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="FRC 1884 mentors"')
        self.send_header("Content-Length", "0")
        self.end_headers()
        return False

    # routes
    def do_GET(self) -> None:
        url = urlparse(self.path)
        if url.path == "/healthz":
            return self.send_json(200, {"ok": True, "time": iso(now())})
        if not self.require_mentor():
            return None
        if url.path == "/":
            return self.send(200, render_overview(self.store, self.config))
        if url.path == "/laptops":
            return self.send(200, render_laptops(self.store, self.config))
        if url.path == "/digest":
            date = parse_qs(url.query).get("date", [now().date().isoformat()])[0]
            try:
                datetime.fromisoformat(date)
            except ValueError:
                return self.send(400, b"bad date")
            return self.send(200, render_digest(self.store, date))
        if url.path.startswith("/student/"):
            return self.send(200, render_student(self.store, url.path[len("/student/"):]))
        return self.send(404, b"not found")

    def do_POST(self) -> None:
        url = urlparse(self.path)
        try:
            if url.path == "/api/events":
                return self.post_events()
            if url.path == "/api/heartbeat":
                return self.post_heartbeat()
            if url.path == "/api/enroll":
                return self.post_enroll()
            if url.path == "/api/github":
                return self.post_github()
            if url.path.startswith("/laptops/") and url.path.endswith("/rebaseline"):
                if not self.require_mentor():
                    return None
                self.store.rebaseline(url.path.split("/")[2])
                self.send_response(303)
                self.send_header("Location", "/laptops")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return None
            return self.send(404, b"not found")
        except (ValueError, json.JSONDecodeError) as error:
            return self.send_json(400, {"error": str(error)})

    def laptop(self) -> sqlite3.Row | None:
        token = self.bearer()
        return self.store.laptop_for_token(token) if token else None

    def post_events(self) -> None:
        lap = self.laptop()
        if lap is None:
            return self.send_json(401, {"error": "bad laptop token"})
        payload = json.loads(self.body() or b"{}")
        events = payload.get("events")
        if not isinstance(events, list):
            raise ValueError("events must be a list")
        stored = self.store.insert_events(lap["laptop_id"], events)
        return self.send_json(200, {"stored": stored, "received": len(events)})

    def post_heartbeat(self) -> None:
        lap = self.laptop()
        if lap is None:
            return self.send_json(401, {"error": "bad laptop token"})
        payload = json.loads(self.body() or b"{}")
        fingerprints = payload.get("fingerprints") or {}
        versions = payload.get("versions") or {}
        if not isinstance(fingerprints, dict) or not isinstance(versions, dict):
            raise ValueError("fingerprints and versions must be objects")
        self.store.heartbeat(lap["laptop_id"], {str(k): str(v) for k, v in fingerprints.items()},
                             {str(k): str(v)[:80] for k, v in versions.items()})
        return self.send_json(200, {"ok": True})

    def post_enroll(self) -> None:
        token = self.bearer()
        payload = json.loads(self.body() or b"{}")
        name = str(payload.get("laptop_name", "")).strip()[:80]
        if not token or not name:
            return self.send_json(400, {"error": "need GitHub token and laptop_name"})
        login = github_mentor_login(token, self.config["github_org"], self.config["mentor_team"])
        if not login:
            return self.send_json(403, {"error": "GitHub user is not an active mentor"})
        laptop_id, laptop_token = self.store.create_laptop(name, login)
        return self.send_json(201, {"laptop_id": laptop_id, "token": laptop_token, "enrolled_by": login})

    def post_github(self) -> None:
        secret = self.config.get("github_webhook_secret") or ""
        raw = self.body()
        signature = self.headers.get("X-Hub-Signature-256", "")
        expected = "sha256=" + hmac.new(secret.encode(), raw, hashlib.sha256).hexdigest()
        if not secret or not hmac.compare_digest(signature, expected):
            return self.send_json(401, {"error": "bad signature"})
        kind = self.headers.get("X-GitHub-Event", "")
        delivery = self.headers.get("X-GitHub-Delivery", "") or secrets.token_hex(8)
        payload = json.loads(raw or b"{}")
        repository = str((payload.get("repository") or {}).get("full_name", ""))
        wanted = self.config.get("repository_filter") or ""
        if wanted and repository != wanted:
            return self.send_json(202, {"ignored": repository})
        actor = str((payload.get("sender") or {}).get("login", ""))
        action = str(payload.get("action", ""))
        ref, sha, pr_number, summary = "", "", None, ""
        if kind == "push":
            ref = str(payload.get("ref", ""))
            sha = str(payload.get("after", ""))
            commits = payload.get("commits") or []
            actor = str((payload.get("pusher") or {}).get("name") or actor)
            summary = f"{len(commits)} commit(s) to {ref.removeprefix('refs/heads/')}"
        elif kind == "pull_request":
            pr = payload.get("pull_request") or {}
            pr_number = int(payload.get("number") or 0) or None
            sha = str((pr.get("head") or {}).get("sha", ""))
            ref = str((pr.get("head") or {}).get("ref", ""))
            summary = f"PR #{pr_number} {str(pr.get('title', ''))[:80]} → {str((pr.get('base') or {}).get('ref', ''))}"
        elif kind == "ping":
            return self.send_json(200, {"pong": True})
        else:
            summary = kind
        self.store.insert_github(delivery, kind, action, actor, repository, ref, sha, pr_number, summary)
        return self.send_json(200, {"ok": True})


def load_config(path: str | None) -> dict[str, Any]:
    config = dict(DEFAULTS)
    if path:
        config.update(json.loads(Path(path).read_text()))
    return config


def serve(config: dict[str, Any]) -> None:
    store = Store(config["db_path"])
    Handler.store = store
    Handler.config = config

    def pruner() -> None:
        while True:
            removed = store.prune(int(config["retention_days"]))
            if removed:
                sys.stderr.write(f"{iso(now())} pruned {removed} rows older than {config['retention_days']} days\n")
            time.sleep(24 * 3600)

    threading.Thread(target=pruner, daemon=True).start()
    server = ThreadingHTTPServer((config["bind"], int(config["port"])), Handler)
    sys.stderr.write(f"{iso(now())} collector listening on {config['bind']}:{config['port']} db={config['db_path']}\n")
    if not config.get("mentor_password"):
        sys.stderr.write("WARNING: mentor_password is empty; the mentor pages are unreachable until it is set\n")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["serve", "mint-token", "prune"])
    parser.add_argument("--config", help="JSON config file")
    parser.add_argument("--laptop-name", help="for mint-token: a name for the laptop")
    args = parser.parse_args()
    config = load_config(args.config)
    if args.command == "serve":
        serve(config)
        return 0
    store = Store(config["db_path"])
    if args.command == "mint-token":
        if not args.laptop_name:
            parser.error("--laptop-name is required")
        laptop_id, token = store.create_laptop(args.laptop_name, "local-admin")
        print(json.dumps({"laptop_id": laptop_id, "token": token}))
        return 0
    print(f"pruned {store.prune(int(config['retention_days']))} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
