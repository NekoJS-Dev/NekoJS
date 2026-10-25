# Scoped orphan cleanup for strict-TS baseline installs (bench/tsbaseline).
#
# Terminates ONLY servers this driver recorded in <target>/<node>/server-process.json,
# and only after verifying all three identity facts: the PID is alive, its command line
# still matches the recorded launcher shape, and the process creation time matches the
# recorded start (a reused PID would fall outside the window). Generic java processes
# and other installs are never matched. Graceful stopping is run.py's job (it owns the
# child and always tries RCON stop first); this script is the break-glass path for
# orphaned installs whose driver is gone.
#
# Usage: powershell -ExecutionPolicy Bypass -File bench\tsbaseline\kill-servers.ps1 -Target build\tsbaseline-<tag>

param(
    [Parameter(Mandatory = $true)][string]$Target
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path $Target).Path
$launcherPattern = 'win_args\.txt|fabric-server-launch'
$killed = 0

Get-ChildItem -Path $root -Recurse -Filter server-process.json | ForEach-Object {
    $record = Get-Content $_.FullName -Raw | ConvertFrom-Json
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)" -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
        Write-Output ("SKIP " + $record.node + " pid " + $record.pid + " not running")
        return
    }
    if ($proc.CommandLine -notmatch $launcherPattern) {
        Write-Output ("SKIP " + $record.node + " pid " + $record.pid + " command line no longer matches the recorded launcher")
        return
    }
    $recorded = [DateTimeOffset]::FromUnixTimeMilliseconds([long]($record.startedAtEpoch * 1000))
    $started = $proc.CreationDate.ToUniversalTime()
    if ([Math]::Abs(($started - $recorded.UtcDateTime).TotalSeconds) -gt 30) {
        Write-Output ("SKIP " + $record.node + " pid " + $record.pid + " creation time mismatch (probable PID reuse)")
        return
    }
    Write-Output ("STOP " + $record.node + " pid " + $record.pid)
    Stop-Process -Id $record.pid -Force
    $killed++
}

Write-Output ("DONE killed=" + $killed)
