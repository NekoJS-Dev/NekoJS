# NekoJS ticket 05 smoke runner (bench/smoke/run-smoke.ps1)
#
# 单 owner 运行时（NekoRuntimeAssembly / 窄句柄注入）行为烟测：启动 → server started →
# RCON `nekojs reload server` → marker 计数 → stop。输出全部进 bench/smoke/out/<stamp>-<node>/。
#
# 用法（Git Bash 下，Windows PowerShell 5.1 兼容）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/smoke/run-smoke.ps1 -Node 26.1.2
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/smoke/run-smoke.ps1 -Node 26.1.2-fabric
#
# 通道口径复用 ticket 02 的结论（bench/perf/sample.ps1）：命令与停服统一走 RCON
# （stdin 在 gradlew 下不可达）；rcon.py 为已验证的 Source-RCON 客户端。
# fabric 节点 runServer 的 run dir 是 run-server（loom runs.named("server").runDir），
# NeoForge 节点是 run（MDG 默认）——脚本按节点选择。

param(
    [Parameter(Mandatory = $true)][string]$Node,
    [int]$ServerPort = 25881,
    [int]$RconPort = 25882,
    [string]$RconPass = "smoke05",
    [int]$BootTimeoutSec = 900
)

$ErrorActionPreference = "Stop"
$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$FixtureDir = Join-Path $PSScriptRoot "fixtures\nekojs"
$Stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$OutDir = Join-Path $PSScriptRoot "out\$Stamp-$Node"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

switch ($Node) {
    "26.1.2"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run" }
    "26.2.0"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run" }
    "1.21.1"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run" }
    "26.1.2-fabric" { $RunDir = Join-Path $ProjectDir "versions\$Node\run-server" }
    "26.2.0-fabric" { $RunDir = Join-Path $ProjectDir "versions\$Node\run-server" }
    default { throw "unsupported node '$Node'" }
}

$GitRev = (& git -C $ProjectDir rev-parse HEAD).Trim().Substring(0, 8)
"utc: $((Get-Date).ToUniversalTime().ToString('o'))`ngit rev: $GitRev`nnode: $Node`nrun dir: $RunDir" |
    Out-File (Join-Path $OutDir "env.txt") -Encoding utf8
Write-Host "[smoke] node=$Node rev=$GitRev run=$RunDir out=$OutDir"

# ---------- run 目录铺设（最小数据集：marker 脚本 + rcon 预置） ----------
$nekoDir = Join-Path $RunDir "nekojs"
if (Test-Path $nekoDir) {
    Remove-Item -Recurse -Force $nekoDir -ErrorAction SilentlyContinue
    $goneDeadline = [DateTime]::UtcNow.AddSeconds(30)
    while ((Test-Path $nekoDir) -and [DateTime]::UtcNow -lt $goneDeadline) {
        Start-Sleep -Milliseconds 500
        Remove-Item -Recurse -Force $nekoDir -ErrorAction SilentlyContinue
    }
    if (Test-Path $nekoDir) { throw "run nekojs dir still locked after 30s: $nekoDir" }
}
New-Item -ItemType Directory -Force -Path $nekoDir | Out-Null
& robocopy $FixtureDir $nekoDir /E /NJH /NJS /NDL /NFL /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy fixtures failed with exit $LASTEXITCODE" }
# per-type 脚本日志（console.info / "发现了" 的落点是 logs/nekojs/<type>.log，不是 stdout）：
# 每会话清空，保证 marker 计数从零开始
$nekoLogDir = Join-Path $RunDir "logs\nekojs"
if (Test-Path $nekoLogDir) { Remove-Item -Recurse -Force $nekoLogDir -ErrorAction SilentlyContinue }
Set-Content -Path (Join-Path $RunDir "eula.txt") -Value "eula=true" -Encoding ascii
$props = "server-port=$ServerPort`nmotd=nekojs smoke05`nonline-mode=false`ndifficulty=peaceful`ngamemode=survival`nenable-rcon=true`nrcon.port=$RconPort`nrcon.password=$RconPass`nspawn-protection=0`nview-distance=8`npause-when-empty-seconds=-1"
Set-Content -Path (Join-Path $RunDir "server.properties") -Value $props -Encoding ascii
Write-Host "[deploy] fixtures + rcon ready"

