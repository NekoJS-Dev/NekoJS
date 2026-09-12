# NekoJS ticket 02 perf sampler (bench/perf/sample.ps1)
#
# 固定采样入口。所有会话使用同一份数据集（fixtures/nekojs），run 目录 = versions/<node>/run
# （Gradle MDG 默认 game dir；bench/perf/run/<node> 是指向它的目录联接别名）。
#
# 用法（Git Bash 下，Windows PowerShell 5.1 兼容）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode deploy
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode startup -Warmup 2 -Samples 5
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode bench   -Rounds 3
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode reload  -Reloads 5
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/perf/sample.ps1 -Mode probe   -ProbeSamples 5
#
# 命令通道口径（固定）：计时类命令（nekojs reload / nekojs probe）统一走 RCON
# （server.properties 预置 enable-rcon，Source RCON 直连服务端，与 gradle 是否转发 stdin 无关）；
# 时间口径 = 命令发送时刻 -> stdout 日志出现完成 marker。stdin（gradlew 进程标准输入）只在
# 首个 startup 会话的停服时做一次可行性试验（工单 D1 要求），结果作为通道事实记录。
# 停服通道：stdin 可用则用 stdin，否则 RCON；两者都失败才 taskkill 强杀并记录。
#
# 输出：bench/perf/out/<UTC 时间戳>-<mode>-<node>/（samples.jsonl + 每会话 stdout/stderr 原文 + CSV 副本）。

param(
    [string]$Mode = "startup",
    [string]$Node = "26.1.2",
    [int]$Warmup = 2,
    [int]$Samples = 5,
    [int]$Rounds = 3,
    [int]$Reloads = 5,
    [int]$ProbeSamples = 5,
    [int]$TickWindowRows = 1200,
    [int]$TickWindowMaxSec = 240,
    [string]$GradleUserHome = "D:\mcmodDemo\NekoJS\.gradle-perf02",
    [int]$ServerPort = 25871,
    [int]$RconPort = 25872,
    [string]$RconPass = "perf02"
)

$ErrorActionPreference = "Stop"
$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RunDir = Join-Path $ProjectDir "versions\$Node\run"
$FixtureDir = Join-Path $PSScriptRoot "fixtures\nekojs"
$OutRoot = Join-Path $PSScriptRoot "out"
$Stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$OutDir = Join-Path $OutRoot "$Stamp-$Mode-$Node"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$GitRev = (& git -C $ProjectDir rev-parse HEAD).Trim().Substring(0, 8)
# 脚本侧写文件被沙盒限制在 nekojs/ 根内（allowFsWriteOutsideNekojs 默认 false），
# 故 perf-out 位于 run/nekojs/perf-out
$perfOut = Join-Path $RunDir "nekojs\perf-out"
# probe 生成物目录（清空以强制每个样本走完整生成路径；见 probe 模式说明）
$probeOutDir = Join-Path $RunDir ".neko_probe"
$script:StdinOk = $null   # $null=未测；$true/$false=stdin 转发实测（进程级缓存，逐会话记录）

