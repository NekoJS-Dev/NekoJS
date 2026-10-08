$ErrorActionPreference = 'Stop'
$project = 'D:\mcmodDemo\NekoJS-mult'
$root = Join-Path $project 'build\ticket37-python-installed-26.2'
$java = 'C:\Program Files\Java\jdk-25.0.2\bin\java.exe'
$python = 'C:\Users\11515\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$expectedHash = 'E75CB9B933CD469F3A4D605EA500963F0442AC464613D8889C2CEECB762DF78C'
if ((Resolve-Path -LiteralPath $root).Path -ne $root) { throw 'Unexpected installed Python proof root' }
if ((Get-FileHash -LiteralPath (Join-Path $root 'mods\nekojs-neoforge-26.2.0-1.1.0-preview3.jar')).Hash -ne $expectedHash) { throw 'Installed repaired artifact bytes changed' }
if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25891,25892 }).Count -ne 0) { throw 'Installed verification ports occupied' }
if (Test-Path -LiteralPath (Join-Path $root 'server-stdout.log')) { throw 'Preserve prior installed Python output' }
$process = Start-Process -FilePath $java -ArgumentList @('-Xms512m','-Xmx2048m','@win_args.txt','nogui') -WorkingDirectory $root -RedirectStandardOutput (Join-Path $root 'server-stdout.log') -RedirectStandardError (Join-Path $root 'server-stderr.log') -PassThru
Write-Output ('OWNED_INSTALLED_PYTHON_SERVER_PID=' + $process.Id)
$deadline = [datetime]::UtcNow.AddMinutes(4)
$done = $false
while ([datetime]::UtcNow -lt $deadline -and -not $process.HasExited) {
    if (Test-Path -LiteralPath (Join-Path $root 'server-stdout.log')) {
        if (Select-String -LiteralPath (Join-Path $root 'server-stdout.log') -Pattern 'Done \([0-9.]+s\)!' -Quiet) {
            $done = $true
            break
        }
    }
    Start-Sleep -Milliseconds 500
}
if (-not $done) {
    Get-Content -LiteralPath (Join-Path $root 'server-stdout.log') -Tail 35
    if (-not $process.HasExited) { $process.Kill() }
    throw 'Owned installed repaired server failed bounded boot; no PASS asserted'
}
Start-Sleep -Seconds 3
& $python (Join-Path $project 'build\ticket37-installed-proof.py') $root (Join-Path $project 'bench\perf\rcon.py') *> (Join-Path $root 'command-proof.txt')
$domainExit = $LASTEXITCODE
if (-not $process.WaitForExit(90000)) { throw 'Owned repaired server failed normal RCON shutdown' }
Get-Content -LiteralPath (Join-Path $root 'command-proof.txt') -Tail 12
Write-Output ('INSTALLED_REPAIRED_SERVER_EXIT=' + $process.ExitCode)
if ($domainExit -ne 0 -or $process.ExitCode -ne 0) { throw 'Repaired installed domain proof failed; preserve raw output' }
& $python (Join-Path $project 'build\ticket37-check-live-probe.py') $root *> (Join-Path $root 'live-python-audit.txt')
$auditExit = $LASTEXITCODE
Get-Content -LiteralPath (Join-Path $root 'live-python-audit.txt')
if ($auditExit -ne 0) { throw ('Whole installed Python syntax audit still failed: ' + $auditExit) }
Write-Output 'EXACT_INSTALLED_ALL_PYTHON_AST_AND_DYNAMIC_DOMAIN_REPLAY_PASS'
