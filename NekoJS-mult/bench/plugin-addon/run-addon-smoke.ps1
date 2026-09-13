# NekoJS ticket 08 — external addon discovery / reload-survival smoke (bench/plugin-addon/run-addon-smoke.ps1)
#
# Proves that a REAL external addon (a standalone jar in run/mods, discovered through the loader's
# own plugin discovery path) enters the Plugin Runtime, contributes through pre-existing extension
# points, is consumed from scripts, and survives a normal /nekojs reload without re-bootstrap.
#
# Channel: RCON (same conclusion as bench/smoke: gradlew stdin is unreachable).
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/run-addon-smoke.ps1 -Node 26.1.2-fabric

param(
    [Parameter(Mandatory = $true)][string]$Node,
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
    "26.1.2"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run";        $AddonJar = "nekojs08addon-1.0.0-neoforge.jar" }
    "1.21.1"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run";        $AddonJar = "nekojs08addon-1.0.0-neoforge.jar" }
    "26.2.0"        { $RunDir = Join-Path $ProjectDir "versions\$Node\run";        $AddonJar = "nekojs08addon-1.0.0-neoforge.jar" }
    "26.1.2-fabric" { $RunDir = Join-Path $ProjectDir "versions\$Node\run-server"; $AddonJar = "nekojs08addon-1.0.0-fabric.jar" }
    default { throw "unsupported node '$Node'" }
}

$GitRev = (& git -C $ProjectDir rev-parse HEAD).Trim().Substring(0, 8)
"utc: $((Get-Date).ToUniversalTime().ToString('o'))`ngit rev: $GitRev`nnode: $Node`nrun dir: $RunDir" |
    Out-File (Join-Path $OutDir "env.txt") -Encoding utf8
Write-Host "[addon-smoke] node=$Node rev=$GitRev run=$RunDir out=$OutDir"

# ---------- deploy: addon jar + consuming scripts ----------
$nekoDir = Join-Path $RunDir "nekojs"
if (Test-Path $nekoDir) { Remove-Item -Recurse -Force $nekoDir -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $nekoDir | Out-Null
& robocopy (Join-Path $PSScriptRoot "fixtures\nekojs") $nekoDir /E /NJH /NJS /NDL /NFL /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw "robocopy fixtures failed with exit $LASTEXITCODE" }
$modsDir = Join-Path $RunDir "mods"
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
# replace only our own fixture jar so unrelated third-party mods in the dev workspace are left alone
Get-ChildItem $modsDir -Filter "nekojs08addon-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item (Join-Path $PSScriptRoot "build\$AddonJar") $modsDir -Force
if (-not (Test-Path (Join-Path $modsDir $AddonJar))) { throw "addon jar not found: build/$AddonJar (run build-addon.ps1 first)" }
$nekoLogDir = Join-Path $RunDir "logs\nekojs"
if (Test-Path $nekoLogDir) { Remove-Item -Recurse -Force $nekoLogDir -ErrorAction SilentlyContinue }
Set-Content -Path (Join-Path $RunDir "eula.txt") -Value "eula=true" -Encoding ascii
# server.properties: the RCON channel this runner depends on (same conclusion as bench/smoke)
$props = "server-port=$ServerPort`nmotd=nekojs addon08 smoke`nonline-mode=false`ndifficulty=peaceful`ngamemode=survival`nenable-rcon=true`nrcon.port=$RconPort`nrcon.password=$RconPass`nspawn-protection=0`nview-distance=6`npause-when-empty-seconds=-1`nlevel-name=world"
Set-Content -Path (Join-Path $RunDir "server.properties") -Value $props -Encoding ascii
Write-Host "[addon-smoke] deployed $AddonJar + fixtures"

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

# ---------- launch ----------
$stdoutLog = Join-Path $OutDir "server-stdout.log"
$stderrLog = Join-Path $OutDir "server-stderr.log"
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "cmd.exe"
$psi.Arguments = "/c gradlew.bat :$Node`:runServer --console=plain > `"$stdoutLog`" 2> `"$stderrLog`""
$psi.WorkingDirectory = $ProjectDir
$psi.UseShellExecute = $false
$proc = [System.Diagnostics.Process]::Start($psi)
Write-Host "[addon-smoke] started pid=$($proc.Id)"

