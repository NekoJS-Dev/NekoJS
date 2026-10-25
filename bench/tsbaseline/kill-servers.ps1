# Scoped orphan cleanup for strict-TS baseline installs (bench/tsbaseline).
#
# Terminates ONLY servers this driver recorded in <target>/<node>/server-process.json,
# and only after verifying the process identity EXACTLY: the PID is alive, the recorded
# microsecond-precision creation time still matches (a reused PID gets a new creation
# time, so the window for identity confusion is closed), and the full command line still
# equals the recorded one. Records without a captured identity are skipped conservatively.
# Generic java processes and other installs are never matched. Graceful stopping is
# run.py's job (it owns the child and always tries RCON stop first); this script is the
# break-glass path for orphaned installs whose driver is gone.
#
# Usage: powershell -ExecutionPolicy Bypass -File bench\tsbaseline\kill-servers.ps1 -Target build\tsbaseline-<tag>

param(
    [Parameter(Mandatory = $true)][string]$Target
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path $Target).Path
$killed = 0

Get-ChildItem -Path $root -Recurse -Filter server-process.json | ForEach-Object {
    $record = Get-Content $_.FullName -Raw | ConvertFrom-Json
    if (-not $record.creationDate -or -not $record.commandLine) {
        Write-Output ("SKIP " + $record.node + " pid " + $record.pid + " record has no captured identity")
        return
    }
    $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.pid)" -ErrorAction SilentlyContinue
    if ($null -eq $proc) {
        Write-Output ("SKIP " + $record.node + " pid " + $record.pid + " not running")
        return
    }
    if ($proc.CommandLine -cne $record.commandLine) {
        Write-Output ("SKIP " + $record.node + " pid " + $record.pid + " command line differs from the recorded launcher")
        return
    }
    $creation = (Get-Date $proc.CreationDate).ToString('yyyy-MM-ddTHH:mm:ss.fffffff')
    if ($creation -cne $record.creationDate) {
        Write-Output ("SKIP " + $record.node + " pid " + $record.pid + " creation time differs (PID reuse)")
        return
    }
    Write-Output ("STOP " + $record.node + " pid " + $record.pid)
    Stop-Process -Id $record.pid -Force
    $killed++
}

Write-Output ("DONE killed=" + $killed)
