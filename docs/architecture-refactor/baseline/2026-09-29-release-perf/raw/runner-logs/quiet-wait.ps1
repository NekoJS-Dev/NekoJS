# Machine-quiet gate for ticket 35 sampling (local helper in gitignored out/).
# Prints a per-phase quiet decision: foreign java processes are sampled for CPU
# usage over a 5 s window; anything above an idle threshold blocks sampling.
param([int]$WaitForQuietSec = 0)

function Sample-Cpu($procs) {
    $first = @{}
    foreach ($p in $procs) { try { $first[$p.Id] = $p.TotalProcessorTime.TotalMilliseconds } catch {} }
    Start-Sleep -Seconds 5
    $procs2 = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(java|javaw|gradle|python|pwsh|powershell)$' }
    $rows = @()
    foreach ($p in $procs2) {
        if ($first.ContainsKey($p.Id)) {
            try {
                $delta = ($p.TotalProcessorTime.TotalMilliseconds - $first[$p.Id]) / 5.0
                $rows += [pscustomobject]@{ Id = $p.Id; Name = $p.ProcessName; CpuPctOfOneCore = [math]::Round($delta / 10.0, 1) }
            } catch {}
        }
    }
    return $rows
}

$deadline = [DateTime]::UtcNow.AddSeconds($WaitForQuietSec)
while ($true) {
    $procs = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(java|javaw|gradle|python|pwsh|powershell)$' }
    $rows = Sample-Cpu $procs
    $foreign = @($rows | Where-Object { $_.CpuPctOfOneCore -gt 5 })
    $stamp = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    if ($foreign.Count -eq 0) {
        "$stamp QUIET: no sampling-class process above idle threshold"
        $rows | ForEach-Object { "$stamp   idle: $($_.Name) pid=$($_.Id) cpu=$($_.CpuPctOfOneCore)%-of-core" }
        exit 0
    }
    "$stamp BUSY:" + (($foreign | ForEach-Object { "$($_.Name) pid=$($_.Id) cpu=$($_.CpuPctOfOneCore)%-of-core" }) -join '; ')
    if ([DateTime]::UtcNow -ge $deadline) { exit 1 }
    Start-Sleep -Seconds 10
}
