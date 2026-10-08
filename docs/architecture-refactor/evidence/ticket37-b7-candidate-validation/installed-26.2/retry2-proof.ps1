$ErrorActionPreference = 'Stop'
$root = 'D:\mcmodDemo\NekoJS-mult\build\ticket37-b7-installed-26.2'
$java = 'C:\Program Files\Java\jdk-25.0.2\bin\java.exe'
$python = 'C:\Users\11515\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$project = 'D:\mcmodDemo\NekoJS-mult'
$manifest = Get-Content -LiteralPath (Join-Path $project 'docs\architecture-refactor\evidence\ticket16-default-proxy-declarations\artifact-manifest.json') -Raw | ConvertFrom-Json
$artifact = @($manifest.artifacts | Where-Object path -like '*26.2.0/build/libs/nekojs-neoforge*')[0]
if ((Resolve-Path -LiteralPath $root).Path -ne $root) { throw 'Unexpected installed proof root' }
if ((Get-FileHash -LiteralPath (Join-Path $root 'mods\nekojs-neoforge-26.2.0-1.1.0-preview3.jar')).Hash -ne $artifact.sha256) { throw 'Installed artifact bytes changed' }
if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25891,25892 }).Count -ne 0) { throw 'Installed proof ports occupied' }
if (Test-Path -LiteralPath (Join-Path $root 'retry2-server-stdout.log')) { throw 'Preserve previous installed proof output' }
$process = Start-Process -FilePath $java -ArgumentList @('-Xms512m', '-Xmx2048m', '@win_args.txt', 'nogui') -WorkingDirectory $root -RedirectStandardOutput (Join-Path $root 'retry2-server-stdout.log') -RedirectStandardError (Join-Path $root 'retry2-server-stderr.log') -PassThru
Write-Output ('OWNED_INSTALLED_SERVER_PID=' + $process.Id)
$deadline = [datetime]::UtcNow.AddMinutes(4)
$done = $false
while ([datetime]::UtcNow -lt $deadline -and -not $process.HasExited) {
    if (Test-Path -LiteralPath (Join-Path $root 'retry2-server-stdout.log')) {
        if (Select-String -LiteralPath (Join-Path $root 'retry2-server-stdout.log') -Pattern 'Done \([0-9.]+s\)!' -Quiet) {
            $done = $true
            break
        }
    }
    Start-Sleep -Milliseconds 500
}
if (-not $done) {
    Get-Content -LiteralPath (Join-Path $root 'retry2-server-stdout.log') -Tail 45
    if (-not $process.HasExited) {
        $ownedProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
        if ($ownedProcess.ExecutablePath -eq $java -and $ownedProcess.CommandLine.Contains('@win_args.txt')) {
            Stop-Process -Id $process.Id
        }
    }
    throw 'Installed server did not reach Done; no domain proof is asserted'
}
Start-Sleep -Seconds 3
& $python (Join-Path $project 'build\ticket37-installed-proof.py') $root (Join-Path $project 'bench\perf\rcon.py') *> (Join-Path $root 'retry2-command-proof.txt')
$proofExit = $LASTEXITCODE
Get-Content -LiteralPath (Join-Path $root 'retry2-command-proof.txt') -Tail 50
if (-not $process.WaitForExit(90000)) { throw 'Owned installed server did not stop after normal RCON command' }
Write-Output ('INSTALLED_SERVER_EXIT=' + $process.ExitCode)
Get-Content -LiteralPath (Join-Path $root 'retry2-server-stdout.log') -Tail 28
if ($proofExit -ne 0) { throw ('Installed public-command proof failed: ' + $proofExit) }
if ($process.ExitCode -ne 0) { throw ('Installed server exited with failure: ' + $process.ExitCode) }
Write-Output 'EXACT_OFFICIAL_INSTALLED_SINGLE_SERVER_DOMAIN_PROOF_PASS'
