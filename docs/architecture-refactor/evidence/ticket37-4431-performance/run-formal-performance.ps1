$ErrorActionPreference = 'Stop'
$project = 'D:\mcmodDemo\NekoJS-mult'
$owned = Join-Path $project 'build\ticket37-perf-owned-ac810fa7'
$revision = '4431faeb2b404c914e66e85c41c2fd0ba3d14535'
$runRoot = Join-Path $owned 'versions\26.1.2\run'
$destination = Join-Path $project 'build\ticket37-perf-4431faeb'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.2'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
if (Test-Path -LiteralPath $destination) { throw 'Preserve existing performance evidence' }
if (@(git -C $owned status --porcelain).Count -ne 0) { throw 'Owned performance checkout is not clean' }
foreach ($path in @($project,(Join-Path $project 'build'),$owned,(Join-Path $owned 'versions'),(Join-Path $owned 'versions\26.1.2'),$runRoot)) {
    $item = Get-Item -LiteralPath $path -Force
    if ($item.FullName -ne [IO.Path]::GetFullPath($path) -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw "Unexpected owned path: $path" }
}
foreach ($relative in @('nekojs','.neko_probe','world')) {
    $candidate = Join-Path $runRoot $relative
    if (-not [IO.Path]::GetFullPath($candidate).StartsWith($runRoot + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Run target escaped owned root' }
    if (Test-Path -LiteralPath $candidate) {
        $items = @((Get-Item -LiteralPath $candidate -Force)) + @(Get-ChildItem -LiteralPath $candidate -Recurse -Force)
        if (@($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -ne 0) { throw "Reparse point under owned run target: $candidate" }
    }
}
if (@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($owned) }).Count -ne 0) { throw 'Existing owned game process must settle normally first' }
if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25871,25872,25897,25898 }).Count -ne 0) { throw 'Performance or installed proof ports occupied' }
& git -C $owned switch --detach $revision
if ($LASTEXITCODE -ne 0 -or (git -C $owned rev-parse HEAD).Trim() -ne $revision -or @(git -C $owned status --porcelain).Count -ne 0) { throw 'Exact owned source checkout failed' }
$sampler = Join-Path $owned 'bench\perf\sample.ps1'
if ((Get-FileHash -LiteralPath $sampler).Hash -ne '1F9C5D14A1A586DA4B3F1CA8E71B2407CE089EE8BEE185418963E1B5745DF0B4') { throw 'Benchmark harness changed' }
New-Item -ItemType Directory -Path $destination | Out-Null
$revision | Set-Content -LiteralPath (Join-Path $destination 'source-revision.txt') -Encoding ascii
Get-ChildItem -LiteralPath (Join-Path $owned 'bench\perf\fixtures\nekojs') -Recurse -File | ForEach-Object { [pscustomobject]@{path=$_.FullName.Substring($owned.Length+1);sha256=(Get-FileHash -LiteralPath $_.FullName).Hash} } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destination 'fixture-hashes.json') -Encoding utf8
Push-Location $owned
try {
    foreach ($group in @('reload-1','reload-2','startup')) {
        if (@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 25871,25872 }).Count -ne 0) { throw 'Previous formal session did not release ports' }
        $mode = if ($group -eq 'startup') { 'startup' } else { 'reload' }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $sampler -Mode $mode -Node 26.1.2 -Warmup 2 -Samples 5 -Reloads 5 -GradleUserHome 'D:\mcmodDemo\NekoJS\.gradle-perf02' -ServerPort 25871 -RconPort 25872 *> (Join-Path $destination "$group.txt")
        $result = $LASTEXITCODE
        Write-Output "FORMAL_GROUP=$group EXIT=$result"
        Get-Content -LiteralPath (Join-Path $destination "$group.txt") -Tail 4
        if ($result -ne 0) { throw "Formal sampler failed: $group" }
    }
} finally { Pop-Location }
