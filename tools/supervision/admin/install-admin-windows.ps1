# FRC 1884 — Windows admin install for the supervised harness.
#
# Run ONCE per laptop from the protected admin account, in an elevated
# PowerShell, from a checkout of the robot repository:
#
#   $env:COLLECTOR_URL = "https://supervision.example.org"
#   Set-ExecutionPolicy -Scope Process Bypass
#   .\tools\supervision\admin\install-admin-windows.ps1
#
# Installs, writable by Administrators and SYSTEM only, readable by students:
#   C:\Program Files\FRC1884\tools\robotics_harness   admin-owned harness copy
#   C:\Program Files\FRC1884\bin\frc1884_hook.py       machine-wide hook wrapper
#   C:\Program Files\FRC1884\bin\ship.py               event shipper
#   C:\Program Files\FRC1884\etc\approved_repos.txt
#   C:\Program Files\ClaudeCode\managed-settings.json  Claude Code lockdown + hooks
#   %ProgramData%\OpenAI\Codex\requirements.toml       Codex lockdown + hooks
#   %ProgramData%\FRC1884\shipper.json                 collector token (admins only)
#   Scheduled task "FRC1884 Shipper" running ship.py as SYSTEM at startup
#
# STATUS: written from the macOS script; not yet run on a Windows laptop.
# Override $env:PREFIX, $env:CLAUDE_MANAGED_DIR, $env:CODEX_DIR, $env:ETC_DIR,
# $env:SKIP_TASK=1 to test into a temporary location.

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$Prefix = if ($env:PREFIX) { $env:PREFIX } else { "C:\Program Files\FRC1884" }
$ClaudeManagedDir = if ($env:CLAUDE_MANAGED_DIR) { $env:CLAUDE_MANAGED_DIR } else { "C:\Program Files\ClaudeCode" }
$CodexDir = if ($env:CODEX_DIR) { $env:CODEX_DIR } else { Join-Path $env:ProgramData "OpenAI\Codex" }
$EtcDir = if ($env:ETC_DIR) { $env:ETC_DIR } else { Join-Path $env:ProgramData "FRC1884" }
$CollectorUrl = $env:COLLECTOR_URL
$LaptopName = if ($env:LAPTOP_NAME) { $env:LAPTOP_NAME } else { $env:COMPUTERNAME }

function Log($m) { Write-Host "`n==> $m" -ForegroundColor Blue }
function Ok($m)  { Write-Host "    ✓ $m" -ForegroundColor Green }

$Python = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $Python) { throw "python not found; install Python 3.12 for all users first" }
& $Python -c "import sys; sys.exit(0 if sys.version_info >= (3, 12) else 1)"
if ($LASTEXITCODE -ne 0) { throw "Python 3.12+ required at $Python" }
if (-not (Test-Path (Join-Path $RepoRoot "tools\robotics_harness\entry.py"))) { throw "run from a checkout of the robot repository" }

function Lock-Down($path) {
    # Administrators and SYSTEM: full control. Users: read and execute only.
    icacls $path /inheritance:r /grant:r "Administrators:(OI)(CI)F" "SYSTEM:(OI)(CI)F" "Users:(OI)(CI)RX" | Out-Null
}
function Admins-Only($path) {
    icacls $path /inheritance:r /grant:r "Administrators:F" "SYSTEM:F" | Out-Null
}

Log "Harness copy -> $Prefix"
New-Item -ItemType Directory -Force (Join-Path $Prefix "tools"), (Join-Path $Prefix "bin"), (Join-Path $Prefix "etc") | Out-Null
$HarnessDst = Join-Path $Prefix "tools\robotics_harness"
if (Test-Path $HarnessDst) { Remove-Item -Recurse -Force $HarnessDst }
Copy-Item -Recurse (Join-Path $RepoRoot "tools\robotics_harness") $HarnessDst
Get-ChildItem -Recurse -Directory -Filter "__pycache__" $HarnessDst | Remove-Item -Recurse -Force
Copy-Item (Join-Path $RepoRoot "tools\supervision\admin\frc1884_hook.py") (Join-Path $Prefix "bin\frc1884_hook.py") -Force
Copy-Item (Join-Path $RepoRoot "tools\supervision\agent\ship.py") (Join-Path $Prefix "bin\ship.py") -Force
$Revision = (git -C $RepoRoot rev-parse HEAD 2>$null); if (-not $Revision) { $Revision = "unknown" }
Set-Content (Join-Path $Prefix "etc\harness_revision.txt") $Revision
$Approved = Join-Path $Prefix "etc\approved_repos.txt"
if (-not (Test-Path $Approved)) { Set-Content $Approved "FRC1884/Season2027`nFRC1884/Preseason2027" }
Lock-Down $Prefix
Ok "harness revision $Revision"

$Hook = Join-Path $Prefix "bin\frc1884_hook.py"

