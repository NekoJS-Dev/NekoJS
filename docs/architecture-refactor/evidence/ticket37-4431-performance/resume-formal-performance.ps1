$ErrorActionPreference='Stop'
$owned='D:\mcmodDemo\NekoJS-mult\build\ticket37-perf-owned-ac810fa7'
$destination='D:\mcmodDemo\NekoJS-mult\build\ticket37-perf-4431faeb'
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
foreach ($group in @('reload-2','startup')) {
    if ((git -C $owned rev-parse HEAD).Trim() -ne '4431faeb2b404c914e66e85c41c2fd0ba3d14535' -or @(git -C $owned status --porcelain).Count -ne 0) { throw 'Source changed' }
    if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25871,25872 }).Count -ne 0) { throw 'Formal ports occupied' }
    if (Test-Path -LiteralPath (Join-Path $destination "$group.txt")) { throw 'Preserve existing group output' }
    $mode=if($group -eq 'startup') {'startup'}else{'reload'}
    $args=@('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $owned 'bench\perf\sample.ps1'),'-Mode',$mode,'-Node','26.1.2','-Warmup','2','-Samples','5','-Reloads','5','-GradleUserHome','D:\mcmodDemo\NekoJS\.gradle-perf02','-ServerPort','25871','-RconPort','25872')
    $sampler=Start-Process -FilePath powershell.exe -WindowStyle Hidden -ArgumentList $args -WorkingDirectory $owned -RedirectStandardOutput (Join-Path $destination "$group.txt") -RedirectStandardError (Join-Path $destination "$group-stderr.txt") -PassThru
    Write-Output "OWNED_SAMPLER_GROUP=$group PID=$($sampler.Id)"
    while(-not $sampler.HasExited) { Start-Sleep -Milliseconds 500; $sampler.Refresh() }
    Write-Output "FORMAL_GROUP=$group EXIT=$($sampler.ExitCode)"
    Get-Content -LiteralPath (Join-Path $destination "$group.txt") -Tail 4
    if($sampler.ExitCode -ne 0) { throw 'Formal sampler failed' }
}
