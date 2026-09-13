# NekoJS ticket 06 smoke runner (bench/smoke-reload/run-reload-smoke.ps1)
#
# 目标：用真实 runServer 会话给出「candidate generation / 单一 commit 点」的外部可观察证据。
#
# 观察设计（为什么这样能判定）：
#   * commit witness = 版本化 interval 的 tick 行。candidate 的 timer 只进候选 node runtime，
#     生产 tick flush 只冲刷 active generation（ScriptManager#flushReadyNodeTimers 读 this.runtime）。
#     因此 `ticket06-tick-<v>` 行只可能由「已提交为 active 的那个 generation」产生：
#       - commit 成功 → 新版本 tick 出现（~1 行/秒），旧版本 tick 永久停止；
#       - candidate 失败 → 该版本的 tick 永不出现（候选 timer 随候选关闭丢弃）；
#       - 双重执行 → 两个版本的 tick 同时以 ~1 行/秒出现（各自 1 interval）。
#   * 入口行（ticket06-entry-<v>）在候选执行期即落日志——候选执行是真实求值，console 输出属
#     spec 09 user story 28 明示的「不深回滚外部副作用」；因此它只作为「入口跑了多少次」的计数
#     证据，不作为 commit 判定面。
#   * 失败注入：fixtures 的 engine.toml 设 scriptStatementLimit = 200_000 —— runner 写入的
#     `while(true){}` 烧尽候选 Context 的语句预算后被 Graal 关闭（确定性；现有回归测试
#     infiniteLoopInScriptEntryDoesNotFreezeServerThread 验证的正是这条路径）。
#     不要改用 scriptRunawayTimeoutSeconds 的时间窗口路径：实测它对 while(true) 无效
#     （RCON 无应答、服务器线程卡死），见 fixtures engine.toml 注释与 Ticket06RunawayProbeTest。
#     全会话期望恰好 1 行 ResourceLimits（只有注入的候选被终止）；active 被误杀会让
#     tick 停止、检查直接判失败。
#
# 通道口径复用 ticket 05/02 结论：命令与停服统一走 RCON（gradlew stdin 不可达）；rcon.py 为
# 本目录内的 Source-RCON 客户端副本（原文件在 bench/perf，本票不修改它）。
#
# 用法（Git Bash 下）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/smoke-reload/run-reload-smoke.ps1 -Node 26.1.2

param(
    [Parameter(Mandatory = $true)][string]$Node,
    [int]$ServerPort = 25871,
    [int]$RconPort = 25872,
    [string]$RconPass = "smoke06",
    [int]$BootTimeoutSec = 900,
    [int]$TickWindowSec = 6
)

$ErrorActionPreference = "Stop"
$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$FixtureDir = Join-Path $PSScriptRoot "fixtures\nekojs"
$Stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$OutDir = Join-Path $PSScriptRoot "out\$Stamp-$Node"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

switch ($Node) {
    "26.1.2" { $RunDir = Join-Path $ProjectDir "versions\$Node\run" }
    "26.2.0" { $RunDir = Join-Path $ProjectDir "versions\$Node\run" }
    default { throw "unsupported node '$Node' (ticket 06 smoke targets 26.1.2/26.2.0 runServer)" }
}

$nekoDir = Join-Path $RunDir "nekojs"
$nekoLogDir = Join-Path $RunDir "logs\nekojs"
$ScriptsDir = Join-Path $nekoDir "server_scripts"
$WitnessPath = Join-Path $ScriptsDir "sm01-witness.js"
$BoomPath = Join-Path $ScriptsDir "sm02-boom.js"

$GitRev = (& git -C $ProjectDir rev-parse HEAD).Trim().Substring(0, 8)
"utc: $((Get-Date).ToUniversalTime().ToString('o'))`ngit rev: $GitRev`nnode: $Node`nrun dir: $RunDir" |
    Out-File (Join-Path $OutDir "env.txt") -Encoding utf8
Write-Host "[smoke] node=$Node rev=$GitRev run=$RunDir out=$OutDir"

# ---------- run 目录铺设 ----------
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
if (Test-Path $nekoLogDir) { Remove-Item -Recurse -Force $nekoLogDir -ErrorAction SilentlyContinue }
Set-Content -Path (Join-Path $RunDir "eula.txt") -Value "eula=true" -Encoding ascii
$props = "server-port=$ServerPort`nmotd=nekojs smoke06`nonline-mode=false`ndifficulty=peaceful`ngamemode=survival`nenable-rcon=true`nrcon.port=$RconPort`nrcon.password=$RconPass`nspawn-protection=0`nview-distance=8`npause-when-empty-seconds=-1"
Set-Content -Path (Join-Path $RunDir "server.properties") -Value $props -Encoding ascii
$shipped = Select-String -Path (Join-Path $nekoDir "config\engine.toml") -Pattern '^scriptStatementLimit = (\d+)$' |
    Select-Object -First 1
