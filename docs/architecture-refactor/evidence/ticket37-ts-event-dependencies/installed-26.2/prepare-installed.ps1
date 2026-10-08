$ErrorActionPreference = 'Stop'
$project = 'D:\mcmodDemo\NekoJS-mult'
$previous = Join-Path $project 'build\ticket37-python-installed-26.2'
$root = Join-Path $project 'build\ticket37-ts-installed-26.2'
$expectedRoot = [System.IO.Path]::GetFullPath($root)
if ($expectedRoot -ne 'D:\mcmodDemo\NekoJS-mult\build\ticket37-ts-installed-26.2') { throw 'Unexpected proof root' }
if (Test-Path -LiteralPath $root) { throw 'Preserve existing proof directory' }
New-Item -ItemType Directory -Path $root | Out-Null
New-Item -ItemType Junction -Path (Join-Path $root 'libraries') -Target (Join-Path $previous 'libraries') | Out-Null
foreach ($relative in @('mods', 'nekojs\config', 'nekojs\server_scripts')) {
    New-Item -ItemType Directory -Path (Join-Path $root $relative) | Out-Null
}
Copy-Item -LiteralPath (Join-Path $previous 'win_args.txt') -Destination (Join-Path $root 'win_args.txt')
Copy-Item -LiteralPath (Join-Path $previous 'mods\graal-1504336-8762962.jar') -Destination (Join-Path $root 'mods')
Copy-Item -LiteralPath (Join-Path $project 'versions\26.2.0\build\libs\nekojs-neoforge-26.2.0-1.1.0-preview3.jar') -Destination (Join-Path $root 'mods')
Copy-Item -LiteralPath (Join-Path $previous 'nekojs\config\engine.toml') -Destination (Join-Path $root 'nekojs\config')
Copy-Item -LiteralPath (Join-Path $previous 'nekojs\server_scripts\proof-dynamic.js') -Destination (Join-Path $root 'nekojs\server_scripts')
$credential = [guid]::NewGuid().ToString('N')
@('eula=true') | Set-Content -LiteralPath (Join-Path $root 'eula.txt') -Encoding utf8
@('server-ip=127.0.0.1','server-port=25893','enable-rcon=true','rcon.port=25894',"rcon.password=$credential",'level-name=ts-event-dependencies-proof-world','online-mode=false','view-distance=3','simulation-distance=3','max-players=4','spawn-protection=0','motd=Owned TS event dependency proof') | Set-Content -LiteralPath (Join-Path $root 'server.properties') -Encoding utf8
Write-Output ('INSTALLED_ARTIFACT_SHA256=' + (Get-FileHash -LiteralPath (Join-Path $root 'mods\nekojs-neoforge-26.2.0-1.1.0-preview3.jar')).Hash)