$doneS = $null
$deadline = [DateTime]::UtcNow.AddSeconds($BootTimeoutSec)
while ($true) {
    if ($proc.HasExited) { break }
    if ([DateTime]::UtcNow -gt $deadline) {
        Write-Host "[addon-smoke] boot timeout, killing"
        try { & taskkill /F /T /PID $proc.Id | Out-Null } catch {}
        break
    }
    $doneLine = Get-Content $stdoutLog -Tail 80 -ErrorAction SilentlyContinue |
        Select-String -Pattern 'Done \((\d+\.\d+)s\)!' | Select-Object -Last 1
    if ($null -ne $doneLine) { $doneS = $doneLine.Matches[0].Groups[1].Value; break }
    Start-Sleep -Milliseconds 300
}
if ($null -eq $doneS) { throw "server did not reach 'Done' (see $stdoutLog)" }
Write-Host "[addon-smoke] server started (Done(${doneS}s))"
Start-Sleep -Seconds 2

# ---------- counting helpers ----------
function Read-NekoLog([string]$Name) {
    $p = Join-Path $nekoLogDir $Name
    if (-not (Test-Path $p)) { return "" }
    # the game process keeps these logs open for writing; open shared instead of exclusive
    $stream = [System.IO.File]::Open($p, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::GetEncoding(936))
        return $reader.ReadToEnd()
    } finally {
        $stream.Dispose()
    }
}
function Count-Text([string]$Text, [string]$Pattern) {
    if ([string]::IsNullOrEmpty($Text)) { return 0 }
    return ([regex]::Matches($Text, $Pattern)).Count
}
function Count-Stdout([string]$Pattern) {
    return @(Select-String -Path $stdoutLog -Pattern $Pattern -ErrorAction SilentlyContinue).Count
}

$preStartup = Read-NekoLog "startup.log"
$preServer = Read-NekoLog "server.log"
$pre = [ordered]@{
    discovered       = Count-Stdout "NEKOJS-ADDON08 plugin-instantiated";
    instanceMarker   = Count-Stdout "NEKOJS-ADDON08 plugin-instantiated instance=1";
    bindingReg       = Count-Stdout "NEKOJS-ADDON08 binding-registered";
    startupConsumed  = Count-Text $preStartup "NEKOJS-ADDON08 startup-binding";
    serverConsumed   = Count-Text $preServer "NEKOJS-ADDON08 server-binding";
    moduleConsumed   = Count-Text $preServer "NEKOJS-ADDON08 server-module";
    initStartup      = Count-Stdout "NEKOJS-ADDON08 initStartup instances=";
    requiredModsLeak = Count-Stdout "required-mods-filter-LEAKED";
    clientOnlyLeak   = Count-Stdout "client-only-filter-LEAKED";
    filteredRequired = Count-Text $preStartup "NEKOJS-ADDON08 startup-filtered-requiredmods-absent true";
    filteredClient   = Count-Text $preStartup "NEKOJS-ADDON08 startup-filtered-clientonly-absent true";
    scriptErrors     = Count-Text $preServer "\[ERROR\]"
}
# ---------- normal server reload (must NOT re-bootstrap the plugin runtime) ----------
$reloadRcon = Invoke-Rcon -Command "nekojs reload server"
$reloadRcon | Out-File (Join-Path $OutDir "rcon-reload-response.txt") -Encoding utf8
Write-Host "[addon-smoke] nekojs reload server -> $reloadRcon"
$reloadDeadline = [DateTime]::UtcNow.AddSeconds(120)
while ([DateTime]::UtcNow -lt $reloadDeadline) {
    if ($proc.HasExited) { break }
    $now = Read-NekoLog "server.log"
    if ((Count-Text $now "NEKOJS-ADDON08 server-binding") -gt $pre.serverConsumed) { break }
    Start-Sleep -Milliseconds 200
}
Start-Sleep -Seconds 2

# best-effort second opinion: the reload response and the per-type script log are the primary
# error signals, so a failed error query must not fail the whole smoke run.
$errorsRcon = ""
try { $errorsRcon = Invoke-Rcon -Command "nekojs error" } catch { $errorsRcon = "RCON-QUERY-FAILED: $_" }
$errorsRcon | Out-File (Join-Path $OutDir "rcon-errors-response.txt") -Encoding utf8