function Write-Sample([hashtable]$obj) {
    $obj.rev = $GitRev
    $obj.node = $Node
    $obj.ts_utc = (Get-Date).ToUniversalTime().ToString("o")
    $parts = @()
    foreach ($k in $obj.Keys) {
        $v = $obj[$k]
        if ($null -eq $v) { $parts += ('"{0}":null' -f $k) }
        elseif ($v -is [bool]) { $parts += ('"{0}":{1}' -f $k, ($v.ToString().ToLower())) }
        elseif ($v -is [string]) {
            $s = $v.Replace("\", "\\").Replace('"', '\"').Replace("`r", " ").Replace("`n", " ")
            $parts += ('"{0}":"{1}"' -f $k, $s)
        }
        else { $parts += ('"{0}":{1}' -f $k, $v) }
    }
    Add-Content -Path (Join-Path $OutDir "samples.jsonl") -Value ("{" + ($parts -join ",") + "}")
    Write-Host ("[sample] " + ($parts -join ","))
}

function Write-EnvSnapshot {
    $p = Join-Path $OutDir "env-snapshot.txt"
    "utc: $((Get-Date).ToUniversalTime().ToString('o'))" | Out-File $p -Encoding utf8
    "git rev: $GitRev" | Out-File $p -Append -Encoding utf8
    "gradle user home: $GradleUserHome" | Out-File $p -Append -Encoding utf8
    $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
    "cpu: $($cpu.Name) load=$($cpu.LoadPercentage)%" | Out-File $p -Append -Encoding utf8
    $os = Get-CimInstance Win32_OperatingSystem
    "mem free GB: $([math]::Round($os.FreePhysicalMemory / 1MB, 1))" | Out-File $p -Append -Encoding utf8
    "java processes: $((Get-Process java -ErrorAction SilentlyContinue | Measure-Object).Count)" | Out-File $p -Append -Encoding utf8
}

# ---------- run 目录铺设（固定数据集，可复现、不累积） ----------
function Deploy-Fixtures {
    param([bool]$Bench)
    $nekoDir = Join-Path $RunDir "nekojs"
    if (Test-Path $nekoDir) {
        Remove-Item -Recurse -Force $nekoDir -ErrorAction SilentlyContinue
        # delete-gone wait: a just-stopped server's handles can outlive Remove-Item on
        # Windows; without this the copy below hits a half-deleted tree (shakedown r3).
        $goneDeadline = [DateTime]::UtcNow.AddSeconds(30)
        while ((Test-Path $nekoDir) -and [DateTime]::UtcNow -lt $goneDeadline) {
            Start-Sleep -Milliseconds 500
            Remove-Item -Recurse -Force $nekoDir -ErrorAction SilentlyContinue
        }
        if (Test-Path $nekoDir) { throw "run nekojs dir still locked after 30s: $nekoDir" }
    }
    # 先建空 nekojs/ 再复制内容：v1 先建了 nekojs\perf-out，导致 Copy-Item 把整个
    # fixtures 目录嵌套成 run\nekojs\nekojs\...，数据集一个都没被发现（shakedown 轮证据）。
    New-Item -ItemType Directory -Force -Path $nekoDir | Out-Null
    # robocopy, not Copy-Item: PS 5.1 Copy-Item with a wildcard source + -Recurse
    # hits CopyContainerItemToLeafError once the fixture tree contains nested dirs
    # (server_scripts/src); robocopy /E has no such quirk. Exit codes 0-7 = success.
    & robocopy $FixtureDir $nekoDir /E /NJH /NJS /NDL /NFL /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy fixtures failed with exit $LASTEXITCODE" }
    New-Item -ItemType Directory -Force -Path $perfOut | Out-Null
    # eula / server.properties：固定端口与 rcon 预置；world 复用（warmup 期建立，之后复用以降方差）
    Set-Content -Path (Join-Path $RunDir "eula.txt") -Value "eula=true" -Encoding ascii
    $sp = Join-Path $RunDir "server.properties"
    if (-not (Test-Path $sp)) {
        $props = "server-port=$ServerPort`nmotd=perf02 baseline`nonline-mode=false`ndifficulty=peaceful`ngamemode=survival`nenable-rcon=true`nrcon.port=$RconPort`nrcon.password=$RconPass`nspawn-protection=0`nview-distance=8`npause-when-empty-seconds=-1"
        Set-Content -Path $sp -Value $props -Encoding ascii
    }
    if ($Bench) { Set-Content -Path (Join-Path $perfOut "RUN_BENCH") -Value "bench" -Encoding ascii }
    Write-Host "[deploy] run dir ready (bench=$Bench): $RunDir"
}

# ---------- RCON (timed-command and fallback stop channel) ----------
# Fixed channel: bench/perf/rcon.py. The v1 pure-PowerShell framing got the
# connection reset by the vanilla RCON thread on auth; verified with a python
# client on 2026-09-12 and switched over (see baseline report channel notes).
function Invoke-Rcon {
    param([string]$Command, [int]$TimeoutMs = 180000)
    $out = & python (Join-Path $PSScriptRoot "rcon.py") $RconPort $RconPass $Command 2>&1
    if ($LASTEXITCODE -ne 0) { throw "rcon failed: $out" }
    return ($out -join "`n")
}

# ---------- 停服（含工单 D1 的 stdin 一次性试验） ----------
function Stop-Server($proc) {
    if ($script:StdinOk -ne $false) {
        try {
            $proc.StandardInput.WriteLine("stop")
            $proc.StandardInput.Flush()
        } catch {}
        if ($proc.WaitForExit(60000)) { $script:StdinOk = $true; Wait-WorldLockRelease; return @{ channel = "stdin"; killed = $false } }
        $script:StdinOk = $false   # stdin 试验失败：本进程后续全部走 RCON
    }
    try {
        Invoke-Rcon -Command "stop" -TimeoutMs 30000 | Out-Null
        if ($proc.WaitForExit(90000)) { Wait-WorldLockRelease; return @{ channel = "rcon"; killed = $false } }
    } catch {}
    # 双通道失败：强杀进程树，记录（gradlew(cmd) 客户端树；残留 daemon 由 gradle 自身管理）
    try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
    if (-not $proc.WaitForExit(30000)) { try { $proc.Kill() } catch {} }
    Wait-WorldLockRelease
    return @{ channel = "taskkill"; killed = $true }
}

# Wait for world/session.lock release after stop: MC DirectoryLock holds the file
# lock until the server JVM exits, which can lag the gradle client exit; without
# this wait the next session's server dies on DirectoryLock.create contention.
function Wait-WorldLockRelease {
    param([int]$TimeoutSec = 60)
    $lockFile = Join-Path $RunDir "world\session.lock"
    if (-not (Test-Path $lockFile)) { return }
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $fs = [System.IO.File]::Open($lockFile, 'Open', 'ReadWrite', 'None')
            $fs.Close()
            return
        } catch { Start-Sleep -Milliseconds 500 }
    }
    Write-Host "[warn] session.lock still held after ${TimeoutSec}s"
}

