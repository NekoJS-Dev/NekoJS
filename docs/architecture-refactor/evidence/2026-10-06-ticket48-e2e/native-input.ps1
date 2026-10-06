param([int]$ClientPid, [string]$Keys, [int]$ClientX = -1, [int]$ClientY = -1, [string]$FollowingKeys = '')
Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class MinecraftProofInput {
    [StructLayout(LayoutKind.Sequential)] public struct Point { public int X; public int Y; }
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr handle);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr handle, int command);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern IntPtr LoadKeyboardLayout(string name, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr handle, uint message, IntPtr word, IntPtr value);
    [DllImport("user32.dll")] public static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr handle, ref Point point);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr handle, IntPtr insertAfter, int x, int y, int width, int height, uint flags);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint x, uint y, uint data, UIntPtr extra);
    [DllImport("user32.dll")] public static extern void keybd_event(byte key, byte scan, uint flags, UIntPtr extra);
}
'@
$client = Get-Process -Id $ClientPid -ErrorAction Stop
[MinecraftProofInput]::SetThreadDpiAwarenessContext([IntPtr]::new(-4)) | Out-Null
if ($client.ProcessName -notin @('java', 'javaw') -or $client.MainWindowHandle -eq 0) { throw 'The selected client has no Java game window.' }
[MinecraftProofInput]::ShowWindow($client.MainWindowHandle, 9) | Out-Null
if (-not [MinecraftProofInput]::SetForegroundWindow($client.MainWindowHandle)) { throw 'Could not focus the isolated client window.' }
Start-Sleep -Milliseconds 200
if ([MinecraftProofInput]::GetForegroundWindow() -ne $client.MainWindowHandle) { throw 'The isolated game window is not in the foreground.' }
$english = [MinecraftProofInput]::LoadKeyboardLayout('00000409', 0x80)
if ($english -eq [IntPtr]::Zero -or -not [MinecraftProofInput]::PostMessage($client.MainWindowHandle, 0x50, [IntPtr]::Zero, $english)) { throw 'Could not select English input for the isolated game window.' }
Start-Sleep -Milliseconds 200
if ($Keys -eq 'chat-command') {
    if (-not $FollowingKeys.StartsWith('/')) { throw 'A slash command is required.' }
    [MinecraftProofInput]::keybd_event(0x11, 0x1D, 0, [UIntPtr]::Zero)
    [MinecraftProofInput]::keybd_event(0x41, 0x1E, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 100
    [MinecraftProofInput]::keybd_event(0x41, 0x1E, 2, [UIntPtr]::Zero)
    [MinecraftProofInput]::keybd_event(0x11, 0x1D, 2, [UIntPtr]::Zero)
    [System.Windows.Forms.SendKeys]::SendWait($FollowingKeys.Replace('{ENTER}', ''))
    Start-Sleep -Milliseconds 200
    [MinecraftProofInput]::keybd_event(0x0D, 0x1C, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 100
    [MinecraftProofInput]::keybd_event(0x0D, 0x1C, 2, [UIntPtr]::Zero)
} elseif ($Keys -eq 'resize') {
    if (-not [MinecraftProofInput]::SetWindowPos($client.MainWindowHandle, [IntPtr]::Zero, 0, 0, 1280, 759, 6)) { throw 'Could not resize the client window.' }
} elseif ($Keys -in @('click', 'double-click')) {
    if ($ClientX -lt 0 -or $ClientY -lt 0) { throw 'Client coordinates must be nonnegative.' }
    $point = New-Object MinecraftProofInput+Point
    $point.X = $ClientX
    $point.Y = $ClientY
    if (-not [MinecraftProofInput]::ClientToScreen($client.MainWindowHandle, [ref]$point)) { throw 'Could not locate the client coordinates.' }
    [MinecraftProofInput]::SetCursorPos($point.X, $point.Y) | Out-Null
    [MinecraftProofInput]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 100
    [MinecraftProofInput]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
    if ($Keys -eq 'double-click') {
        Start-Sleep -Milliseconds 50
        [MinecraftProofInput]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 50
        [MinecraftProofInput]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
    }
    if ($FollowingKeys.Length -gt 0) {
        Start-Sleep -Milliseconds 500
        if ($FollowingKeys.StartsWith('/')) {
            [MinecraftProofInput]::keybd_event(0x54, 0x14, 0, [UIntPtr]::Zero)
            Start-Sleep -Milliseconds 100
            [MinecraftProofInput]::keybd_event(0x54, 0x14, 2, [UIntPtr]::Zero)
            Start-Sleep -Milliseconds 200
            [System.Windows.Forms.SendKeys]::SendWait($FollowingKeys.Replace('{ENTER}', ''))
            Start-Sleep -Milliseconds 200
            [MinecraftProofInput]::keybd_event(0x0D, 0x1C, 0, [UIntPtr]::Zero)
            Start-Sleep -Milliseconds 100
            [MinecraftProofInput]::keybd_event(0x0D, 0x1C, 2, [UIntPtr]::Zero)
        } else {
            [System.Windows.Forms.SendKeys]::SendWait($FollowingKeys)
        }
    }
} elseif ($Keys -eq 'reload') {
    [MinecraftProofInput]::keybd_event(0x72, 0, 0, [UIntPtr]::Zero)
    [MinecraftProofInput]::keybd_event(0x54, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 100
    [MinecraftProofInput]::keybd_event(0x54, 0, 2, [UIntPtr]::Zero)
    [MinecraftProofInput]::keybd_event(0x72, 0, 2, [UIntPtr]::Zero)
} else {
    [System.Windows.Forms.SendKeys]::SendWait($Keys)
}
Write-Output "Sent $Keys to isolated Java client $ClientPid"
