#!/usr/bin/env bash
# FRC 1884 — macOS admin install for the supervised harness.
#
# Run ONCE per laptop from the protected admin account, with sudo, from a
# checkout of the robot repository:
#
#   sudo COLLECTOR_URL=https://supervision.example.org \
#        bash tools/supervision/admin/install-admin-macos.sh
#
# It installs, owned by root and read-only for students:
#   PREFIX/tools/robotics_harness      admin-owned copy of the harness runtime
#   PREFIX/bin/frc1884_hook.py         machine-wide hook wrapper
#   PREFIX/bin/ship.py                 event shipper
#   PREFIX/etc/approved_repos.txt      repositories where AI work is allowed
#   Claude Code managed settings       hooks + lockdown, students cannot override
#   Codex requirements.toml            hooks + lockdown, students cannot override
#   /etc/frc1884/shipper.json          collector token (root-only)
#   LaunchDaemon org.frc1884.shipper   runs the shipper as root at boot
#
# Override PREFIX, CLAUDE_MANAGED_DIR, CODEX_DIR, ETC_DIR, LAUNCHD_DIR, PYTHON
# to test into a temporary location without touching the system.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PREFIX="${PREFIX:-/opt/frc1884}"
CLAUDE_MANAGED_DIR="${CLAUDE_MANAGED_DIR:-/Library/Application Support/ClaudeCode}"
CODEX_DIR="${CODEX_DIR:-/etc/codex}"
ETC_DIR="${ETC_DIR:-/etc/frc1884}"
LAUNCHD_DIR="${LAUNCHD_DIR:-/Library/LaunchDaemons}"
PYTHON="${PYTHON:-$(command -v python3.12 || command -v python3)}"
COLLECTOR_URL="${COLLECTOR_URL:-}"
LAPTOP_NAME="${LAPTOP_NAME:-$(scutil --get ComputerName 2>/dev/null || hostname)}"
SKIP_LAUNCHD="${SKIP_LAUNCHD:-0}"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()  { printf '    \033[32m✓ %s\033[0m\n' "$*"; }
die() { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

[[ "$(uname -s)" == "Darwin" ]] || die "macOS only; use install-admin-windows.ps1 on Windows"
[[ -f "$REPO_ROOT/tools/robotics_harness/entry.py" ]] || die "run from a checkout of the robot repository"
[[ -x "$PYTHON" ]] || die "python3 not found"
"$PYTHON" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 12) else 1)' || die "Python 3.12+ required at $PYTHON"
PYTHON="$(cd "$(dirname "$PYTHON")" && pwd -P)/$(basename "$PYTHON")"
case "$PYTHON" in
  /Users/*|/tmp/*|/private/tmp/*) echo "WARNING: $PYTHON lives in a user folder; students may be able to replace it. Install Python for all users (python.org installer) and set PYTHON=/Library/Frameworks/Python.framework/Versions/3.12/bin/python3." ;;
esac
if [[ "$PREFIX" == /opt/* && "$(id -u)" -ne 0 ]]; then die "run with sudo"; fi

install_file() { # src dst mode
  install -d -m 755 "$(dirname "$2")"
  install -m "$3" "$1" "$2"
}

log "Harness copy → $PREFIX"
install -d -m 755 "$PREFIX/tools" "$PREFIX/bin" "$PREFIX/etc"
rm -rf "$PREFIX/tools/robotics_harness"
cp -R "$REPO_ROOT/tools/robotics_harness" "$PREFIX/tools/robotics_harness"
find "$PREFIX/tools/robotics_harness" -name __pycache__ -prune -exec rm -rf {} + 2>/dev/null || true
chmod -R u=rwX,go=rX "$PREFIX/tools"
install_file "$REPO_ROOT/tools/supervision/admin/frc1884_hook.py" "$PREFIX/bin/frc1884_hook.py" 755
install_file "$REPO_ROOT/tools/supervision/agent/ship.py" "$PREFIX/bin/ship.py" 755
git -C "$REPO_ROOT" rev-parse HEAD > "$PREFIX/etc/harness_revision.txt" 2>/dev/null || echo unknown > "$PREFIX/etc/harness_revision.txt"
[[ -f "$PREFIX/etc/approved_repos.txt" ]] || printf 'FRC1884/Season2027\nFRC1884/Preseason2027\n' > "$PREFIX/etc/approved_repos.txt"
chmod 644 "$PREFIX/etc/approved_repos.txt" "$PREFIX/etc/harness_revision.txt"
ok "harness revision $(cat "$PREFIX/etc/harness_revision.txt")"

log "Claude Code managed settings → $CLAUDE_MANAGED_DIR/managed-settings.json"
install -d -m 755 "$CLAUDE_MANAGED_DIR"
sed -e "s|__PYTHON__|$PYTHON|g" -e "s|__HOOK__|$PREFIX/bin/frc1884_hook.py|g" \
  "$REPO_ROOT/tools/supervision/admin/managed-settings.claude.json" > "$CLAUDE_MANAGED_DIR/managed-settings.json"
chmod 644 "$CLAUDE_MANAGED_DIR/managed-settings.json"
"$PYTHON" -c "import json,sys; json.load(open(sys.argv[1]))" "$CLAUDE_MANAGED_DIR/managed-settings.json"
ok "valid JSON"

log "Codex requirements → $CODEX_DIR/requirements.toml"
install -d -m 755 "$CODEX_DIR"
sed -e "s|__WINDOWS_MANAGED_DIR__|C:\\\\Program Files\\\\FRC1884\\\\bin|g" \
    -e "s|__WINDOWS_PYTHON__|python|g" -e "s|__WINDOWS_HOOK__|C:\\\\Program Files\\\\FRC1884\\\\bin\\\\frc1884_hook.py|g" \
    -e "s|__PYTHON__|$PYTHON|g" -e "s|__HOOK__|$PREFIX/bin/frc1884_hook.py|g" -e "s|__MANAGED_DIR__|$PREFIX/bin|g" \
  "$REPO_ROOT/tools/supervision/admin/requirements.codex.toml" > "$CODEX_DIR/requirements.toml"
chmod 644 "$CODEX_DIR/requirements.toml"
"$PYTHON" -c "import tomllib,sys; tomllib.load(open(sys.argv[1],'rb'))" "$CODEX_DIR/requirements.toml"
ok "valid TOML"

log "Shipper config → $ETC_DIR/shipper.json"
install -d -m 755 "$ETC_DIR"
if [[ -n "$COLLECTOR_URL" ]]; then
  if [[ -f "$ETC_DIR/shipper.json" ]] && "$PYTHON" -c "import json,sys; d=json.load(open(sys.argv[1])); sys.exit(0 if d.get('token') else 1)" "$ETC_DIR/shipper.json"; then
    ok "already enrolled; keeping existing token"
  else
    # Enrolment proves the mentor's GitHub identity with their own gh login.
    MENTOR_USER="${SUDO_USER:-$(id -un)}"
    sudo -u "$MENTOR_USER" "$PYTHON" "$PREFIX/bin/ship.py" enroll --server "$COLLECTOR_URL" --laptop-name "$LAPTOP_NAME" --config "$ETC_DIR/shipper.json.new" \
      || die "enrolment failed; is $MENTOR_USER logged in to gh as a mentor?"
    mv "$ETC_DIR/shipper.json.new" "$ETC_DIR/shipper.json"
  fi
else
  echo "    COLLECTOR_URL not set; skipping enrolment (set it and rerun to enable shipping)"
  [[ -f "$ETC_DIR/shipper.json" ]] || echo '{}' > "$ETC_DIR/shipper.json"
fi
"$PYTHON" - "$ETC_DIR/shipper.json" "$PREFIX" "$CLAUDE_MANAGED_DIR" "$CODEX_DIR" <<'EOF'
import json, sys
path, prefix, claude_dir, codex_dir = sys.argv[1:5]
cfg = json.load(open(path))
cfg.update({
    "interval_seconds": 30,
    "heartbeat_seconds": 300,
    "state_dir": "/var/lib/frc1884/shipper",
    "homes_glob": ["/Users/*"],
    "fingerprint_paths": [
        f"{claude_dir}/managed-settings.json",
        f"{codex_dir}/requirements.toml",
        f"{prefix}/bin/frc1884_hook.py",
        f"{prefix}/bin/ship.py",
        f"{prefix}/etc/approved_repos.txt",
        f"{prefix}/tools/robotics_harness/entry.py",
        f"{prefix}/tools/robotics_harness/runtime/harness/policy/session_hooks.py",
    ],
    "version_commands": {"claude": ["claude", "--version"], "codex": ["codex", "--version"], "java": ["java", "-version"], "gh": ["gh", "--version"]},
})
json.dump(cfg, open(path, "w"), indent=2)
EOF
chown root:wheel "$ETC_DIR/shipper.json" 2>/dev/null || true
chmod 600 "$ETC_DIR/shipper.json"
ok "root-only config"

log "LaunchDaemon → $LAUNCHD_DIR/org.frc1884.shipper.plist"
install -d -m 755 "$LAUNCHD_DIR"
cat > "$LAUNCHD_DIR/org.frc1884.shipper.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>org.frc1884.shipper</string>
  <key>ProgramArguments</key><array>
    <string>$PYTHON</string><string>$PREFIX/bin/ship.py</string><string>--config</string><string>$ETC_DIR/shipper.json</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>/var/log/frc1884-shipper.log</string>
  <key>StandardErrorPath</key><string>/var/log/frc1884-shipper.log</string>
</dict></plist>
EOF
chmod 644 "$LAUNCHD_DIR/org.frc1884.shipper.plist"
if [[ "$SKIP_LAUNCHD" == "1" || "$(id -u)" -ne 0 ]]; then
  echo "    not loading the daemon (test mode or not root)"
else
  launchctl bootout system/org.frc1884.shipper 2>/dev/null || true
  launchctl bootstrap system "$LAUNCHD_DIR/org.frc1884.shipper.plist"
  ok "daemon loaded"
fi

log "Done"
echo "Students must be Standard (non-admin) accounts. Verify with:  $PYTHON $REPO_ROOT/tools/laptop/doctor.py"