$postStartup = Read-NekoLog "startup.log"
$postServer = Read-NekoLog "server.log"
$post = [ordered]@{
    discovered       = Count-Stdout "NEKOJS-ADDON08 plugin-instantiated";
    instanceMarker   = Count-Stdout "NEKOJS-ADDON08 plugin-instantiated instance=1";
    bindingReg       = Count-Stdout "NEKOJS-ADDON08 binding-registered";
    startupConsumed  = Count-Text $postStartup "NEKOJS-ADDON08 startup-binding";
    serverConsumed   = Count-Text $postServer "NEKOJS-ADDON08 server-binding";
    moduleConsumed   = Count-Text $postServer "NEKOJS-ADDON08 server-module";
    initStartup      = Count-Stdout "NEKOJS-ADDON08 initStartup instances=";
    requiredModsLeak = Count-Stdout "required-mods-filter-LEAKED";
    clientOnlyLeak   = Count-Stdout "client-only-filter-LEAKED";
    filteredRequired = Count-Text $postStartup "NEKOJS-ADDON08 startup-filtered-requiredmods-absent true";
    filteredClient   = Count-Text $postStartup "NEKOJS-ADDON08 startup-filtered-clientonly-absent true";
    scriptErrors     = Count-Text $postServer "\[ERROR\]";
}

$stopChannel = Stop-Server $proc
$exitCode = $proc.ExitCode
Write-Host "[addon-smoke] stopped via $stopChannel exitCode=$exitCode"

# ---------- verdict ----------
# Loader-specific discovery shapes (recorded, not papered over):
#   NeoForge: FML's annotation scan instantiates the plugin class exactly once.
#   Fabric:   FabricLoader instantiates the entrypoint once per declared entrypoint key (the
#             fixture declares a single NekoJS key, so the count is 1), and the loader's own
#             pre-launch probe constructs one extra throwaway instance that NekoJS never
#             discovers. Both numbers are asserted, so neither drift is silently accepted.
$expectedDiscovery = if ($Node -match "fabric") { 2 } else { 1 }
$discoveredTotal = $post.discovered
$checks = [ordered]@{
    server_started                 = ($null -ne $doneS);
    addon_instances_are_deduped    = ($post.instanceMarker -eq 1 -and $discoveredTotal -eq $expectedDiscovery);
    addon_bootstrap_once           = ($pre.bindingReg -eq $post.bindingReg -and $pre.bindingReg -ge 1);
    addon_init_once                = ($pre.initStartup -eq 1 -and $post.initStartup -eq 1);
    binding_consumed_by_script     = ($pre.startupConsumed -ge 1 -and $pre.serverConsumed -ge 1);
    node_module_consumed_by_script = ($pre.moduleConsumed -ge 1);
    binding_alive_after_reload     = ($post.serverConsumed -gt $pre.serverConsumed);
    module_alive_after_reload      = ($post.moduleConsumed -gt $pre.moduleConsumed);
    addon_required_mods_filter_holds = ($pre.filteredRequired -ge 1 -and $post.requiredModsLeak -eq 0);
    addon_client_only_filter_holds   = ($pre.filteredClient -ge 1 -and $post.clientOnlyLeak -eq 0);
    no_script_errors               = ($post.scriptErrors -eq 0);
    clean_exit                     = ($stopChannel -eq "rcon")
}
$checks | ConvertTo-Json | Out-File (Join-Path $OutDir "checks.json") -Encoding utf8
[ordered]@{ pre = $pre; post = $post } | ConvertTo-Json -Depth 5 | Out-File (Join-Path $OutDir "counts.json") -Encoding utf8
$s = ""
foreach ($k in $checks.Keys) { $s += "$k=$($checks[$k]) " }
Write-Host "[checks] $s"
$failed = @($checks.Keys | Where-Object { -not $checks[$_] })
if ($failed.Count -gt 0) { throw "ADDON SMOKE FAILED: $($failed -join ', ')" }
Write-Host "[addon-smoke] PASSED ($Node)"