function Invoke-Rcon {
    param([string]$Command, [int]$TimeoutMs = 120000)
    $out = & python (Join-Path $PSScriptRoot "rcon.py") $RconPort $RconPass ([string]($TimeoutMs / 1000)) $Command 2>&1
    if ($LASTEXITCODE -ne 0) { throw "rcon failed: $out" }
    return ($out -join "`n")
}

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

function Stop-Server($proc) {
    try {
        Invoke-Rcon -Command "stop" -TimeoutMs 30000 | Out-Null
        if ($proc.WaitForExit(90000)) { Wait-WorldLockRelease; return "rcon" }
    } catch {}
    try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
    if (-not $proc.WaitForExit(30000)) { try { $proc.Kill() } catch {} }
    Wait-WorldLockRelease
    return "taskkill"
}

# ---------- 启动 ----------
$stdoutLog = Join-Path $OutDir "server-stdout.log"
$stderrLog = Join-Path $OutDir "server-stderr.log"
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c gradlew.bat :$Node`:runServer --console=plain > `"$stdoutLog`" 2> `"$stderrLog`""
$psi.WorkingDirectory = $ProjectDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$proc = [System.Diagnostics.Process]::Start($psi)
Write-Host "[session] started pid=$($proc.Id)"

$doneS = $null
$deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSec)
while ($true) {
    if ($proc.HasExited) { break }
    if ([DateTime]::UtcNow -gt $deadline) {
        Write-Host "[session] boot timeout, killing"
        try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
        break
    }
    $doneLine = Get-Content $stdoutLog -Tail 60 -ErrorAction SilentlyContinue |
        Select-String -Pattern 'Done \((\d+\.\d+)s\)!' | Select-Object -Last 1
    if ($null -ne $doneLine) { $doneS = $doneLine.Matches[0].Groups[1].Value; break }
    Start-Sleep -Milliseconds 300
}
if ($null -eq $doneS) { throw "server did not reach 'Done' (see $stdoutLog)" }
Write-Host "[session] server started (Done($doneS)s, wall=$($sw.ElapsedMilliseconds)ms)"

# ---------- reload 前计数 ----------
# console.info 与「发现了 N 个 <TYPE> 脚本」落 logs/nekojs/<type>.log；bootstrap/平台 marker 落 stdout
function Count-LogMarker([string]$LogFile, [string]$Pattern) {
    $p = Join-Path $nekoLogDir $LogFile
    if (-not (Test-Path $p)) { return 0 }
    return @(Select-String -Path $p -Pattern $Pattern -ErrorAction SilentlyContinue).Count
}
function Count-StdoutMarker([string]$Pattern) {
    return @(Select-String -Path $stdoutLog -Pattern $Pattern -ErrorAction SilentlyContinue).Count
}
$pre = @{
    startupMarker = Count-LogMarker "startup.log" "NEKOJS-SMOKE STARTUP loaded"
    serverMarker  = Count-LogMarker "server.log"  "NEKOJS-SMOKE SERVER loaded"
    clientMarker  = Count-LogMarker "client.log"  "NEKOJS-SMOKE CLIENT loaded"
    discoverStartup = Count-LogMarker "startup.log" "发现了 \d+ 个 STARTUP 脚本"
    discoverServer  = Count-LogMarker "server.log"  "发现了 \d+ 个 SERVER 脚本"
    discoverClient  = Count-LogMarker "client.log"  "发现了 \d+ 个 CLIENT 脚本"
    fabricBootstrapDone = Count-StdoutMarker "fabric bootstrap done"
    fabricEntrypoint = Count-StdoutMarker "NekoJS fabric entrypoint reached"
    exceptions = Count-StdoutMarker "(?i)FATAL|crash report|Exception in thread .Server."
}

