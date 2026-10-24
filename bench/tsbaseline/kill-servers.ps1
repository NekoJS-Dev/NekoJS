Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -match 'win_args\.txt|fabric-server-launch' } |
  ForEach-Object {
    Write-Output ("KILL " + $_.ProcessId)
    Stop-Process -Id $_.ProcessId -Force
  }
Start-Sleep 3
$left = (Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -match 'win_args\.txt|fabric-server-launch' } | Measure-Object).Count
Write-Output ("REMAINING " + $left)
