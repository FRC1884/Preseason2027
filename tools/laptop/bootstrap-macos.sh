#!/usr/bin/env bash
# FRC 1884 Season2027 — macOS laptop bootstrap.
#
# Installs the tools a student laptop needs for the AI harness, clones the
# repository, and runs the doctor. Safe to rerun; every step is skip-if-present.
#
#   bash tools/laptop/bootstrap-macos.sh            # from an existing checkout
#   curl -fsSL https://raw.githubusercontent.com/FRC1884/Preseason2027/main/tools/laptop/bootstrap-macos.sh | bash
#
# Interactive logins (GitHub, Claude Code, Codex) are left to the student with a
# mentor present; this script never stores credentials.

set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/FRC1884/Preseason2027.git}"
REPO_DIR="${REPO_DIR:-$HOME/FRC1884/Season2027}"
GRADLE_VERSION="8.11"
WPILIB_YEAR="2026"

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()   { printf '    \033[32m✓ %s\033[0m\n' "$*"; }
warn() { printf '    \033[33m! %s\033[0m\n' "$*"; }

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script is for macOS. Use bootstrap-windows.ps1 on Windows." >&2
  exit 1
fi

log "Xcode command line tools (git)"
if xcode-select -p >/dev/null 2>&1; then ok "present"; else
  xcode-select --install || true
  warn "Finish the Xcode tools dialog, then rerun this script."
  exit 1
fi

log "Homebrew"
if command -v brew >/dev/null 2>&1; then ok "present"; else
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
fi
if [[ -x /opt/homebrew/bin/brew ]]; then eval "$(/opt/homebrew/bin/brew shellenv)"; fi
if [[ -x /usr/local/bin/brew ]]; then eval "$(/usr/local/bin/brew shellenv)"; fi

brew_formula() { brew list --formula "$1" >/dev/null 2>&1 && ok "$1 present" || brew install "$1"; }
brew_cask()    { brew list --cask    "$1" >/dev/null 2>&1 && ok "$1 present" || brew install --cask "$1"; }

log "Core tools: git, gh, python 3.12, Temurin JDK 17"
brew_formula git
brew_formula gh
brew_formula python@3.12
brew_cask temurin@17

log "Gradle $GRADLE_VERSION"
if [[ -f "$REPO_DIR/gradlew" ]]; then ok "repository has a Gradle wrapper; no system Gradle needed"
elif gradle --version 2>/dev/null | grep -q "Gradle $GRADLE_VERSION"; then ok "gradle $GRADLE_VERSION present"
else
  # Homebrew's gradle formula tracks the latest major, which CI does not use.
  # Install exactly 8.11 under ~/.gradle-dist and expose it on PATH.
  DIST="$HOME/.gradle-dist/gradle-$GRADLE_VERSION"
  if [[ ! -x "$DIST/bin/gradle" ]]; then
    mkdir -p "$HOME/.gradle-dist"
    curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o /tmp/gradle.zip
    unzip -qo /tmp/gradle.zip -d "$HOME/.gradle-dist"
    rm -f /tmp/gradle.zip
  fi
  PROFILE="$HOME/.zprofile"
  grep -q "gradle-dist" "$PROFILE" 2>/dev/null || echo "export PATH=\"$DIST/bin:\$PATH\"" >> "$PROFILE"
  export PATH="$DIST/bin:$PATH"
  ok "gradle $GRADLE_VERSION installed at $DIST"
fi

log "AI agents: Claude Code and Codex CLI"
brew_cask claude-code
brew_cask codex

log "Repository"
if [[ -d "$REPO_DIR/.git" ]]; then
  ok "already cloned at $REPO_DIR"
  git -C "$REPO_DIR" pull --ff-only || warn "pull failed; ask a mentor"
else
  mkdir -p "$(dirname "$REPO_DIR")"
  git clone "$REPO_URL" "$REPO_DIR"
  ok "cloned to $REPO_DIR"
fi

log "WPILib $WPILIB_YEAR (robot deploy toolchain)"
if [[ -d "$HOME/wpilib/$WPILIB_YEAR" ]]; then ok "present"; else
  warn "Not installed. Download the macOS installer from https://github.com/wpilibsuite/allwpilib/releases"
  warn "and run it once (about 2 GB). It is only needed to deploy to the robot."
fi

log "Logins (a mentor should be present)"
if gh api user --jq .login >/dev/null 2>&1; then ok "GitHub: logged in as $(gh api user --jq .login)"; else
  warn "GitHub: run   gh auth login   with the student's own GitHub account"
fi
warn "Claude Code: run   claude   once and log in with the team-approved account"
warn "Codex: run   codex login   with the team-approved account"

log "Doctor"
python3 "$REPO_DIR/tools/laptop/doctor.py" --repo "$REPO_DIR" || true

echo
echo "Done. If the doctor shows FAIL lines, fix them and rerun:  python3 $REPO_DIR/tools/laptop/doctor.py"
