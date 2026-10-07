$ErrorActionPreference = 'Stop'
$project = 'D:\mcmodDemo\NekoJS-mult'
$owned = Join-Path $project 'build\ticket37-perf-owned-ac810fa7'
$runExpected = Join-Path $owned 'versions\26.1.2\run'
$javaHome = 'C:\Program Files\Java\jdk-25.0.2'
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"
$profileDir = Join-Path $project 'build\ticket37-reload-profile-current-1b406626'
if (Test-Path -LiteralPath $profileDir) { throw 'Existing diagnostic directory must be preserved' }
if ((git -C $owned rev-parse HEAD).Trim() -ne '1b406626662b7c6334eb05fed0d964563eb10f01' -or @(git -C $owned status --porcelain).Count -ne 0) { throw 'Diagnostic source is not exact and clean' }
if ((Resolve-Path -LiteralPath $runExpected).Path -ne $runExpected) { throw 'Unexpected owned run root' }
if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25871,25872 }).Count -ne 0) { throw 'Benchmark ports occupied' }
if ((Get-FileHash -LiteralPath (Join-Path $owned 'bench\perf\sample.ps1')).Hash -ne '1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4') { throw 'Benchmark harness changed' }
New-Item -ItemType Directory -Path $profileDir | Out-Null
$recording = Join-Path $profileDir 'reload.jfr'
$settings = Join-Path $profileDir 'reload.jfc'
& "$javaHome\bin\jfr.exe" configure --input "$javaHome\lib\jfr\profile.jfc" --output $settings 'jdk.ExecutionSample#period=1ms' 'jdk.NativeMethodSample#period=1ms' 'jdk.ThreadPark#threshold=1ms' 'jdk.JavaMonitorWait#threshold=1ms' 'jdk.JavaMonitorEnter#threshold=1ms' 'jdk.InitialEnvironmentVariable#enabled=false' 'jdk.InitialSystemProperty#enabled=false' 'jdk.InitialSecurityProperty#enabled=false' 'jdk.SystemProcess#enabled=false' 'jdk.JVMInformation#enabled=false'
if ($LASTEXITCODE -ne 0) { throw 'JFR settings generation failed' }
$stdout = Join-Path $profileDir 'sampler-stdout.txt'
$stderr = Join-Path $profileDir 'sampler-stderr.txt'
$arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $owned 'bench\perf\sample.ps1'), '-Mode', 'reload', '-Node', '26.1.2', '-Reloads', '10', '-GradleUserHome', 'D:\mcmodDemo\NekoJS\.gradle-perf02', '-ServerPort', '25871', '-RconPort', '25872')
$sampler = Start-Process -FilePath 'powershell.exe' -ArgumentList $arguments -WorkingDirectory $owned -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
Write-Output ('DIAGNOSTIC_ONLY_SAMPLER_PID=' + $sampler.Id)
$attachDeadline = [datetime]::UtcNow.AddMinutes(4)
$gameProcessId = $null
while ([datetime]::UtcNow -lt $attachDeadline -and -not $sampler.HasExited) {
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object LocalPort -eq 25871)
    if ($listeners.Count -gt 0) {
        $candidateId = $listeners[0].OwningProcess
        $candidate = Get-CimInstance Win32_Process -Filter "ProcessId=$candidateId"
        if ($candidate.Name -ne 'java.exe' -or $candidate.ExecutablePath -ne "$javaHome\bin\java.exe" -or -not $candidate.CommandLine.Contains($owned)) { throw 'Unexpected game process; refuse to attach' }
        $gameProcessId = $candidateId
        & "$javaHome\bin\jcmd.exe" $gameProcessId JFR.start 'name=neko-current-reload-diagnostic' "settings=$settings" 'disk=true' 'dumponexit=true' "filename=$recording"
        if ($LASTEXITCODE -ne 0) { throw 'Owned game JFR attachment failed' }
        Write-Output ('OWNED_GAME_JFR_PID=' + $gameProcessId)
        break
    }
    Start-Sleep -Milliseconds 250
}
if (-not $sampler.WaitForExit(240000)) { throw 'Diagnostic sampler did not settle within four minutes' }
Get-Content -LiteralPath $stdout -Tail 42
if ((Get-Item -LiteralPath $stderr).Length -gt 0) { Get-Content -LiteralPath $stderr -Tail 15 }
if ($sampler.ExitCode -ne 0) { throw ('Diagnostic sampler failed: ' + $sampler.ExitCode) }
if ($null -eq $gameProcessId -or -not (Test-Path -LiteralPath $recording)) { throw 'No owned game recording captured' }
$safeRecording = Join-Path $profileDir 'safe-profile.jfr'
& "$javaHome\bin\jfr.exe" scrub --include-events 'jdk.ExecutionSample,jdk.NativeMethodSample,jdk.ThreadPark,jdk.JavaMonitorWait,jdk.JavaMonitorEnter,jdk.GCPhasePause' $recording $safeRecording
if ($LASTEXITCODE -ne 0) { throw 'Safe event whitelist extraction failed' }
& "$javaHome\bin\jfr.exe" summary $safeRecording
if ($LASTEXITCODE -ne 0) { throw 'Safe JFR summary failed' }
Write-Output ('SAFE_DIAGNOSTIC_RECORDING=' + $safeRecording)
Write-Output ('SAFE_RECORDING_BYTES=' + (Get-Item -LiteralPath $safeRecording).Length)