# ---------- 计时类命令（固定口径：RCON 发送 -> 日志 marker） ----------
function Send-TimedCommand {
    param($proc, [string]$Command, [string]$MarkerPattern, [string]$outLog, [int]$TimeoutSec = 300)
    $before = @(Select-String -Path $outLog -Pattern $MarkerPattern -ErrorAction SilentlyContinue).Count
    $t0 = [System.Diagnostics.Stopwatch]::StartNew()
    $rconText = $null
    try { $rconText = Invoke-Rcon -Command $Command } catch { $rconText = "RCON-ERROR: $($_.Exception.Message)" }
    $rconRttMs = $t0.ElapsedMilliseconds
    $markerMs = $null
    $hitText = $null
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($proc.HasExited) { break }
        $all = @(Select-String -Path $outLog -Pattern $MarkerPattern -ErrorAction SilentlyContinue)
        if ($all.Count -gt $before) {
            $markerMs = $t0.ElapsedMilliseconds
            $hitText = $all[$all.Count - 1].Line
            break
        }
        Start-Sleep -Milliseconds 100
    }
    return @{ rconRttMs = $rconRttMs; rconText = $rconText; markerMs = $markerMs; markerLine = $hitText }
}

function Count-CsvRows([string]$path) {
    if (-not (Test-Path $path)) { return 0 }
    return (Get-Content $path -ErrorAction SilentlyContinue | Measure-Object -Line).Lines
}