Log "Claude Code managed settings -> $ClaudeManagedDir\managed-settings.json"
New-Item -ItemType Directory -Force $ClaudeManagedDir | Out-Null
$Managed = Get-Content (Join-Path $RepoRoot "tools\supervision\admin\managed-settings.claude.json") -Raw
# JSON needs escaped backslashes inside the command string.
$Managed = $Managed.Replace("__PYTHON__", $Python.Replace("\", "\\")).Replace("__HOOK__", $Hook.Replace("\", "\\"))
$Managed = $Managed.Replace('"Read(//etc/frc1884/**)",', '').Replace('"Read(//Library/Application Support/ClaudeCode/**)"', '"Read(//ProgramData/FRC1884/**)"')
Set-Content (Join-Path $ClaudeManagedDir "managed-settings.json") $Managed -Encoding UTF8
& $Python -c "import json,sys; json.load(open(sys.argv[1], encoding='utf-8-sig'))" (Join-Path $ClaudeManagedDir "managed-settings.json")
if ($LASTEXITCODE -ne 0) { throw "managed-settings.json is not valid JSON" }
Lock-Down $ClaudeManagedDir
Ok "valid JSON"

Log "Codex requirements -> $CodexDir\requirements.toml"
New-Item -ItemType Directory -Force $CodexDir | Out-Null
$Toml = Get-Content (Join-Path $RepoRoot "tools\supervision\admin\requirements.codex.toml") -Raw
$Toml = $Toml.Replace("__WINDOWS_MANAGED_DIR__", (Join-Path $Prefix "bin")).Replace("__MANAGED_DIR__", "/opt/frc1884/bin")
$Toml = $Toml.Replace("__WINDOWS_PYTHON__", $Python).Replace("__WINDOWS_HOOK__", $Hook)
$Toml = $Toml.Replace("__PYTHON__", "python3").Replace("__HOOK__", "/opt/frc1884/bin/frc1884_hook.py")
Set-Content (Join-Path $CodexDir "requirements.toml") $Toml -Encoding UTF8
& $Python -c "import tomllib,sys; tomllib.load(open(sys.argv[1],'rb'))" (Join-Path $CodexDir "requirements.toml")
if ($LASTEXITCODE -ne 0) { throw "requirements.toml is not valid TOML" }
Lock-Down $CodexDir
Ok "valid TOML"

Log "Shipper config -> $EtcDir\shipper.json"
New-Item -ItemType Directory -Force $EtcDir | Out-Null
$ShipperCfg = Join-Path $EtcDir "shipper.json"
if ($CollectorUrl) {
    $enrolled = (Test-Path $ShipperCfg) -and ((Get-Content $ShipperCfg -Raw | ConvertFrom-Json).token)
    if ($enrolled) { Ok "already enrolled; keeping existing token" }
    else {
        # Enrolment proves the mentor's GitHub identity through their own gh login.
        & $Python (Join-Path $Prefix "bin\ship.py") enroll --server $CollectorUrl --laptop-name $LaptopName --config $ShipperCfg
        if ($LASTEXITCODE -ne 0) { throw "enrolment failed; are you logged in to gh as a mentor?" }
    }
} else {
    Write-Host "    COLLECTOR_URL not set; skipping enrolment (set it and rerun to enable shipping)"
    if (-not (Test-Path $ShipperCfg)) { Set-Content $ShipperCfg "{}" }
}
$cfg = Get-Content $ShipperCfg -Raw | ConvertFrom-Json
$cfg | Add-Member -Force interval_seconds 30
$cfg | Add-Member -Force heartbeat_seconds 300
$cfg | Add-Member -Force state_dir (Join-Path $EtcDir "shipper")
$cfg | Add-Member -Force homes_glob @("C:/Users/*")
$cfg | Add-Member -Force fingerprint_paths @(
    (Join-Path $ClaudeManagedDir "managed-settings.json"), (Join-Path $CodexDir "requirements.toml"),
    $Hook, (Join-Path $Prefix "bin\ship.py"), $Approved,
    (Join-Path $HarnessDst "entry.py"), (Join-Path $HarnessDst "runtime\harness\policy\session_hooks.py"))
$cfg | Add-Member -Force version_commands ([pscustomobject]@{ claude = @("claude", "--version"); codex = @("codex", "--version"); java = @("java", "-version"); gh = @("gh", "--version") })
$cfg | ConvertTo-Json -Depth 5 | Set-Content $ShipperCfg -Encoding UTF8
Admins-Only $ShipperCfg
Ok "admins-only config"

Log "Scheduled task: FRC1884 Shipper (SYSTEM, at startup)"
if ($env:SKIP_TASK -eq "1") { Write-Host "    skipped (test mode)" }
else {
    $Action = New-ScheduledTaskAction -Execute $Python -Argument "`"$(Join-Path $Prefix 'bin\ship.py')`" --config `"$ShipperCfg`""
    $Trigger = New-ScheduledTaskTrigger -AtStartup
    $Settings = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit (New-TimeSpan -Days 3650) -Hidden
    $Principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    Register-ScheduledTask -TaskName "FRC1884 Shipper" -Action $Action -Trigger $Trigger -Settings $Settings -Principal $Principal -Force | Out-Null
    Start-ScheduledTask -TaskName "FRC1884 Shipper"
    Ok "task registered and started"
}

Log "Done"
Write-Host "Students must be Standard (non-admin) accounts. Verify with:  python $RepoRoot\tools\laptop\doctor.py"
