$ErrorActionPreference = 'Stop'
$owned = 'D:\mcmodDemo\NekoJS-mult\build\ticket37-perf-owned-ac810fa7'
$destination = 'D:\mcmodDemo\NekoJS-mult\build\ticket37-perf-085698ab'
$revision = '085698ab1de7e37438b16823ac538ca15a50f87e'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
foreach ($group in @('reload-confirmed-1', 'reload-confirmed-2', 'startup-confirmed')) {
    if ((git -C $owned rev-parse HEAD).Trim() -ne $revision -or @(git -C $owned status --porcelain).Count -ne 0) { throw 'Exact source changed' }
    if ((Get-FileHash -LiteralPath (Join-Path $owned 'bench\perf\sample.ps1')).Hash -ne '1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4') { throw 'Sampler changed' }
    if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25871,25872 }).Count -ne 0) { throw 'Formal ports occupied' }
    if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($owned) }).Count -ne 0) { throw 'Existing owned game process must settle normally first' }
    $runRoot = Join-Path $owned 'versions\26.1.2\run'
    foreach ($path in @($owned, (Join-Path $owned 'versions'), (Join-Path $owned 'versions\26.1.2'), $runRoot)) {
        $item = Get-Item -LiteralPath $path -Force
        if ($item.FullName -ne [IO.Path]::GetFullPath($path) -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Unexpected owned path: $path" }
    }
    foreach ($relative in @('nekojs', '.neko_probe', 'world')) {
        $candidate = Join-Path $runRoot $relative
        if (-not [IO.Path]::GetFullPath($candidate).StartsWith($runRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Run target escaped owned root' }
        if (Test-Path -LiteralPath $candidate) {
            $items = @((Get-Item -LiteralPath $candidate -Force)) + @(Get-ChildItem -LiteralPath $candidate -Recurse -Force)
            if (@($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -ne 0) { throw "Reparse point under owned run target: $candidate" }
        }
    }
    $output = Join-Path $destination "$group.txt"
    if (Test-Path -LiteralPath $output) { throw 'Preserve existing group output' }
    $mode = if ($group -eq 'startup-confirmed') { 'startup' } else { 'reload' }
    Write-Output "FORMAL_GROUP_START=$group"
    Push-Location -LiteralPath $owned
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $owned 'bench\perf\sample.ps1') -Mode $mode -Node 26.1.2 -Warmup 2 -Samples 5 -Reloads 5 -GradleUserHome 'D:\mcmodDemo\NekoJS\.gradle-perf02' -ServerPort 25871 -RconPort 25872 *> $output
        $samplerExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    Write-Output "FORMAL_GROUP=$group EXIT=$samplerExit"
    Get-Content -LiteralPath $output -Tail 4
    if ($samplerExit -ne 0) { throw 'Formal sampler failed' }
}
