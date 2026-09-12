# datafix03 read-back runner (ticket 03). Windows PowerShell 5.1.
# MUST be saved with UTF-8 BOM: PS 5.1 decodes BOM-less files with the ANSI code page
# and Chinese comments then corrupt parsing (same pitfall as bench/perf/sample.ps1).
# All log markers compared here are ASCII on purpose.
#
# Steps (run from the isolated worktree's bench/datafix):
#   deploy            wipe + deploy fixtures/nekojs into versions/<Node>/run/nekojs, write eula/server.properties
#   deploy-world      deploy fixtures/world/nekojs_packs into run/world/nekojs_packs (world must exist)
#   start             detached gradlew :<Node>:runServer, output to out/<Session>-stdout.log
#   wait-done         poll stdout for "Done (" or failure markers
#   rcon              send -Commands via RCON (tee to out/rcon-<ts>.log)
#   stop              RCON stop + wait gradle client exit + world/session.lock release
#   snapshot          record SHA256|len|mtime of protected data set into out/snapshots/<Name>.txt
#   compare-snapshot  diff out/snapshots/<Before>.txt vs <After>.txt (summary line DIFF-SUMMARY)
#   break             add syntax-error script run/nekojs/server_scripts/zz-broken.js
#   restore           remove zz-broken.js
#   summon-target     forceload spawn chunk + summon tagged armor stand (pdata write trigger)
#   query-pdata       RCON `data get entity ... NeoForgeData[.NekoJSPersistentData]`
#   probe-clear       delete run/.neko_probe (probe cache regen scenario)
#   find              grep markers in logs/latest.log + session logs (-Pattern)

param(
    [Parameter(Position = 0, Mandatory = $true)][string]$Step,
    [string]$Session = 's1',
    [string]$Node = '26.1.2',
    [int]$TimeoutSec = 900,
    [string]$Name = '',
    [string]$Before = '',
    [string]$After = '',
    [string]$Pattern = '',
    [string[]]$Commands = @()
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Project = (Resolve-Path (Join-Path $ScriptDir '..\..')).Path
$RunDir = Join-Path $Project ("versions\" + $Node + "\run")
$OutDir = Join-Path $ScriptDir 'out'
$SnapDir = Join-Path $OutDir 'snapshots'
$FixtureDir = Join-Path $ScriptDir 'fixtures'
$RconPort = 25872
$RconPassword = 'datafix03'

function Ensure-OutDirs {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    New-Item -ItemType Directory -Force -Path $SnapDir | Out-Null
}

function Remove-TreeWithRetry([string]$path) {
    if (-not (Test-Path $path)) { return }
    for ($i = 0; $i -lt 30; $i++) {
        try { Remove-Item -LiteralPath $path -Recurse -Force; return } catch { Start-Sleep -Milliseconds 500 }
    }
    throw "failed to remove $path after retries"
}

function Copy-Tree([string]$src, [string]$dst) {
    # robocopy /E: PS 5.1 Copy-Item with wildcard+recurse mis-nests nested dirs (perf harness lesson)
    $rc = Start-Process -FilePath 'robocopy' -ArgumentList @($src, $dst, '/E', '/NFL', '/NDL', '/NJH', '/NJS') -Wait -PassThru -WindowStyle Hidden
    if ($rc.ExitCode -ge 8) { throw "robocopy failed ($($rc.ExitCode)) $src -> $dst" }
}

function Get-PidFromSession {
    $pidFile = Join-Path $OutDir ($Session + '.pid')
    if (Test-Path $pidFile) { return [int](Get-Content $pidFile | Select-Object -First 1) }
    return $null
}

function Test-ProcessAlive([int]$procId) {
    return ($null -ne (Get-Process -Id $procId -ErrorAction SilentlyContinue))
}

function Wait-WorldLockRelease([int]$timeoutSeconds) {
    $lockFile = Join-Path $RunDir 'world\session.lock'
    if (-not (Test-Path $lockFile)) { Write-Host 'LOCK-GONE'; return }
    $deadline = [DateTime]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $fs = [System.IO.File]::Open($lockFile, 'Open', 'ReadWrite', 'None')
            $fs.Close()
            Write-Host 'LOCK-RELEASED'
            return
        } catch { Start-Sleep -Milliseconds 500 }
    }
    Write-Host "LOCK-HELD after ${timeoutSeconds}s"
}

