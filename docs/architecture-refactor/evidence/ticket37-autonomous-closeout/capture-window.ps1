param([int]$ClientPid, [string]$OwnedGameDir, [string]$OutputPath)
Add-Type -AssemblyName System.Drawing
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class ProofWindowCapture {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left; public int Top; public int Right; public int Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct Point { public int X; public int Y; }
    [DllImport("user32.dll")] public static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr handle, out Rect rect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr handle, ref Point point);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr handle);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
}
'@
$process = Get-Process -Id $ClientPid -ErrorAction Stop
$description = Get-CimInstance Win32_Process -Filter "ProcessId = $ClientPid"
if ($process.ProcessName -notin @('java', 'javaw') -or $process.MainWindowHandle -eq 0) { throw 'The selected process is not a Java window.' }
. "$PSScriptRoot\owned-game-directory.ps1"
Assert-OwnedGameDirectory -CommandLine $description.CommandLine -OwnedGameDir $OwnedGameDir
[ProofWindowCapture]::SetThreadDpiAwarenessContext([IntPtr]::new(-4)) | Out-Null
if (-not [ProofWindowCapture]::SetForegroundWindow($process.MainWindowHandle)) { throw 'Could not focus the owned proof window.' }
Start-Sleep -Milliseconds 300
if ([ProofWindowCapture]::GetForegroundWindow() -ne $process.MainWindowHandle) { throw 'The owned proof window is not in the foreground.' }
$rectangle = New-Object ProofWindowCapture+Rect
$origin = New-Object ProofWindowCapture+Point
if (-not [ProofWindowCapture]::GetClientRect($process.MainWindowHandle, [ref]$rectangle) -or -not [ProofWindowCapture]::ClientToScreen($process.MainWindowHandle, [ref]$origin)) { throw 'Could not locate the owned client area.' }
$bitmap = New-Object System.Drawing.Bitmap(($rectangle.Right - $rectangle.Left), ($rectangle.Bottom - $rectangle.Top))
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.CopyFromScreen($origin.X, $origin.Y, 0, 0, $bitmap.Size)
    $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $bitmap.Dispose()
}
Write-Output "Captured owned client $ClientPid to $OutputPath"