function Invoke-ServerSession {
    param([string]$Tag, [scriptblock]$OnDone)
    $stdoutLog = Join-Path $OutDir "$Tag-stdout.log"
    $stderrLog = Join-Path $OutDir "$Tag-stderr.log"
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c gradlew.bat :$Node`:runServer --console=plain > `"$stdoutLog`" 2> `"$stderrLog`""
    $psi.WorkingDirectory = $ProjectDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.EnvironmentVariables["GRADLE_USER_HOME"] = $GradleUserHome
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $proc = [System.Diagnostics.Process]::Start($psi)
    Write-Host "[session] $Tag started pid=$($proc.Id)"

    $doneS = $null; $doneWallMs = $null; $extra = @{}
    $deadline = [DateTime]::UtcNow.AddSeconds(1500)
    $forcedKill = $false
    while ($true) {
        if ($proc.HasExited) { break }
        if ([DateTime]::UtcNow -gt $deadline) {
            Write-Host "[session] $Tag global timeout, killing"
            try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
            $extra.timeout = $true
            $forcedKill = $true
            break
        }
        if ($null -eq $doneS) {
            $doneLine = Get-Content $stdoutLog -Tail 60 -ErrorAction SilentlyContinue |
                Select-String -Pattern 'Done \((\d+\.\d+)s\)!' | Select-Object -Last 1
            if ($null -ne $doneLine) {
                $doneS = $doneLine.Matches[0].Groups[1].Value
                $doneWallMs = $sw.ElapsedMilliseconds
                Write-Host "[session] $Tag Done($doneS) wall=${doneWallMs}ms"
                if ($null -ne $OnDone) { $extra = (& $OnDone $proc $stdoutLog $sw) }
            }
        } else {
            if ($proc.WaitForExit(500)) { break }
            if ($extra.stopSent) {
                if ($sw.ElapsedMilliseconds -gt ($doneWallMs + 300000)) {
                    try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
                    $forcedKill = $true
                    break
                }
            }
        }
        Start-Sleep -Milliseconds 200
    }
    try { if ($proc.WaitForExit(5000)) { } } catch {}
    return @{ doneS = $doneS; doneWallMs = $doneWallMs; wallTotalMs = $sw.ElapsedMilliseconds; extra = $extra; pid = $proc.Id; forcedKill = $forcedKill }
}

# ---------- Mode: deploy ----------
if ($Mode -eq "deploy") {
    Deploy-Fixtures -Bench:$false
    exit 0
}

# ---------- Mode: startup ----------
if ($Mode -eq "startup") {
    Write-EnvSnapshot
    $total = $Warmup + $Samples
    for ($i = 1; $i -le $total; $i++) {
        $kind = "formal"
        if ($i -le $Warmup) { $kind = "warmup" }
        Deploy-Fixtures -Bench:$false
        $tag = "$Mode-$i-$kind"
        $r = Invoke-ServerSession -Tag $tag -OnDone {
            param($proc, $outLog, $sw)
            $stop = Stop-Server $proc
            @{ stopSent = $true; stopChannel = $stop.channel; killed = $stop.killed }
        }
        Write-Sample @{
            mode = "startup"; index = $i; kind = $kind; tag = $tag
            wall_done_ms = $r.doneWallMs; done_s = $r.doneS; wall_total_ms = $r.wallTotalMs
            pid = $r.pid; forced_kill = $r.forcedKill
            stop_channel = $r.extra.stopChannel; killed = $r.extra.killed; timeout = $r.extra.timeout
        }
    }
    Write-Host "[done] stdin-ok=$script:StdinOk -> $OutDir"
    Add-Content -Path (Join-Path $OutDir "channel-test.txt") -Value "stdin forwarded to server: $script:StdinOk"
    exit 0
}

# ---------- Mode: bench（tick / adapter / eval / mem，每轮重启服务器） ----------
if ($Mode -eq "bench") {
    Write-EnvSnapshot
    for ($i = 1; $i -le $Rounds; $i++) {
        Deploy-Fixtures -Bench:$true
        $tag = "$Mode-$i"
        $r = Invoke-ServerSession -Tag $tag -OnDone {
            param($proc, $outLog, $sw)
            $stopAt = [DateTime]::UtcNow.AddSeconds($TickWindowMaxSec)
            while ([DateTime]::UtcNow -lt $stopAt) {
                if ((Count-CsvRows (Join-Path $perfOut "tick-samples.csv")) -ge $TickWindowRows) { break }
                Start-Sleep -Seconds 2
            }
            $rows = Count-CsvRows (Join-Path $perfOut "tick-samples.csv")
            $stop = Stop-Server $proc
            @{ stopSent = $true; stopChannel = $stop.channel; killed = $stop.killed; tickRows = $rows }
        }
        $csvDir = Join-Path $OutDir "round-$i-csv"
        New-Item -ItemType Directory -Force -Path $csvDir | Out-Null
        foreach ($f in @("tick-samples.csv", "adapter-samples.csv", "eval-samples.csv", "mem-samples.csv")) {
            $src = Join-Path $perfOut $f
            if (Test-Path $src) { Copy-Item $src (Join-Path $csvDir $f) }
            else { Set-Content -Path (Join-Path $csvDir "$f.missing") -Value "MISSING" -Encoding ascii }
        }
        Write-Sample @{
            mode = "bench"; index = $i; kind = "formal"; tag = $tag
            wall_done_ms = $r.doneWallMs; done_s = $r.doneS; wall_total_ms = $r.wallTotalMs
            pid = $r.pid; forced_kill = $r.forcedKill
            stop_channel = $r.extra.stopChannel; killed = $r.extra.killed; timeout = $r.extra.timeout
            tick_rows = $r.extra.tickRows
        }
    }
    Write-Host "[done] bench rounds -> $OutDir"
    exit 0
}

