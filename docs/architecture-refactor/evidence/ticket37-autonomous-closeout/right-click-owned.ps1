param([int]$ClientPid, [string]$OwnedGameDir)
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class OwnedWorldInteraction {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr handle);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint horizontal, uint vertical, uint data, UIntPtr extra);
}
'@
$process = Get-Process -Id $ClientPid -ErrorAction Stop
$description = Get-CimInstance Win32_Process -Filter "ProcessId = $ClientPid"
if ($process.ProcessName -notin @('java', 'javaw') -or $process.MainWindowHandle -eq 0) { throw 'The selected process is not a Java window.' }
. "$PSScriptRoot\owned-game-directory.ps1"
Assert-OwnedGameDirectory -CommandLine $description.CommandLine -OwnedGameDir $OwnedGameDir
if (-not [OwnedWorldInteraction]::SetForegroundWindow($process.MainWindowHandle)) { throw 'Could not focus the owned proof window.' }
Start-Sleep -Milliseconds 300
if ([OwnedWorldInteraction]::GetForegroundWindow() -ne $process.MainWindowHandle) { throw 'The owned proof window is not in the foreground.' }
[OwnedWorldInteraction]::mouse_event(8, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 100
[OwnedWorldInteraction]::mouse_event(16, 0, 0, 0, [UIntPtr]::Zero)
Write-Output "Sent right-click to owned client $ClientPid"
