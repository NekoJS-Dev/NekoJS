# NekoJS ticket 08 — external addon discovery / reload-survival smoke (bench/plugin-addon/run-addon-smoke.ps1)
#
# Proves that a REAL external addon (a standalone jar in run/mods, discovered through the loader's
# own plugin discovery path) enters the Plugin Runtime, contributes through pre-existing extension
# points, is consumed from scripts, and survives a normal /nekojs reload without re-bootstrap.
#
# Repeatability (AC2): every round starts from a CLEAN run/mods directory — the script wipes
# run/nekojs, run/logs/nekojs and any previously installed nekojs08addon-*.jar before each round, and
# each round gets a fresh server process. Rounds are compared field-by-field and the result is
# written to consistency.json; all rounds must also individually pass.
#
# Channel: RCON (same conclusion as bench/smoke: gradlew stdin is unreachable).
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2-fabric
#
# Optional: -Rounds N (default 2), -Quiet (no per-round PASSED banner).

param(
    [Parameter(Mandatory = $true)][string]$Node,
    [int]$Rounds = 2,
    [int]$ServerPort = 25881,
    [int]$RconPort = 25882,
    [string]$RconPass = "t08rcon",
    [int]$BootTimeoutSec = 900
)

$ErrorActionPreference = "Stop"
$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$OutDir = Join-Path $PSScriptRoot "out\$Stamp-$Node"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

switch ($Node) {
    "26.1.2"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run";        $AddonJar = "nekojs08addon-1.0.0-neoforge.jar" ; $ExpectedDiscovery = 1 }
    "1.21.1"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run";        $AddonJar = "nekojs08addon-1.0.0-neoforge.jar" ; $ExpectedDiscovery = 1 }
    "26.2.0"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run";        $AddonJar = "nekojs08addon-1.0.0-neoforge.jar" ; $ExpectedDiscovery = 1 }
    "26.1.2-fabric" { $RunDir = Join-Path $ProjectDir "versions\$Node\run-server"; $AddonJar = "nekojs08addon-1.0.0-fabric.jar"   ; $ExpectedDiscovery = 2 }
    default { throw "unsupported node '$Node'" }
}

# ---------- environment record (AC2 evidence must be self-describing) ----------
$GitRev = (& git -C $ProjectDir rev-parse HEAD).Trim()
$GitDirty = (& git -C $ProjectDir status --porcelain) -join "`n"
$DirtyFlag = if ([string]::IsNullOrWhiteSpace($GitDirty)) { "clean" } else { "dirty" }
$AddonSha = (Get-FileHash (Join-Path $PSScriptRoot "build\$AddonJar") -Algorithm SHA256).Hash
$envLines = @(
    "utc: $((Get-Date).ToUniversalTime().ToString('o'))",
    "git head: $GitRev",
    "git worktree: $DirtyFlag",
    "node: $Node",
    "rounds: $Rounds",
    "run dir: $RunDir",
    "addon jar: $AddonJar",
    "addon jar sha256: $AddonSha",
    "expected nkoj-framework discoveries: $ExpectedDiscovery"
)
if ($DirtyFlag -eq "dirty") { $envLines += "git status --porcelain:"; $envLines += ($GitDirty -split "`n" | ForEach-Object { "  $_" }) }
$envLines | Out-File (Join-Path $OutDir "env.txt") -Encoding utf8
Write-Host "[addon-smoke] node=$Node head=$($GitRev.Substring(0,8)) worktree=$DirtyFlag rounds=$Rounds out=$OutDir"

function Invoke-Rcon {
    param([string]$Command, [int]$TimeoutMs = 120000)
    $out = & python (Join-Path $PSScriptRoot "..\smoke\rcon.py") $RconPort $RconPass ([string]($TimeoutMs / 1000)) $Command 2>&1
    if ($LASTEXITCODE -ne 0) { throw "rcon failed: $out" }
    return ($out -join "`n")
}

