# FRC 1884 Season2027 — Windows laptop bootstrap.
#
# Installs the tools a student laptop needs for the AI harness, clones the
# repository, and runs the doctor. Safe to rerun. Requires Windows 10/11 with
# winget (App Installer). Run in PowerShell:
#
#   Set-ExecutionPolicy -Scope Process Bypass; .\tools\laptop\bootstrap-windows.ps1
#
# STATUS: written from the macOS script; not yet run on a Windows laptop.
# Interactive logins (GitHub, Claude Code, Codex) are left to the student with a
# mentor present; this script never stores credentials.

$ErrorActionPreference = "Stop"
$RepoUrl = if ($env:REPO_URL) { $env:REPO_URL } else { "https://github.com/FRC1884/Preseason2027.git" }
$RepoDir = if ($env:REPO_DIR) { $env:REPO_DIR } else { Join-Path $HOME "FRC1884\Season2027" }
$GradleVersion = "8.11"
$WpilibYear = "2026"

function Log($m)  { Write-Host "`n==> $m" -ForegroundColor Blue }
function Ok($m)   { Write-Host "    ✓ $m" -ForegroundColor Green }
function Warn($m) { Write-Host "    ! $m" -ForegroundColor Yellow }

function Ensure-Winget($id, $label) {
    if (winget list --id $id --exact 2>$null | Select-String $id) { Ok "$label present"; return }
    winget install --id $id --exact --silent --accept-package-agreements --accept-source-agreements
}

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Error "winget is missing. Install 'App Installer' from the Microsoft Store, then rerun."
}

Log "Core tools: Git, GitHub CLI, Python 3.12, Temurin JDK 17, Node.js"
Ensure-Winget "Git.Git" "Git"
Ensure-Winget "GitHub.cli" "GitHub CLI"
Ensure-Winget "Python.Python.3.12" "Python 3.12"
Ensure-Winget "EclipseAdoptium.Temurin.17.JDK" "Temurin JDK 17"
Ensure-Winget "OpenJS.NodeJS.LTS" "Node.js LTS"

# Refresh PATH for this session so newly installed tools resolve.
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

Log "Gradle $GradleVersion"
if (Test-Path (Join-Path $RepoDir "gradlew.bat")) { Ok "repository has a Gradle wrapper; no system Gradle needed" }
else {
    $Dist = Join-Path $HOME ".gradle-dist\gradle-$GradleVersion"
    if (-not (Test-Path (Join-Path $Dist "bin\gradle.bat"))) {
        New-Item -ItemType Directory -Force (Join-Path $HOME ".gradle-dist") | Out-Null
        $Zip = Join-Path $env:TEMP "gradle.zip"
        Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $Zip
        Expand-Archive $Zip -DestinationPath (Join-Path $HOME ".gradle-dist") -Force
        Remove-Item $Zip
    }
    $UserPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    if ($UserPath -notlike "*gradle-dist*") {
        [System.Environment]::SetEnvironmentVariable("Path", "$Dist\bin;$UserPath", "User")
    }
    $env:Path = "$Dist\bin;$env:Path"
    Ok "gradle $GradleVersion installed at $Dist"
}

Log "AI agents: Claude Code and Codex CLI"
if (Get-Command claude -ErrorAction SilentlyContinue) { Ok "Claude Code present" } else { npm install -g @anthropic-ai/claude-code }
if (Get-Command codex -ErrorAction SilentlyContinue)  { Ok "Codex CLI present" }  else { npm install -g @openai/codex }

Log "Repository"
if (Test-Path (Join-Path $RepoDir ".git")) {
    Ok "already cloned at $RepoDir"
    git -C $RepoDir pull --ff-only
} else {
    New-Item -ItemType Directory -Force (Split-Path $RepoDir) | Out-Null
    git clone $RepoUrl $RepoDir
    Ok "cloned to $RepoDir"
}

Log "WPILib $WpilibYear (robot deploy toolchain)"
if (Test-Path "C:\Users\Public\wpilib\$WpilibYear") { Ok "present" }
else {
    Warn "Not installed. Download the Windows installer from https://github.com/wpilibsuite/allwpilib/releases"
    Warn "and run it once (about 2 GB). It is only needed to deploy to the robot."
}

Log "Logins (a mentor should be present)"
$login = gh api user --jq .login 2>$null
if ($login) { Ok "GitHub: logged in as $login" } else { Warn "GitHub: run   gh auth login   with the student's own GitHub account" }
Warn "Claude Code: run   claude   once and log in with the team-approved account"
Warn "Codex: run   codex login   with the team-approved account"

Log "Doctor"
python (Join-Path $RepoDir "tools\laptop\doctor.py") --repo $RepoDir

Write-Host "`nDone. If the doctor shows FAIL lines, fix them and rerun:  python $RepoDir\tools\laptop\doctor.py"