if ($null -eq $shipped -or $shipped.Matches[0].Groups[1].Value -ne "200000") {
    throw "fixture engine.toml must ship scriptStatementLimit = 200000 (deterministic candidate kill)"
}
$shippedRunaway = Select-String -Path (Join-Path $nekoDir "config\engine.toml") -Pattern '^scriptRunawayTimeoutSeconds = (\d+)$' |
    Select-Object -First 1
if ($null -eq $shippedRunaway -or $shippedRunaway.Matches[0].Groups[1].Value -ne "0") {
    throw "fixture must keep scriptRunawayTimeoutSeconds = 0 (time-window path is not a working kill; see fixture notes)"
}
Write-Host "[deploy] fixtures + engine.toml(scriptStatementLimit=200000) + rcon ready"

function Invoke-Rcon {
    param([string]$Command, [int]$TimeoutMs = 180000)
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

$LogFile = Join-Path $nekoLogDir "server.log"

function Count-Pattern([string]$Pattern) {
    if (-not (Test-Path $LogFile)) { return 0 }
    return @(Select-String -Path $LogFile -Pattern $Pattern -ErrorAction SilentlyContinue).Count
}

function Count-Ticks([string]$Tag) { return (Count-Pattern "ticket06-tick-$Tag\b") }
function Count-Entry([string]$Tag) { return (Count-Pattern "ticket06-entry-$Tag\b") }

# 一次窗口内按版本分别采样 tick 增量（避免多次 sleep 造成窗口错位）
function Measure-Ticks([int]$Seconds, [string[]]$Tags) {
    $before = @{}
    foreach ($t in $Tags) { $before[$t] = Count-Ticks $t }
    Start-Sleep -Seconds $Seconds
    $delta = @{}
    foreach ($t in $Tags) { $delta[$t] = (Count-Ticks $t) - $before[$t] }
    return $delta
}

function Write-Witness([string]$Version) {
    $src = @(
        "// ticket 06 smoke witness (generated by run-reload-smoke.ps1): version $Version",
        "// entry 行 = 入口求值次数；tick 行 = 该版本 generation 是否持有生产 timer 分发（commit witness）",
        "console.info('ticket06-entry-$Version');",
        "setInterval(function () { console.info('ticket06-tick-$Version'); }, 1000);"
    ) -join "`n"
    Set-Content -Path $WitnessPath -Value ($src + "`n") -Encoding ascii
}

function Write-Boom {
    # while(true) 不执行宿主调用 → 只有 Graal 的语句上限能终止它（scriptStatementLimit=5e6）；
    # 候选被终止 → 事务式 reload 走 EXECUTION 失败路径（domain=candidate-killed）
    Set-Content -Path $BoomPath -Value "// ticket 06 failure injection: burns the candidate statement budget (200k; 5M+ never fires - JIT boundary)`nwhile (true) { }`n" -Encoding ascii
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
        Select-String -Pattern 'Done \((\d+\.\d+)s\)!?' | Select-Object -Last 1
    if ($null -ne $doneLine) { $doneS = $doneLine.Matches[0].Groups[1].Value; break }
    Start-Sleep -Milliseconds 300
}
if ($null -eq $doneS) { throw "server did not reach 'Done' (see $stdoutLog)" }
Write-Host "[session] server started (Done($doneS)s)"
Start-Sleep -Seconds 3

# ---------- 基线（v1：启动期加载）----------
$entryV1 = Count-Entry "v1"
$baseTicks = Measure-Ticks $TickWindowSec @('v1')
Write-Host "[baseline] entry-v1=$entryV1 tickV1=$($baseTicks['v1'])"

# ---------- Phase A：成功 reload v1 → v2（candidate → commit）----------
Write-Witness "v2"
$reloadA = Invoke-Rcon -Command "nekojs reload server"
$reloadA | Out-File (Join-Path $OutDir "rcon-phaseA-reload.txt") -Encoding utf8
Start-Sleep -Seconds 2
$entryV2 = Count-Entry "v2"
$ticksA = Measure-Ticks $TickWindowSec @('v1', 'v2')
$errorsA = Invoke-Rcon -Command "nekojs error"
$errorsA | Out-File (Join-Path $OutDir "rcon-phaseA-errors.txt") -Encoding utf8
Write-Host "[A] reload='$reloadA' entryV2=$entryV2 v1=$($ticksA['v1']) v2=$($ticksA['v2']) errors='$errorsA'"

# ---------- Phase B：注入 runaway 失败脚本 → reload 失败（候选丢弃、active 存活）----------
Write-Witness "v3"
Write-Boom
$beforeB = Count-Ticks "v2"
$reloadB = Invoke-Rcon -Command "nekojs reload server"
$reloadB | Out-File (Join-Path $OutDir "rcon-phaseB-reload-failed.txt") -Encoding utf8
$ticksB = Measure-Ticks $TickWindowSec @('v1', 'v2', 'v3')
$activeAcrossB = (Count-Ticks "v2") - $beforeB
$entryV3 = Count-Entry "v3"
$errorsB = Invoke-Rcon -Command "nekojs error"
$errorsB | Out-File (Join-Path $OutDir "rcon-phaseB-errors.txt") -Encoding utf8
Remove-Item -Force $BoomPath
Write-Host "[B] reload='$reloadB' entryV3(candidate-exec)=$entryV3 v2=$($ticksB['v2']) v3=$($ticksB['v3']) activeAcross=$activeAcrossB errors='$errorsB'"

# ---------- Phase C：修复后 reload 恢复（v4）----------
Write-Witness "v4"
$reloadC = Invoke-Rcon -Command "nekojs reload server"
$reloadC | Out-File (Join-Path $OutDir "rcon-phaseC-reload-fixed.txt") -Encoding utf8
Start-Sleep -Seconds 2
$entryV4 = Count-Entry "v4"
$ticksC = Measure-Ticks $TickWindowSec @('v1', 'v2', 'v3', 'v4')
$errorsC = Invoke-Rcon -Command "nekojs error"
$errorsC | Out-File (Join-Path $OutDir "rcon-phaseC-errors.txt") -Encoding utf8
Write-Host "[C] reload='$reloadC' entryV4=$entryV4 v2=$($ticksC['v2']) v4=$($ticksC['v4']) errors='$errorsC'"

# ---------- Phase D：重复 reload 稳定性（v5 → v6）----------
Write-Witness "v5"
$reloadD1 = Invoke-Rcon -Command "nekojs reload server"
Start-Sleep -Seconds 1
Write-Witness "v6"
$reloadD2 = Invoke-Rcon -Command "nekojs reload server"
Start-Sleep -Seconds 2
$entryV6 = Count-Entry "v6"
$ticksD = Measure-Ticks $TickWindowSec @('v1', 'v2', 'v3', 'v4', 'v5', 'v6')
$reloadD1 | Out-File (Join-Path $OutDir "rcon-phaseD-reload-v5.txt") -Encoding utf8
$reloadD2 | Out-File (Join-Path $OutDir "rcon-phaseD-reload-v6.txt") -Encoding utf8
Write-Host "[D] v5='$reloadD1' v6='$reloadD2' entryV6=$entryV6 ticks=$($ticksD | ConvertTo-Json -Compress)"

# ---------- Phase E：STARTUP 非事务边界（AC6 入口外观）----------
$reloadE = Invoke-Rcon -Command "nekojs reload startup"
$reloadE | Out-File (Join-Path $OutDir "rcon-phaseE-reload-startup.txt") -Encoding utf8
Write-Host "[E] startup reload='$reloadE'"

# ---------- 停服 ----------
$stopChannel = Stop-Server $proc
$exitCode = $proc.ExitCode
Write-Host "[session] stopped via $stopChannel exitCode=$exitCode"

# ---------- 判定 ----------
$fatal = @(Select-String -Path $stdoutLog -Pattern '(?i)FATAL|crash report' -ErrorAction SilentlyContinue).Count
$resourceLimitKills = Count-Pattern "ResourceLimits"
$noGuidance = ($reloadB -notmatch '(?i)try |run /|reload again|fix |suggest')
$oldTickSumD = (@('v1', 'v2', 'v3', 'v4', 'v5') | ForEach-Object { $ticksD[$_] } | Measure-Object -Sum).Sum
$allTickSumD = (@('v1', 'v2', 'v3', 'v4', 'v5', 'v6') | ForEach-Object { $ticksD[$_] } | Measure-Object -Sum).Sum

$checks = [ordered]@{
    server_started                        = ($null -ne $doneS)
    # baseline：启动期加载的 v1 generation 持有生产 timer
    baseline_active_generation_ticks      = ($baseTicks['v1'] -ge 4 -and $baseTicks['v1'] -le 8)
    # Phase A：成功 reload = candidate → commit，生产路由切到 v2
    A_reload_ok_response                  = ($reloadA -match "reloaded" -and $reloadA -match "no errors")
    A_entry_reran_once                    = ($entryV2 -eq 1)
    A_new_generation_owns_production_tick = ($ticksA['v2'] -ge 4 -and $ticksA['v2'] -le 8)
    A_old_generation_stopped              = ($ticksA['v1'] -eq 0)
    A_no_double_execution                 = (($ticksA['v1'] + $ticksA['v2']) -le ($TickWindowSec + 2))
    A_errors_healthy                      = ($errorsA -match "healthy")
    # Phase B：candidate 失败 → 结构化失败结果 + active 存活 + 候选资源丢弃
    B_failure_response_structured         = (
                                             ($reloadB -match "reload failed") -and
                                             ($reloadB -match "phase=EXECUTION") -and
                                             ($reloadB -match "generation=") -and
                                             ($reloadB -match "owner=ScriptManager\[server\]") -and
                                             ($reloadB -match "domain=candidate-killed"))
    B_failure_source_location_points_at_injected_script = ($reloadB -match "source=server/sm02-boom\.js")
    B_failure_has_no_repair_guidance      = $noGuidance
    B_candidate_never_committed           = ($ticksB['v3'] -eq 0)
    B_active_survives_failure             = ($ticksB['v2'] -ge 4 -and $activeAcrossB -ge 6)
    B_old_generations_silent              = ($ticksB['v1'] -eq 0)
    B_error_reported_to_panel             = ($errorsB -notmatch "healthy")
    # 语句上限终止面：全会话恰好 1 次 ResourceLimits（注入的候选）；active 若被误杀会 >1 且 tick 断流
    B_resource_limit_kill_is_the_injected_candidate = ($resourceLimitKills -eq 1)
    # Phase C：修复后再次 commit 成功
    C_reload_recovers                     = ($reloadC -match "reloaded" -and $entryV4 -eq 1)
    C_new_generation_owns_tick            = ($ticksC['v4'] -ge 4 -and $ticksC['v4'] -le 8)
    C_previous_generations_silent         = ($ticksC['v2'] -eq 0 -and $ticksC['v3'] -eq 0)
    C_errors_healthy                      = ($errorsC -match "healthy")
    # Phase D：重复 reload 稳定（每代只跑一次，只有最新一代 tick）
    D_repeated_reloads_ok                 = ($reloadD1 -match "reloaded" -and $reloadD2 -match "reloaded")
    D_latest_entry_reran_once             = ($entryV6 -eq 1)
    D_latest_generation_single_owner      = ($ticksD['v6'] -ge 4 -and $ticksD['v6'] -le 8)
    D_no_old_generation_dispatch          = ($oldTickSumD -eq 0)
    D_no_double_execution                 = ($allTickSumD -le ($TickWindowSec + 2))
    # Phase E：STARTUP 非事务边界在入口外观显式可见（AC6）
    E_startup_non_transactional_surface   = (
                                             ($reloadE -match "reloaded") -and
                                             ($reloadE -match "non-transactionally") -and
                                             ($reloadE -match "restart"))
    no_fatal_exceptions                   = ($fatal -eq 0)
    clean_exit                            = ($stopChannel -eq "rcon" -and $exitCode -eq 0)
}

$counts = [ordered]@{
    entries = [ordered]@{ v1 = $entryV1; v2 = $entryV2; v3_candidate_execution_visible = $entryV3; v4 = $entryV4; v6 = $entryV6 }
    tickWindows = [ordered]@{
        baseline_v1                 = $baseTicks
        phaseA                      = $ticksA
        phaseB                      = $ticksB
        phaseB_active_across_reload = $activeAcrossB
        phaseC                      = $ticksC
        phaseD                      = $ticksD
    }
    serverStartedSec = $doneS
    resourceLimitKills = $resourceLimitKills
    stopChannel = $stopChannel
    exitCode = $exitCode
}
$checks | ConvertTo-Json | Out-File (Join-Path $OutDir "checks.json") -Encoding utf8
$counts | ConvertTo-Json -Depth 5 | Out-File (Join-Path $OutDir "counts.json") -Encoding utf8
$s = ""
foreach ($k in $checks.Keys) { $s += "$k=$($checks[$k]) " }
Write-Host "[checks] $s"

# 证据副本：stdout 与 nekojs server.log 随 out/ 一起进 gz 归档
Copy-Item $stdoutLog (Join-Path $OutDir "server-stdout-copy.log") -Force
if (Test-Path $LogFile) { Copy-Item $LogFile (Join-Path $OutDir "nekojs-server.log") -Force }

$failed = @($checks.Keys | Where-Object { -not $checks[$_] })
if ($failed.Count -gt 0) { throw "SMOKE FAILED: $($failed -join ', ')" }
Write-Host "[smoke] PASSED ($Node)"