function Stop-Server($proc) {
    try {
        Invoke-Rcon -Command "stop" -TimeoutMs 30000 | Out-Null
        if ($proc.WaitForExit(90000)) { return "rcon" }
    } catch {}
    try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
    if (-not $proc.WaitForExit(30000)) { try { $proc.Kill() } catch {} }
    return "taskkill"
}

function Reset-RunDir {
    # CLEAN run/mods: drop every artefact this smoke owns before each round.
    $nekoDir = Join-Path $RunDir "nekojs"
    if (Test-Path $nekoDir) { Remove-Item -Recurse -Force $nekoDir -ErrorAction SilentlyContinue }
    $nekoLogDir = Join-Path $RunDir "logs\nekojs"
    if (Test-Path $nekoLogDir) { Remove-Item -Recurse -Force $nekoLogDir -ErrorAction SilentlyContinue }
    $modsDir = Join-Path $RunDir "mods"
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
    Get-ChildItem $modsDir -Filter "nekojs08addon-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
    New-Item -ItemType Directory -Force -Path $nekoDir | Out-Null
    & robocopy (Join-Path $PSScriptRoot "fixtures\nekojs") $nekoDir /E /NJH /NJS /NDL /NFL /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy fixtures failed with exit $LASTEXITCODE" }
    Copy-Item (Join-Path $PSScriptRoot "build\$AddonJar") $modsDir -Force
    Set-Content -Path (Join-Path $RunDir "eula.txt") -Value "eula=true" -Encoding ascii
    $props = "server-port=$ServerPort`nmotd=nekojs addon08 smoke`nonline-mode=false`ndifficulty=peaceful`ngamemode=survival`nenable-rcon=true`nrcon.port=$RconPort`nrcon.password=$RconPass`nspawn-protection=0`nview-distance=6`npause-when-empty-seconds=-1`nlevel-name=world"
    Set-Content -Path (Join-Path $RunDir "server.properties") -Value $props -Encoding ascii
}

function Read-NekoLog([string]$Name) {
    $p = Join-Path $RunDir "logs\nekojs\$Name"
    if (-not (Test-Path $p)) { return "" }
    # the game process keeps these logs open for writing; open shared instead of exclusive
    $stream = [System.IO.File]::Open($p, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::GetEncoding(936))
        return $reader.ReadToEnd()
    } finally { $stream.Dispose() }
}

function Count-Text([string]$Text, [string]$Pattern) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    return ([regex]::Matches($Text, $Pattern)).Count
}