# ---------- Mode: reload（独立会话：一次启动，连发 Reloads 次 nekojs reload） ----------
# 计时口径（两列并记）：
#   1) rcon_rtt_ms —— RCON 发送到响应返回的往返（命令在服务端线程执行，响应文本
#      "NekoJS server scripts reloaded. (N error(s) remain)" 即完成回执）；
#   2) marker_ms —— RCON 发送到 stdout 出现完成代理 marker 的墙钟差。代理 marker 用
#      ASCII 的最后一个 server 脚本 load 行（fixture 字典序最后一位是 tick-bench.js），
#      避免 PS 5.1 对 .ps1 里中文字面量按 ANSI 解码、以及 mod 完成行异步 Log-Flusher
#      刷盘带来的匹配不确定性（2026-09-12 shakedown 实测：中文 marker 在 PS 侧恒为 null）。
# stdout 增量按 UTF-8 显式解码读取，避免 PS 默认 ANSI 解码把非 ASCII 行读坏。
$script:ReloadMarker = 'PERF02-SERVER tick-bench load'
function Read-LogTailUtf8([string]$outLog, [ref]$offsetRef) {
    $fi = Get-Item $outLog -ErrorAction SilentlyContinue
    if ($null -eq $fi -or $fi.Length -le $offsetRef.Value) { return "" }
    $fs = [System.IO.File]::Open($outLog, 'Open', 'Read', 'ReadWrite')
    try {
        [void]$fs.Seek($offsetRef.Value, 'Begin')
        $buf = New-Object byte[] ($fi.Length - $offsetRef.Value)
        [void]$fs.Read($buf, 0, $buf.Length)
        $offsetRef.Value = $fi.Length
        return [System.Text.Encoding]::UTF8.GetString($buf)
    } finally { $fs.Close() }
}
if ($Mode -eq "reload") {
    Write-EnvSnapshot
    Deploy-Fixtures -Bench:$false
    $tag = "$Mode-session"
    $r = Invoke-ServerSession -Tag $tag -OnDone {
        param($proc, $outLog, $sw)
        $offset = (Get-Item $outLog).Length
        $results = @()
        for ($k = 1; $k -le $Reloads; $k++) {
            Start-Sleep -Seconds 3
            $before = ([regex]::Matches((Read-LogTailUtf8 $outLog ([ref]$offset)), [regex]::Escape($script:ReloadMarker))).Count
            $t0 = [System.Diagnostics.Stopwatch]::StartNew()
            $rconText = $null
            try { $rconText = Invoke-Rcon -Command "nekojs reload" } catch { $rconText = "RCON-ERROR: $($_.Exception.Message)" }
            $rconRttMs = $t0.ElapsedMilliseconds
            $markerMs = $null; $markerLine = $null
            $deadline = [DateTime]::UtcNow.AddSeconds(300)
            while ([DateTime]::UtcNow -lt $deadline) {
                if ($proc.HasExited) { break }
                $chunk = Read-LogTailUtf8 $outLog ([ref]$offset)
                if ($chunk.Length -gt 0) {
                    $all = ([regex]::Matches($chunk, [regex]::Escape($script:ReloadMarker))).Count
                    if ($all -gt 0) {
                        $markerMs = $t0.ElapsedMilliseconds
                        $markerLine = (($chunk -split "`n") | Where-Object { $_.Contains($script:ReloadMarker) } | Select-Object -Last 1)
                        break
                    }
                }
                Start-Sleep -Milliseconds 100
            }
            $results += @{ k = $k; markerMs = $markerMs; rconRttMs = $rconRttMs; markerLine = $markerLine; rconText = $rconText }
            Write-Host "[reload] #$k marker=$markerMs ms rcon=$rconRttMs ms resp=$rconText"
        }
        $stop = Stop-Server $proc
        @{ stopSent = $true; stopChannel = $stop.channel; killed = $stop.killed; reloads = $results }
    }
    $n = 0
    foreach ($rr in $r.extra.reloads) {
        $n++
        Write-Sample @{
            mode = "reload"; index = $n; kind = "formal"; tag = $tag
            wall_done_ms = $r.doneWallMs; done_s = $r.doneS; wall_total_ms = $r.wallTotalMs
            pid = $r.pid; forced_kill = $r.forcedKill
            marker_ms = $rr.markerMs; rcon_rtt_ms = $rr.rconRttMs
            marker_line = $rr.markerLine; rcon_response = $rr.rconText
            stop_channel = $r.extra.stopChannel; killed = $r.extra.killed
        }
    }
    Write-Host "[done] reload sampling -> $OutDir"
    exit 0
}

