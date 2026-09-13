# query25 harness runner (ticket 25). Windows PowerShell 5.1.
# MUST be saved with UTF-8 BOM: PS 5.1 decodes BOM-less files with the ANSI code page and
# Chinese comments then corrupt parsing (same pitfall as bench/perf/sample.ps1).
#
# Steps (run from the isolated worktree root):
#   deploy     wipe + deploy fixtures/test_scripts into versions/<Node>/run/nekojs/test_scripts,
#              write eula.txt / server.properties (fixed 25971 / RCON 25972)
#   start      detached gradlew :<Node>:runServer, stdout/stderr -> out/<Session>-*.log
#   wait-done  poll stdout for "Done (" or failure markers
#   setup      RCON forceload + tagged summons near the anchor (REQUIRED world prerequisite)
#   test       RCON `nekojs test` (tee to out/rcon-<ts>.log)
#   extract    pull [NekoJS Test] / script-failure / BUILD lines into out/<Session>-extract.log
#   verify     every SUMMARY line must be "0 failed" and PASS lines must exist, else throw
#   stop       RCON stop + wait gradle client exit + world/session.lock release
#   all        deploy -> start -> wait-done -> setup -> test -> extract -> verify -> stop
#
# Optional:
#   -IncludeDiagnostics  also deploy bench/query/diagnostics/*.js (probes, not acceptance)
#   -IncludeNegative     also deploy bench/query/fixtures/negative/*.js (EXPECTED failures;
#                        verify will fail by design - the point is the source-location trio)

param(
    [Parameter(Position = 0, Mandatory = $true)][string]$Step,
    [string]$Session = 'q1',
    [string]$Node = '26.1.2',
    [int]$ServerPort = 25971,
    [int]$RconPort = 25972,
    [string]$RconPass = 'ticket25',
    [int]$BootTimeoutSec = 900,
    [switch]$IncludeDiagnostics,
    [switch]$IncludeNegative
)