function Run-Round([int]$Round) {
    Write-Host "[addon-smoke] ---- round $Round/$Rounds (clean run dir) ----"
    Reset-RunDir

    $stdoutLog = Join-Path $OutDir "round-$Round-server.log"
    $stderrLog = Join-Path $OutDir "round-$Round-server-stderr.log"
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c gradlew.bat :$Node`:runServer --console=plain > `"$stdoutLog`" 2> `"$stderrLog`""
    $psi.WorkingDirectory = $ProjectDir
    $psi.UseShellExecute = $false
    $proc = [System.Diagnostics.Process]::Start($psi)
    Write-Host "[addon-smoke] r$Round started pid=$($proc.Id)"
    function Count-Stdout([string]$Pattern) { @(Select-String -Path $stdoutLog -Pattern $Pattern -ErrorAction SilentlyContinue).Count }

    $doneS = $null
    $deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSec)
    while ($true) {
        if ($proc.HasExited) { break }
        if ([DateTime]::UtcNow -gt $deadline) {
            Write-Host "[addon-smoke] r$Round boot timeout, killing"
            try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
            break
        }
        $doneLine = Get-Content $stdoutLog -Tail 80 -ErrorAction SilentlyContinue |
            Select-String -Pattern 'Done \((\d+\.\d+)s\)!' | Select-Object -Last 1
        if ($null -ne $doneLine) { $doneS = $doneLine.Matches[0].Groups[1].Value; break }
        Start-Sleep -Milliseconds 300
    }
    if ($null -eq $doneS) { throw "round ${Round}: server did not reach 'Done' (see $stdoutLog)" }
    Write-Host "[addon-smoke] r$Round server started (Done(${doneS}s))"
    Start-Sleep -Seconds 2

    $preStartup = Read-NekoLog "startup.log"
    $preServer = Read-NekoLog "server.log"
    $preStartupConsumed = Count-Text $preStartup "NEKOJS-ADDON08 startup-binding"
    $preServerConsumed = Count-Text $preServer "NEKOJS-ADDON08 server-binding"
    $preModuleConsumed = Count-Text $preServer "NEKOJS-ADDON08 server-module"
    $preFilteredRequired = Count-Text $preStartup "NEKOJS-ADDON08 startup-filtered-requiredmods-absent true"
    $preFilteredClient = Count-Text $preStartup "NEKOJS-ADDON08 startup-filtered-clientonly-absent true"

    $reloadRcon = Invoke-Rcon -Command "nekojs reload server"
    $reloadRcon | Out-File (Join-Path $OutDir "round-$Round-rcon-reload.txt") -Encoding utf8
    $reloadDeadline = [DateTime]::UtcNow.AddSeconds(120)
    while ([DateTime]::UtcNow -lt $reloadDeadline) {
        if ($proc.HasExited) { break }
        if ((Count-Text (Read-NekoLog "server.log") "NEKOJS-ADDON08 server-binding") -gt $preServerConsumed) { break }
        Start-Sleep -Milliseconds 200
    }
    Start-Sleep -Seconds 2

    $errorsRcon = ""
    try { $errorsRcon = Invoke-Rcon -Command "nekojs error" } catch { $errorsRcon = "RCON-QUERY-FAILED: $_" }
    $errorsRcon | Out-File (Join-Path $OutDir "round-$Round-rcon-errors.txt") -Encoding utf8

    $postStartup = Read-NekoLog "startup.log"
    $postServer = Read-NekoLog "server.log"
    $postStartupConsumed = Count-Text $postStartup "NEKOJS-ADDON08 startup-binding"
    $postServerConsumed = Count-Text $postServer "NEKOJS-ADDON08 server-binding"
    $postModuleConsumed = Count-Text $postServer "NEKOJS-ADDON08 server-module"
    $stopChannel = Stop-Server $proc
    Start-Sleep -Seconds 3

    $discovered = Count-Stdout "NEKOJS-ADDON08 plugin-instantiated"
    $instanceMarker = Count-Stdout "NEKOJS-ADDON08 plugin-instantiated instance=1"
    $bindingReg = Count-Stdout "NEKOJS-ADDON08 binding-registered"
    $initStartup = Count-Stdout "NEKOJS-ADDON08 initStartup instances="
    $requiredModsLeak = Count-Stdout "required-mods-filter-LEAKED"
    $clientOnlyLeak = Count-Stdout "client-only-filter-LEAKED"

    # Loader-specific discovery shapes (recorded, not papered over):
    #   NeoForge: FML's annotation scan instantiates the addon class exactly once.
    #   Fabric:   FabricLoader pre-launch probes each declared entrypoint, so AddonPlugin's ctor
    #             runs twice (one throwaway probe NekoJS never registers + one discovery).
    $checks = [ordered]@{
        server_started                   = ($null -ne $doneS);
        addon_instances_are_deduped      = ($instanceMarker -eq 1 -and $discovered -eq $ExpectedDiscovery);
        addon_bootstrap_once             = ($bindingReg -ge 1 -and $initStartup -eq 1);
        binding_consumed_by_script       = ($preStartupConsumed -ge 1 -and $preServerConsumed -ge 1);
        node_module_consumed_by_script   = ($preModuleConsumed -ge 1);
        binding_alive_after_reload       = ($postServerConsumed -gt $preServerConsumed);
        module_alive_after_reload        = ($postModuleConsumed -gt $preModuleConsumed);
        addon_required_mods_filter_holds = ($preFilteredRequired -ge 1 -and $requiredModsLeak -eq 0);
        addon_client_only_filter_holds   = ($preFilteredClient -ge 1 -and $clientOnlyLeak -eq 0);
        no_script_errors                 = ((Count-Text $postServer "\[ERROR\]") -eq 0);
        clean_exit                       = ($stopChannel -eq "rcon")
    }
    $checks | ConvertTo-Json | Out-File (Join-Path $OutDir "round-$Round-checks.json") -Encoding utf8
    [ordered]@{
        round = $Round; discovered = $discovered; instanceMarker = $instanceMarker;
        bindingReg = $bindingReg; initStartup = $initStartup;
        preStartupConsumed = $preStartupConsumed; preServerConsumed = $preServerConsumed;
        preModuleConsumed = $preModuleConsumed; postServerConsumed = $postServerConsumed;
        postModuleConsumed = $postModuleConsumed; requiredModsLeak = $requiredModsLeak;
        clientOnlyLeak = $clientOnlyLeak; stopChannel = $stopChannel
    } | ConvertTo-Json | Out-File (Join-Path $OutDir "round-$Round-counts.json") -Encoding utf8

    $failed = @($checks.Keys | Where-Object { -not $checks[$_] })
    $banner = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED: $($failed -join ', ')" };
    Write-Host "[addon-smoke] r$Round $banner";
    return [ordered]@{ checks = $checks; counts = (Get-Content (Join-Path $OutDir "round-$Round-counts.json") -Raw | ConvertFrom-Json) }
}