# ---------- Mode: probe（独立会话：1 warmup + N formal 的 nekojs probe） ----------
# 完成 marker 实测格式（2026-09-12）：`Probe [typescript] generated 389 files in 620ms
# (389 written, 0 unchanged, 0 stale removed)`。注意 probe 有增量快路径：不清输出目录时
# 后续样本只走 unchanged/no-op 分支（实测 0 written / 217ms），因此每个样本前清空
# run/.neko_probe，使每个样本都量到完整生成路径；清理是否成功记入样本（deleted 字段）。
if ($Mode -eq "probe") {
    Write-EnvSnapshot
    Deploy-Fixtures -Bench:$false
    $tag = "$Mode-session"
    $r = Invoke-ServerSession -Tag $tag -OnDone {
        param($proc, $outLog, $sw)
        $results = @()
        $total = $ProbeSamples + 1
        for ($k = 1; $k -le $total; $k++) {
            $kind = "formal"
            if ($k -eq 1) { $kind = "warmup" }
            $deleted = $false
            try {
                if (Test-Path $probeOutDir) { Remove-Item -Recurse -Force $probeOutDir -ErrorAction Stop }
                $deleted = $true
            } catch { $deleted = $false }
            Start-Sleep -Seconds 3
            $t = Send-TimedCommand $proc "nekojs probe" 'Probe \[typescript\] generated (\d+) files in (\d+)ms' $outLog 300
            $files = $null; $selfMs = $null
            if ($t.markerLine -match 'generated (\d+) files in (\d+)ms') {
                $files = $Matches[1]; $selfMs = $Matches[2]
            }
            $results += @{ k = $k; kind = $kind; markerMs = $t.markerMs; rconRttMs = $t.rconRttMs; files = $files; selfMs = $selfMs; deleted = $deleted; markerLine = $t.markerLine }
            Write-Host "[probe] #$k($kind) deleted=$deleted files=$files self=${selfMs}ms marker=$($t.markerMs)ms"
        }
        $stop = Stop-Server $proc
        @{ stopSent = $true; stopChannel = $stop.channel; killed = $stop.killed; probes = $results }
    }
    foreach ($pr in $r.extra.probes) {
        Write-Sample @{
            mode = "probe"; index = $pr.k; kind = $pr.kind; tag = $tag
            wall_done_ms = $r.doneWallMs; done_s = $r.doneS; wall_total_ms = $r.wallTotalMs
            pid = $r.pid; forced_kill = $r.forcedKill
            marker_ms = $pr.markerMs; rcon_rtt_ms = $pr.rconRttMs
            probe_files = $pr.files; probe_self_ms = $pr.selfMs
            probe_deleted = $pr.deleted
            marker_line = $pr.markerLine
            stop_channel = $r.extra.stopChannel; killed = $r.extra.killed
        }
    }
    Write-Host "[done] probe sampling -> $OutDir"
    exit 0
}

Write-Host "Unknown mode: $Mode"
exit 1