$ErrorActionPreference = 'Stop'
$ScriptDir = (Resolve-Path $PSScriptRoot).Path
$Project = (Resolve-Path (Join-Path $ScriptDir '..\..')).Path
$RunDir = Join-Path $Project ("versions\" + $Node + "\run")
$NekoRoot = Join-Path $RunDir 'nekojs'
$TestScriptsDir = Join-Path $NekoRoot 'test_scripts'
$OutDir = Join-Path $ScriptDir 'out'
$FixtureDir = Join-Path $ScriptDir 'fixtures'
$StdoutLog = Join-Path $OutDir ($Session + '-stdout.log')
$StderrLog = Join-Path $OutDir ($Session + '-stderr.log')
$PidFile = Join-Path $OutDir ($Session + '.pid')
$ExtractLog = Join-Path $OutDir ($Session + '-extract.log')
# 端口/密码字面量与 param 默认值是同一来源的两份表达——改一处必改另一处（datafix 同款注记）。
$RconPy = Join-Path $ScriptDir 'rcon.py'

function Ensure-OutDir { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

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

function Invoke-Rcon {
    param([string[]]$Commands, [int]$TimeoutSec = 120)
    $out = & python $RconPy $RconPort $RconPass ([string]$TimeoutSec) @Commands 2>&1
    $code = $LASTEXITCODE
    $text = ($out | ForEach-Object { "$_" }) -join "`n"
    if ($code -ne 0) { throw "rcon failed (exit $code): $text" }
    return $text
}

function Invoke-RconTee {
    param([string[]]$Commands, [string]$Tag, [int]$TimeoutSec = 120)
    Ensure-OutDir
    $text = Invoke-Rcon -Commands $Commands -TimeoutSec $TimeoutSec
    $log = Join-Path $OutDir ('rcon-' + $Tag + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
    $text | Set-Content -Path $log -Encoding utf8
    Write-Host "[rcon:$Tag] $text"
    Write-Host "[rcon:$Tag] tee=$log"
    return $text
}

function Wait-WorldLockRelease([int]$TimeoutSec = 120) {
    $lockFile = Join-Path $RunDir 'world\session.lock'
    if (-not (Test-Path $lockFile)) { Write-Host 'LOCK-GONE'; return }
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $fs = [System.IO.File]::Open($lockFile, 'Open', 'ReadWrite', 'None')
            $fs.Close()
            Write-Host 'LOCK-RELEASED'
            return
        } catch { Start-Sleep -Milliseconds 500 }
    }
    Write-Host "LOCK-HELD after ${TimeoutSec}s"
}

function Get-SessionPid {
    if (-not (Test-Path $PidFile)) { return $null }
    return [int](Get-Content $PidFile | Select-Object -First 1)
}

function Test-Alive([int]$procId) { return ($null -ne (Get-Process -Id $procId -ErrorAction SilentlyContinue)) }

# ---------- steps ----------

switch ($Step) {

    'deploy' {
        Ensure-OutDir
        Remove-TreeWithRetry $NekoRoot
        New-Item -ItemType Directory -Force -Path $TestScriptsDir | Out-Null
        Copy-Tree (Join-Path $FixtureDir 'test_scripts') $TestScriptsDir
        if ($IncludeDiagnostics) {
            Copy-Tree (Join-Path $ScriptDir 'diagnostics') $TestScriptsDir
            Write-Host '[deploy] diagnostics included (probes, not acceptance)'
        }
        if ($IncludeNegative) {
            Copy-Tree (Join-Path $FixtureDir 'negative') $TestScriptsDir
            Write-Host '[deploy] negative probe included (EXPECTED failures)'
        }
        Set-Content -Path (Join-Path $RunDir 'eula.txt') -Value 'eula=true' -Encoding Ascii
        $props = @(
            "server-port=$ServerPort",
            'enable-rcon=true',
            "rcon.port=$RconPort",
            "rcon.password=$RconPass",
            'online-mode=false',
            'difficulty=peaceful',
            'gamemode=survival',
            'view-distance=8',
            'spawn-protection=0',
            'pause-when-empty-seconds=-1',
            'level-type=minecraft:flat',
            'motd=ticket25 query tools'
        )
        Set-Content -Path (Join-Path $RunDir 'server.properties') -Value $props -Encoding Ascii
        Write-Host ("DEPLOY-OK scripts=" + (@(Get-ChildItem -Path $TestScriptsDir -Filter *.js -Recurse).Count))
    }

    'start' {
        Ensure-OutDir
        foreach ($f in @($StdoutLog, $StderrLog)) { if (Test-Path $f) { Remove-Item $f -Force } }
        # cmd 重定向（bench/smoke 同款）：字节透明，子进程按 UTF-8 写日志。
        # 必须显式 `chcp 65001`：本机实测不带它时 JVM 的 stdout 编码是 ASCII 代理，
        # 非 ASCII 全部被吞，脚本级失败行（「脚本运行完毕」「脚本执行失败」「环境/位置/原因
        # 三元组」）在日志里不可读，extract/verify 的中文匹配会退化成静默失效的检查。
        # 也不能用 Start-Process -RedirectStandardOutput（同上，非 ASCII 丢失）。
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = 'cmd.exe'
        $psi.Arguments = "/c chcp 65001 >nul & gradlew.bat :$Node`:runServer --console=plain > `"$StdoutLog`" 2> `"$StderrLog`""
        $psi.WorkingDirectory = $Project
        $psi.UseShellExecute = $false
        $psi.RedirectStandardInput = $true
        $proc = [System.Diagnostics.Process]::Start($psi)
        Set-Content -Path $PidFile -Value $proc.Id
        Write-Host ("START-LAUNCHED pid=" + $proc.Id + " stdout=" + $StdoutLog)
    }

    'wait-done' {
        $deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSec)
        while ([DateTime]::UtcNow -lt $deadline) {
            if (Test-Path $StdoutLog) {
                $hit = Select-String -Path $StdoutLog -Pattern 'Done \(' -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($hit) { Write-Host ("START-DONE " + $hit.Line.Trim()); exit 0 }
            }
            if ((Test-Path $StdoutLog) -and (Test-Path $StderrLog)) {
                $fail = Select-String -Path $StdoutLog, $StderrLog -Pattern 'BUILD FAILED|Exception in thread "main"|Fatal error|DirectoryLock' -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($fail) { Write-Host ("START-FAILED " + $fail.Path + ':' + $fail.LineNumber + ' ' + $fail.Line.Trim()); exit 1 }
            }
            $procId = Get-SessionPid
            if (($procId -ne $null) -and (-not (Test-Alive $procId))) { Write-Host 'START-FAILED client-exited-without-done-marker'; exit 1 }
            Start-Sleep -Seconds 2
        }
        Write-Host "START-TIMEOUT after ${BootTimeoutSec}s"
        exit 2
    }

    'setup' {
        # 世界状态前置：forceload + 锚点附近带 tag 的实体（相对坐标 = 服务器 command source 原点）。
        # 不在 /nekojs test 里做 summon：测试在服务器线程同步跑，期间世界不 tick，同 tick 召唤不可见。
        $cmds = @(
            'forceload add -16 -16 16 16',
            'summon minecraft:cow ~ ~ ~ {Tags:["q25f","q25f_c1"],NoAI:1b,NoGravity:1b}',
            'summon minecraft:cow ~3 ~ ~ {Tags:["q25f","q25f_c2"],NoAI:1b,NoGravity:1b}',
            'summon minecraft:cow ~6 ~ ~ {Tags:["q25f","q25f_c3"],NoAI:1b,NoGravity:1b}',
            'summon minecraft:pig ~1.5 ~ ~1.5 {Tags:["q25f","q25f_p1"],NoAI:1b,NoGravity:1b}',
            'summon minecraft:bee ~8 ~ ~8 {Tags:["q25bee"],NoAI:1b,NoGravity:1b}'
        )
        Invoke-RconTee -Commands $cmds -Tag 'setup' | Out-Null
        Write-Host 'SETUP-OK'
    }

    'test' {
        $resp = Invoke-RconTee -Commands @('nekojs test') -Tag 'nekojs-test' -TimeoutSec 180
        Write-Host ("TEST-RESPONSE " + $resp.Trim())
    }

    'extract' {
        Ensure-OutDir
        # 证据源有两处，缺一不可：
        # 1) mod 自己写的 run/logs/nekojs/test.log —— 完整的测试输出，**平台 ANSI 编码**
        #    （本机 zh-CN = GBK；已归档的旧证据 nekojs-test-green.log 同款字节），中文
        #    marker（「脚本运行完毕」「脚本执行失败」+「环境/位置/原因」三元组）只有从这里读才完整。
        # 2) 会话 stdout —— JVM/gradle 管道会把非 ASCII 吞成 ASCII（本机实测：cmd 字节透明重定向 +
        #    chcp 65001 都改不掉，daemon 编码在启动时就固定了），但 ASCII marker
        #    （Done (/BUILD/Unknown identifier）稳定，用于判启动与 preflight。
        $nekoTestLog = Join-Path $RunDir 'logs\nekojs\test.log'
        $lines = New-Object System.Collections.Generic.List[string]
        if (Test-Path $nekoTestLog) {
            $lines.AddRange([string[]]@(Get-Content -Path $nekoTestLog -Encoding Default))
        } else {
            Write-Host "[extract] warn: $nekoTestLog not found (mod per-type log missing)"
        }
        if (Test-Path $StdoutLog) {
            # -CaseSensitive：`BUILD ` 不区分大小写会命中断言消息里的 "build the same selector
            # shape"（实测把一条 PASS 行重复抽进 extract，制造假重复）。
            $console = @(Select-String -Path $StdoutLog -CaseSensitive `
                    -Pattern 'Done \(|BUILD SUCCESSFUL|BUILD FAILED|Unknown identifier' `
                    -Encoding Default -ErrorAction SilentlyContinue |
                ForEach-Object { $_.Line.TrimEnd() })
            $lines.AddRange([string[]]$console)
        }
        $lines | Set-Content -Path $ExtractLog -Encoding utf8
        Write-Host ("EXTRACT-OK lines=" + $lines.Count + " -> " + $ExtractLog)
    }

    'verify' {
        if (-not (Test-Path $ExtractLog)) { throw "missing extract log: $ExtractLog (run the extract step first)" }
        $lines = @(Get-Content $ExtractLog)
        $summaries = @($lines | Where-Object { $_ -match '\[SUMMARY\]' })
        $pass = @($lines | Where-Object { $_ -match '\[PASS\]' }).Count
        $fail = @($lines | Where-Object { $_ -match '\[FAIL\]' }).Count
        $errors = @($lines | Where-Object { $_ -match '脚本执行失败' }).Count
        # ASCII 稳定兜底：脚本级失败块必然引用 `nekojs:test/<path>.js`（路径是 ASCII；PASS 行
        # 从不出脚本路径），preflight 拒绝是 `Unknown identifier` / `Script binding-preflight`。
        # 即使 extract 落在非 zh-CN 平台（ANSI 非 936）导致中文 marker 不可读，这几条也不会
        # 退化成「零检查」——首次实现漏了 `nekojs:test/<path>`，负向探针跑完 verify 仍是 0（已修）。
        $scriptFailures = @($lines | Where-Object {
                $_ -match '脚本执行失败|nekojs:test/[^\s]+\.js|Script binding-preflight'
            }).Count
        $preflight = @($lines | Where-Object { $_ -match 'Unknown identifier' }).Count
        if ($summaries.Count -eq 0) { throw 'VERIFY-FAILED: no [SUMMARY] line in the extract (fixtures did not run)' }
        $bad = @($summaries | Where-Object { $_ -notmatch '0 failed' })
        $stat = "[verify] summaries=$($summaries.Count) pass=$pass fail=$fail scriptLevelFailures=$scriptFailures failLines=$errors preflightErrors=$preflight"
        Write-Host $stat
        foreach ($s in $summaries) { Write-Host ("[verify] " + $s.Trim()) }
        if ($bad.Count -gt 0) {
            throw ("VERIFY-FAILED: " + $bad.Count + " SUMMARY line(s) report failures. 逐条 FAIL 行：" + (($lines | Where-Object { $_ -match '\[FAIL\]' }) -join ' | '))
        }
        # SUMMARY 的 passed/failed 在同一服务器会话内跨调用累计——所以不仅查 SUMMARY，
        # 也要求逐条 FAIL 行与脚本级失败为零（历史累计不会掩盖它们）。
        if ($fail -gt 0) { throw "VERIFY-FAILED: $fail explicit [FAIL] line(s)" }
        if ($pass -eq 0) { throw 'VERIFY-FAILED: zero PASS lines' }
        if ($scriptFailures -gt 0) {
            throw "VERIFY-FAILED: $scriptFailures script-level failure(s) in a default-set run (see $ExtractLog)"
        }
        if ($preflight -gt 0) {
            throw "VERIFY-FAILED: $preflight preflight rejection(s) ('Unknown identifier') in a default-set run (see $ExtractLog)"
        }
        Write-Host ("QUERY-HARNESS PASSED (summary=" + $summaries.Count + ", pass=" + $pass + ", fail=0)")
    }

    'stop' {
        Ensure-OutDir
        try {
            Invoke-RconTee -Commands @('stop') -Tag 'stop' -TimeoutSec 30 | Out-Null
            Write-Host 'STOP-SENT'
        } catch {
            Write-Host 'STOP-SEND-FAILED (server may already be down)'
        }
        $procId = Get-SessionPid
        if ($procId -ne $null) {
            $deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSec)
            while ([DateTime]::UtcNow -lt $deadline) {
                if (-not (Test-Alive $procId)) { Write-Host 'CLIENT-EXITED'; break }
                Start-Sleep -Seconds 1
            }
        }
        Wait-WorldLockRelease 120
        Write-Host 'STOP-COMPLETE'
    }

    'all' {
        & $PSCommandPath deploy -Session $Session -Node $Node -ServerPort $ServerPort -RconPort $RconPort -RconPass $RconPass -BootTimeoutSec $BootTimeoutSec -IncludeDiagnostics:$IncludeDiagnostics -IncludeNegative:$IncludeNegative
        & $PSCommandPath start -Session $Session -Node $Node -ServerPort $ServerPort -RconPort $RconPort -RconPass $RconPass -BootTimeoutSec $BootTimeoutSec
        & $PSCommandPath wait-done -Session $Session -Node $Node -BootTimeoutSec $BootTimeoutSec
        & $PSCommandPath setup -Session $Session -Node $Node -ServerPort $ServerPort -RconPort $RconPort -RconPass $RconPass
        & $PSCommandPath test -Session $Session -Node $Node -ServerPort $ServerPort -RconPort $RconPort -RconPass $RconPass
        & $PSCommandPath extract -Session $Session -Node $Node
        try {
            & $PSCommandPath verify -Session $Session -Node $Node
        } finally {
            & $PSCommandPath stop -Session $Session -Node $Node -ServerPort $ServerPort -RconPort $RconPort -RconPass $RconPass -BootTimeoutSec $BootTimeoutSec
        }
    }

    default { throw "unknown step: $Step" }
}