# ---------- run all rounds ----------
$results = @()
for ($i = 1; $i -le $Rounds; $i++) { $results += (Run-Round $i) }

# ---------- cross-round consistency (AC2: repeated execution must agree) ----------
$reference = $results[0].checks
$mismatches = @()
$roundSummaries = @()
for ($i = 0; $i -lt $results.Count; $i++) {
    $roundSummaries += $results[$i].counts
    foreach ($k in $reference.Keys) {
        if ($results[$i].checks[$k] -ne $reference[$k]) {
            $mismatches += "round $($i + 1): $k = $($results[$i].checks[$k]) but round 1 = $($reference[$k])"
        }
    }
}
$consistent = ($mismatches.Count -eq 0)
[ordered]@{
    rounds = $Rounds; consistent = $consistent; mismatches = $mismatches;
    perRoundChecks = @($results | ForEach-Object { $_.checks });
    perRoundCounts = $roundSummaries
} | ConvertTo-Json -Depth 6 | Out-File (Join-Path $OutDir "consistency.json") -Encoding utf8

# ---------- verdict ----------
# NOTE: PowerShell has no nested-$_ closure capture here; evaluate each round's failing keys in a
# plain loop. (The one-liner form silently produced an empty comparison.)
$perRoundFailed = @()
$allRoundsPassed = $true
foreach ($result in $results) {
    $failedKeys = @()
    foreach ($key in $result.checks.Keys) {
        if (-not $result.checks[$key]) { $failedKeys += $key }
    }
    $perRoundFailed += ,@($failedKeys)
    if ($failedKeys.Count -gt 0) { $allRoundsPassed = $false }
}
$final = [ordered]@{
    rounds = $Rounds;
    all_rounds_passed = $allRoundsPassed;
    rounds_are_consistent = $consistent;
    per_round_failed_checks = $perRoundFailed
}
$final | ConvertTo-Json -Depth 4 | Out-File (Join-Path $OutDir "checks.json") -Encoding utf8
Copy-Item (Join-Path $OutDir "round-1-checks.json") (Join-Path $OutDir "checks-round1.json") -Force
$s = ""; foreach ($k in $final.Keys) { $s += "$k=$($final[$k]) " }
Write-Host "[checks] $s"
Write-Host "[addon-smoke] per-round failed check counts: $((@($perRoundFailed | ForEach-Object { $_.Count })) -join ',')"
if (-not $allRoundsPassed) { throw "ADDON SMOKE FAILED: at least one round had failing checks (see $OutDir)" }
if (-not $consistent) { throw "ADDON SMOKE FAILED: rounds disagree: $($mismatches -join '; ')" }
Write-Host "[addon-smoke] PASSED ($Node, $Rounds rounds, consistent)"
