# Machine-quiet probe for ticket 35 sampling (local helper, lives in gitignored out/).
# Lists java/gradle/python processes with command lines so foreign activity is identifiable.
$procs = Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^(java|javaw|gradle|python|pwsh|powershell)\.exe$' }
foreach ($p in $procs) {
    $cmd = $p.CommandLine
    if ($null -eq $cmd) { $cmd = '<no cmdline>' }
    if ($cmd.Length -gt 200) { $cmd = $cmd.Substring(0, 200) + '...' }
    "{0} {1} [{2}] {3}" -f $p.ProcessId, $p.Name, $p.CreationDate.ToString('yyyy-MM-dd HH:mm:ss'), $cmd
}
"total: $($procs.Count)"
