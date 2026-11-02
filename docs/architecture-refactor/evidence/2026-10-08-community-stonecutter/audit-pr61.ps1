# Run the proposed PR 61 corpus against the current branch without changing test sources.
$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$auditDir = Join-Path $env:TEMP 'nekojs-pr61-ec7fe684-audit'
New-Item -ItemType Directory -Force $auditDir | Out-Null
Push-Location $repository
try {
    $prSource = (git show c7e875713f43e37274d9ffb38c45c13ef62e5720:common/src/test/java/com/tkisor/nekojs/core/compiler/TypeScriptErasureParseCorpusTest.java) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'The pinned PR 61 commit is unavailable.' }
    $prSource = $prSource.Replace('class TypeScriptErasureParseCorpusTest', 'class Pr61CorpusAuditTest')
    $prSource = $prSource.Replace('org.junit.jupiter.api.Assumptions.abort("已知缺口（待修）：" + gap);', '// Audit executes every known gap.')
    [IO.File]::WriteAllText((Join-Path $auditDir 'Pr61CorpusAuditTest.java'), $prSource)
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Pr61AuditRunner.java') -Destination $auditDir
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'audit.init.gradle') -Destination $auditDir
    & ./gradlew.bat -I (Join-Path $auditDir 'audit.init.gradle') :common:runPr61Audit --console=plain
    # This investigative runner reports individual failures; inspect AUDIT_TOTAL.
    $auditExit = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $auditExit