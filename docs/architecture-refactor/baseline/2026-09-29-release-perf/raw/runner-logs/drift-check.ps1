# Environment drift probe for ticket 35 (local helper in gitignored out/).
# Mirrors baseline REPORT section 2 noise profile: Docker Desktop / IDE / browser presence.
$names = 'Docker Desktop','com.docker.backend','com.docker.service','dockerd','idea64','Code','studio64','devenv','firefox','chrome','msedge'
foreach ($n in $names) {
    $c = (Get-Process -Name $n -ErrorAction SilentlyContinue | Measure-Object).Count
    if ($c -gt 0) { "present: $n x$c" }
}
$svc = Get-Service -Name 'com.docker.service' -ErrorAction SilentlyContinue
if ($svc) { "docker service: $($svc.Status)" } else { "docker service: not installed" }
$os = Get-CimInstance Win32_OperatingSystem
"mem free GB: $([math]::Round($os.FreePhysicalMemory / 1MB, 1)) / total GB: $([math]::Round($os.TotalVisibleMemorySize / 1MB, 1))"
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
"cpu: $($cpu.Name) cores=$($cpu.NumberOfCores) load=$($cpu.LoadPercentage)%"
"utc: $((Get-Date).ToUniversalTime().ToString('o'))"
