# NekoJS ticket 08 — test-only external addon artifact builder.
#
# Builds bench/plugin-addon/fixture against the *platform fat jar* (plus slf4j and Graal, which the
# platform already provides) — exactly the dependency face a future third-party NekoJS addon has.
# No Gradle project is added for it; the fixture is deliberately a plain javac+jar invocation so it
# can never leak into any production jar.
#
# Two outputs from the same classes:
#   nekojs08addon-1.0.0-neoforge.jar  — META-INF/neoforge.mods.toml (FML annotation/metadata scan)
#   nekojs08addon-1.0.0-fabric.jar    — fabric.mod.json with the `nekojs` entrypoint
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File bench/plugin-addon/build-addon.ps1 [-Jar <jar>]

param(
    [string]$Jar = ""
)

$ErrorActionPreference = "Stop"
$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$FixtureDir = Join-Path $PSScriptRoot "fixture"
$BuildDir = Join-Path $PSScriptRoot "build"

# ---- locate a JDK 25 (the jar is packaged for Java 25; javac must target 25) ----------------
$javac = $null
foreach ($candidate in @(
        (Join-Path $env:ProgramFiles "Java\jdk-25.0.2\bin"),
        (Join-Path $env:ProgramFiles "Java\jdk-25\bin"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium\jdk-25*\bin")
    )) {
    $resolved = Get-ChildItem -Path $candidate -Filter javac.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($resolved) { $javac = $resolved.FullName; break }
}
if (-not $javac) {
    $javac = (Get-Command javac.exe -ErrorAction SilentlyContinue).Source
}
if (-not $javac) { throw "no javac found; install JDK 25" }
$jarTool = Join-Path (Split-Path $javac) "jar.exe"

# ---- resolve the platform fat jar (AC3 target publishing shape: fat jar) -------------------
if (-not $Jar) {
    $Jar = Get-ChildItem (Join-Path $ProjectDir "versions\26.1.2\build\libs") -Filter "nekojs-neoforge-*.jar" |
        Where-Object { $_.Name -notmatch "sources|javadoc" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Jar -or -not (Test-Path $Jar)) {
    throw "platform fat jar not found — run `gradlew :26.1.2:jar` first (looked in versions/26.1.2/build/libs)"
}

$slf4j = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.slf4j\slf4j-api\2.0.17\d9e58ac9c7779ba3bf8142aff6c830617a7fe60f\slf4j-api-2.0.17.jar"
if (-not (Test-Path $slf4j)) {
    $slf4j = Get-ChildItem (Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.slf4j\slf4j-api") -Recurse -Filter "slf4j-api-2.*.jar" |
        Where-Object { $_.Name -notmatch "sources" } | Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $slf4j) { throw "slf4j-api jar not found in the Gradle cache" }

if (Test-Path $BuildDir) { Remove-Item -Recurse -Force $BuildDir }
$classes = Join-Path $BuildDir "classes"
New-Item -ItemType Directory -Force -Path $classes | Out-Null

Write-Host "[addon] javac  = $javac"
Write-Host "[addon] jarhub = $Jar"
Write-Host "[addon] slf4j  = $slf4j"

$sources = Get-ChildItem (Join-Path $FixtureDir "src\main\java") -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName
& $javac -encoding UTF-8 -parameters --release 25 -Xlint:all -cp "$Jar;$slf4j" -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit $LASTEXITCODE" }

# ---- package both loader flavours from the same classes ------------------------------------
function New-AddonJar {
    param([string]$Name, [string]$ResourceDir)
    $stage = Join-Path $BuildDir "$Name-stage"
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    Copy-Item -Recurse -Force (Join-Path $classes "*") $stage
    if (Test-Path $ResourceDir) {
        Copy-Item -Recurse -Force (Join-Path $ResourceDir "*") $stage
    }
    $out = Join-Path $BuildDir "$Name.jar"
    & $jarTool --create --file $out -C $stage .
    if ($LASTEXITCODE -ne 0) { throw "jar failed for $Name (exit $LASTEXITCODE)" }
    return $out
}

$neoJar = New-AddonJar -Name "nekojs08addon-1.0.0-neoforge" -ResourceDir (Join-Path $FixtureDir "src\main\resources-neoforge")
$fabricJar = New-AddonJar -Name "nekojs08addon-1.0.0-fabric" -ResourceDir (Join-Path $FixtureDir "src\main\resources-fabric")

Write-Host "[addon] built $neoJar"
Write-Host "[addon] built $fabricJar"
