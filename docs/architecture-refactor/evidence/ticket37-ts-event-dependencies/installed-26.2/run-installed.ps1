$ErrorActionPreference = 'Stop'
$project = 'D:\mcmodDemo\NekoJS-mult'
$root = Join-Path $project 'build\ticket37-ts-installed-26.2'
$java = 'C:\Program Files\Java\jdk-25.0.2\bin\java.exe'
$python = 'C:\Users\11515\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
if ((Resolve-Path -LiteralPath $root).Path -ne $root) { throw 'Unexpected installed proof root' }
$expectedHash = (Get-FileHash -LiteralPath (Join-Path $project 'versions\26.2.0\build\libs\nekojs-neoforge-26.2.0-1.1.0-preview3.jar')).Hash
if ((Get-FileHash -LiteralPath (Join-Path $root 'mods\nekojs-neoforge-26.2.0-1.1.0-preview3.jar')).Hash -ne $expectedHash) { throw 'Installed artifact bytes differ from current build' }
if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25893,25894 }).Count -ne 0) { throw 'Proof ports occupied' }
if (Test-Path -LiteralPath (Join-Path $root 'server-stdout.log')) { throw 'Preserve previous output' }
$process = Start-Process -FilePath $java -WindowStyle Hidden -ArgumentList @('-Xms512m','-Xmx2048m','@win_args.txt','nogui') -WorkingDirectory $root -RedirectStandardOutput (Join-Path $root 'server-stdout.log') -RedirectStandardError (Join-Path $root 'server-stderr.log') -PassThru
Write-Output ('OWNED_TS_SERVER_PID=' + $process.Id)
$deadline = [datetime]::UtcNow.AddMinutes(4)
$done = $false
while ([datetime]::UtcNow -lt $deadline -and -not $process.HasExited) {
    if ((Test-Path -LiteralPath (Join-Path $root 'server-stdout.log')) -and (Select-String -LiteralPath (Join-Path $root 'server-stdout.log') -Pattern 'Done \([0-9.]+s\)!' -Quiet)) { $done = $true; break }
    Start-Sleep -Milliseconds 500
}
if (-not $done) { throw 'Owned installed server did not reach Done; inspect without stopping unrelated processes' }
& $python (Join-Path $project 'build\ticket37-installed-proof.py') $root (Join-Path $project 'bench\perf\rcon.py') *> (Join-Path $root 'command-proof.txt')
$proofExit = $LASTEXITCODE
if (-not $process.WaitForExit(60000)) { throw 'Owned server failed normal RCON shutdown' }
Get-Content -LiteralPath (Join-Path $root 'command-proof.txt') -Tail 7
Write-Output ('OWNED_TS_SERVER_EXIT=' + $process.ExitCode)
if ($proofExit -ne 0 -or $process.ExitCode -ne 0) { throw 'Installed proof failed; preserve raw evidence' }