# ---------- steps ----------

switch ($Step) {

    'deploy' {
        Ensure-OutDirs
        $nekoRoot = Join-Path $RunDir 'nekojs'
        Remove-TreeWithRetry $nekoRoot
        Copy-Tree (Join-Path $FixtureDir 'nekojs') $nekoRoot
        # root editor config (<gamedir>/jsconfig.json) is a separate protected fixture
        Copy-Item (Join-Path $FixtureDir 'jsconfig.json') (Join-Path $RunDir 'jsconfig.json') -Force
        Set-Content -Path (Join-Path $RunDir 'eula.txt') -Value 'eula=true' -Encoding Ascii
        $props = @(
            'server-port=25871',
            'enable-rcon=true',
            'rcon.port=25872',
            'rcon.password=datafix03',
            'online-mode=false',
            'difficulty=peaceful',
            'gamemode=survival',
            'view-distance=8',
            'spawn-protection=0',
            'pause-when-empty-seconds=-1',
            'level-seed=datafix03',
            'motd=datafix03'
        )
        Set-Content -Path (Join-Path $RunDir 'server.properties') -Value $props -Encoding Ascii
        Write-Host 'DEPLOY-OK'
    }

    'deploy-world' {
        $src = Join-Path $FixtureDir 'world\nekojs_packs'
        $dst = Join-Path $RunDir 'world\nekojs_packs'
        if (-not (Test-Path (Join-Path $RunDir 'world'))) { throw 'run/world does not exist yet (start the server once first)' }
        Copy-Tree $src $dst
        Write-Host 'DEPLOY-WORLD-OK'
    }

    'start' {
        Ensure-OutDirs
        $stdout = Join-Path $OutDir ($Session + '-stdout.log')
        $stderr = Join-Path $OutDir ($Session + '-stderr.log')
        if (Test-Path $stdout) { Remove-Item $stdout -Force }
        if (Test-Path $stderr) { Remove-Item $stderr -Force }
        $gradlew = Join-Path $Project 'gradlew.bat'
        $proc = Start-Process -FilePath $gradlew `
            -ArgumentList (':' + $Node + ':runServer'), '--console=plain' `
            -WorkingDirectory $Project `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
            -WindowStyle Hidden -PassThru
        Set-Content -Path (Join-Path $OutDir ($Session + '.pid')) -Value $proc.Id
        Write-Host ("START-LAUNCHED pid=" + $proc.Id)
    }

    'wait-done' {
        $stdout = Join-Path $OutDir ($Session + '-stdout.log')
        $stderr = Join-Path $OutDir ($Session + '-stderr.log')
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
        while ([DateTime]::UtcNow -lt $deadline) {
            if (Test-Path $stdout) {
                $hit = Select-String -Path $stdout -Pattern 'Done \(' -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($hit) { Write-Host ("START-DONE " + $hit.Line.Trim()); exit 0 }
            }
            $fail = $null
            if ((Test-Path $stdout) -and (Test-Path $stderr)) {
                $fail = Select-String -Path $stdout, $stderr -Pattern 'BUILD FAILED|Exception in thread "main"|Fatal error|DirectoryLock' -ErrorAction SilentlyContinue | Select-Object -First 1
            }
            if ($fail) { Write-Host ("START-FAILED " + $fail.Path + ':' + $fail.LineNumber + ' ' + $fail.Line.Trim()); exit 1 }
            $procId = Get-PidFromSession
            if ($procId -ne $null -and -not (Test-ProcessAlive $procId)) {
                Write-Host 'START-FAILED client-exited-without-done-marker'; exit 1
            }
            Start-Sleep -Seconds 2
        }
        Write-Host "START-TIMEOUT after ${TimeoutSec}s"
        exit 2
    }

    'rcon' {
        Ensure-OutDirs
        if ($Commands.Count -eq 0) { throw 'no commands given' }
        $log = Join-Path $OutDir ('rcon-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
        $out = & python (Join-Path $ScriptDir 'rcon.py') $RconPort $RconPassword '60' @Commands 2>&1
        $code = $LASTEXITCODE
        $out | ForEach-Object { "$_" } | Tee-Object -FilePath $log | ForEach-Object { Write-Host $_ }
        Write-Host ("RCON-EXIT " + $code)
        exit $code
    }

    'stop' {
        $log = Join-Path $OutDir ('rcon-stop-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
        try {
            $out = & python (Join-Path $ScriptDir 'rcon.py') $RconPort $RconPassword '30' 'stop' 2>&1
            $out | ForEach-Object { "$_" } | Set-Content $log
            Write-Host 'STOP-SENT'
        } catch {
            Write-Host 'STOP-SEND-FAILED (server may already be down)'
        }
        $procId = Get-PidFromSession
        if ($procId -ne $null) {
            $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
            while ([DateTime]::UtcNow -lt $deadline) {
                if (-not (Test-ProcessAlive $procId)) { Write-Host 'CLIENT-EXITED'; break }
                Start-Sleep -Seconds 1
            }
        }
        Wait-WorldLockRelease 120
        Write-Host 'STOP-COMPLETE'
    }

    'snapshot' {
        Ensure-OutDirs
        if ($Name -eq '') { throw 'snapshot requires -Name' }
        $paths = New-Object System.Collections.Generic.List[object]
        foreach ($rel in @('nekojs\config\engine.toml', 'nekojs\config\probe.toml', 'nekojs\config\trusted-servers.json',
                           'nekojs\README.txt', 'jsconfig.json')) {
            $p = Join-Path $RunDir $rel
            if (Test-Path $p) { $paths.Add((Get-Item $p)) }
        }
        foreach ($dir in @('nekojs\packs', 'nekojs\server_scripts', 'nekojs\startup_scripts', 'nekojs\client_scripts',
                           'world\nekojs_packs')) {
            $full = Join-Path $RunDir $dir
            if (Test-Path $full) {
                $paths.AddRange(@(Get-ChildItem -Path $full -Recurse -File -ErrorAction SilentlyContinue))
            }
        }
        $lines = @()
        foreach ($item in $paths) {
            $hash = Get-FileHash -Path $item.FullName -Algorithm SHA256
            $rel = $item.FullName.Substring($RunDir.Length).TrimStart('\') -replace '\\', '/'
            $lines += ($hash.Hash + '|' + $item.Length + '|' + $item.LastWriteTimeUtc.ToString('o') + '|' + $rel)
        }
        $lines = $lines | Sort-Object
        $lines | Set-Content (Join-Path $SnapDir ($Name + '.txt')) -Encoding Ascii
        Write-Host ("SNAPSHOT-OK " + $Name + " entries=" + $lines.Count)
    }

    'compare-snapshot' {
        if ($Before -eq '' -or $After -eq '') { throw 'compare-snapshot requires -Before and -After' }
        $a = @{}
        Get-Content (Join-Path $SnapDir ($Before + '.txt')) | ForEach-Object {
            $parts = $_.Split('|', 4); if ($parts.Count -eq 4) { $a[$parts[3]] = $parts[0] + '|' + $parts[1] }
        }
        $b = @{}
        Get-Content (Join-Path $SnapDir ($After + '.txt')) | ForEach-Object {
            $parts = $_.Split('|', 4); if ($parts.Count -eq 4) { $b[$parts[3]] = $parts[0] + '|' + $parts[1] }
        }
        $changed = @(); $added = @(); $removed = @(); $same = 0
        foreach ($k in $b.Keys) {
            if (-not $a.ContainsKey($k)) { $added += $k }
            elseif ($a[$k] -ne $b[$k]) { $changed += $k }
            else { $same++ }
        }
        foreach ($k in $a.Keys) { if (-not $b.ContainsKey($k)) { $removed += $k } }
        foreach ($k in ($changed | Sort-Object)) { Write-Host ("CHANGED " + $k) }
        foreach ($k in ($added | Sort-Object)) { Write-Host ("ADDED " + $k) }
        foreach ($k in ($removed | Sort-Object)) { Write-Host ("REMOVED " + $k) }
        Write-Host ("DIFF-SUMMARY changed=" + $changed.Count + " added=" + $added.Count + " removed=" + $removed.Count + " same=" + $same)
    }

    'break' {
        $target = Join-Path $RunDir 'nekojs\server_scripts\zz-broken.js'
        $lines = @('// datafix03 intentional syntax error', 'const x = {;')
        Set-Content -Path $target -Value $lines -Encoding Ascii
        Write-Host 'BREAK-OK'
    }

    'restore' {
        $target = Join-Path $RunDir 'nekojs\server_scripts\zz-broken.js'
        if (Test-Path $target) { Remove-Item $target -Force }
        Write-Host 'RESTORE-OK'
    }

    'summon-target' {
        Ensure-OutDirs
        $log = Join-Path $OutDir ('rcon-summon-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
        $cmds = @(
            'forceload add 0 0',
            'summon minecraft:armor_stand 0.5 200.0 0.5 {NoGravity:1b,Invulnerable:1b,Tags:["nekojs_pdata_target"]}'
        )
        $out = & python (Join-Path $ScriptDir 'rcon.py') $RconPort $RconPassword '60' @cmds 2>&1
        $code = $LASTEXITCODE
        $out | ForEach-Object { "$_" } | Tee-Object -FilePath $log | ForEach-Object { Write-Host $_ }
        Write-Host ("RCON-EXIT " + $code)
    }

    'query-pdata' {
        Ensure-OutDirs
        $log = Join-Path $OutDir ('rcon-pdata-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
        $sel = '@e[type=minecraft:armor_stand,tag=nekojs_pdata_target,limit=1]'
        $cmds = @(
            'data get entity ' + $sel + ' NeoForgeData',
            'data get entity ' + $sel + ' NeoForgeData.NekoJSPersistentData'
        )
        $out = & python (Join-Path $ScriptDir 'rcon.py') $RconPort $RconPassword '60' @cmds 2>&1
        $code = $LASTEXITCODE
        $out | ForEach-Object { "$_" } | Tee-Object -FilePath $log | ForEach-Object { Write-Host $_ }
        Write-Host ("RCON-EXIT " + $code)
    }

    'probe-clear' {
        $probeDir = Join-Path $RunDir '.neko_probe'
        Remove-TreeWithRetry $probeDir
        Write-Host 'PROBE-CLEAR-OK'
    }

    'find' {
        if ($Pattern -eq '') { throw 'find requires -Pattern' }
        Ensure-OutDirs
        $latest = Join-Path $RunDir 'logs\latest.log'
        $files = @()
        foreach ($f in @($latest,
                         (Join-Path $OutDir ($Session + '-stdout.log')),
                         (Join-Path $OutDir ($Session + '-stderr.log')))) {
            if (Test-Path $f) { $files += $f }
        }
        $log = Join-Path $OutDir ('find-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
        $hits = Select-String -Path $files -Pattern $Pattern -ErrorAction SilentlyContinue
        $out = @($hits | ForEach-Object { ($_.Path + ':' + $_.LineNumber + ': ' + $_.Line) })
        $out | Tee-Object -FilePath $log | ForEach-Object { Write-Host $_ }
        Write-Host ("FIND-COUNT " + $out.Count)
    }

    default {
        throw "unknown step: $Step"
    }
}