# ---------- RCON reload（唯一 SERVER reload 入口：root.reload(SERVER) 经命令边界） ----------
$reloadRcon = Invoke-Rcon -Command "nekojs reload server"
$reloadRcon | Out-File (Join-Path $OutDir "rcon-reload-response.txt") -Encoding utf8
Write-Host "[rcon] nekojs reload server -> $reloadRcon"

# 等待 post-reload marker（落 server.log）
$reloadDeadline = [DateTime]::UtcNow.AddSeconds(120)
while ([DateTime]::UtcNow -lt $reloadDeadline) {
    if ($proc.HasExited) { break }
    if ((Count-LogMarker "server.log" "NEKOJS-SMOKE SERVER loaded") -gt $pre.serverMarker) { break }
    Start-Sleep -Milliseconds 200
}
Start-Sleep -Seconds 2

$errorsRcon = Invoke-Rcon -Command "nekojs error"
$errorsRcon | Out-File (Join-Path $OutDir "rcon-errors-response.txt") -Encoding utf8
Write-Host "[rcon] nekojs errors -> $errorsRcon"

# ---------- reload 后计数 ----------
$post = @{
    startupMarker = Count-LogMarker "startup.log" "NEKOJS-SMOKE STARTUP loaded"
    serverMarker  = Count-LogMarker "server.log"  "NEKOJS-SMOKE SERVER loaded"
    clientMarker  = Count-LogMarker "client.log"  "NEKOJS-SMOKE CLIENT loaded"
    discoverStartup = Count-LogMarker "startup.log" "发现了 \d+ 个 STARTUP 脚本"
    discoverServer  = Count-LogMarker "server.log"  "发现了 \d+ 个 SERVER 脚本"
    discoverClient  = Count-LogMarker "client.log"  "发现了 \d+ 个 CLIENT 脚本"
    fabricBootstrapDone = Count-StdoutMarker "fabric bootstrap done"
    fabricEntrypoint = Count-StdoutMarker "NekoJS fabric entrypoint reached"
    exceptions = Count-StdoutMarker "(?i)FATAL|crash report|Exception in thread .Server."
}

# ---------- 停服（close 语义：冲刷 + 退出状态） ----------
$stopChannel = Stop-Server $proc
$exitCode = $proc.ExitCode
Write-Host "[session] stopped via $stopChannel exitCode=$exitCode"

# ---------- 判定 ----------
$checks = [ordered]@{
    server_started            = ($null -ne $doneS)
    startup_marker_once       = ($pre.startupMarker -ge 1 -and $post.startupMarker -eq $pre.startupMarker)
    client_marker_not_on_dedicated_server = ($pre.clientMarker -eq 0)
    server_marker_before_reload = ($pre.serverMarker -ge 1)
    server_marker_after_reload  = ($post.serverMarker -gt $pre.serverMarker)
    reload_response_ok        = ($reloadRcon -match "reloaded" -and $reloadRcon -match "no errors")
    errors_response_zero      = ($errorsRcon -match "healthy|no errors|none" -or $errorsRcon -notmatch "[1-9]")
    fabric_bootstrap_once     = ($Node -notmatch "fabric" -or ($pre.fabricBootstrapDone -eq 1 -and $post.fabricBootstrapDone -eq 1))
    no_fatal_exceptions       = ($post.exceptions -eq 0)
    clean_exit                = ($stopChannel -eq "rcon")
}
$checks | ConvertTo-Json | Out-File (Join-Path $OutDir "checks.json") -Encoding utf8
$s = ""
foreach ($k in $checks.Keys) { $s += "$k=$($checks[$k]) " }
Write-Host "[checks] $s"
Copy-Item $stdoutLog (Join-Path $OutDir "server-stdout-copy.log") -Force

$failed = @($checks.Keys | Where-Object { -not $checks[$_] })
if ($failed.Count -gt 0) { throw "SMOKE FAILED: $($failed -join ', ')" }
Write-Host "[smoke] PASSED ($Node)"
