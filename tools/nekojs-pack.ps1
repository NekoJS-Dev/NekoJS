param(
    [Parameter(Mandatory = $true)][ValidateSet('create', 'validate', 'archive', 'install', 'jar', 'schema')][string]$Action,
    [Parameter(Mandatory = $true)][string]$InputPath,
    [string]$OutputPath,
    [string]$Id,
    [string]$Types = 'server',
    [string]$Loader,
    [string]$Minecraft,
    [switch]$Replace
)

$ErrorActionPreference = 'Stop'
$packRepository = Split-Path -Parent $PSScriptRoot
$packArguments = @($Action, $InputPath)
switch ($Action) {
    'create' {
        if (!$Id) { throw 'Package creation requires -Id.' }
        $packArguments += @($Id, $Types)
    }
    'archive' {
        if (!$OutputPath) { throw 'Package archive requires -OutputPath.' }
        $packArguments += $OutputPath
    }
    'install' {
        if (!$OutputPath) { throw 'Package install requires a destination packs root in -OutputPath.' }
        $packArguments += $OutputPath
        if ($Replace) { $packArguments += 'replace' }
    }
    'jar' {
        if (!$OutputPath -or !$Loader -or !$Minecraft) { throw 'Package jar requires -OutputPath, -Loader and -Minecraft.' }
        $packArguments += @($OutputPath, $Loader, $Minecraft)
    }
}
$packArgumentDirectory = Join-Path $packRepository 'build\pack-tool-arguments'
[IO.Directory]::CreateDirectory($packArgumentDirectory) | Out-Null
$packArgumentFile = Join-Path $packArgumentDirectory (([Guid]::NewGuid().ToString('N')) + '.json')
[IO.File]::WriteAllText($packArgumentFile, (ConvertTo-Json -InputObject $packArguments -Compress), [Text.UTF8Encoding]::new($false))
Push-Location $packRepository
try {
    & (Join-Path $packRepository 'gradlew.bat') ':common:packTool' "-PpackArgsFile=$packArgumentFile" '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Package tool failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
    Remove-Item -LiteralPath $packArgumentFile
}
