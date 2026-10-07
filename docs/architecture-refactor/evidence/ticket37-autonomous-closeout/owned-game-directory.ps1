function Assert-OwnedGameDirectory {
    param([string]$CommandLine, [string]$OwnedGameDir)
    if ([string]::IsNullOrWhiteSpace($OwnedGameDir)) { throw 'An owned game directory is required.' }
    $ownedDirectory = Get-Item -LiteralPath $OwnedGameDir -ErrorAction Stop
    if (-not $ownedDirectory.PSIsContainer) { throw 'The owned game directory must be an existing directory.' }
    if (-not ('OwnedGameDirectoryArguments' -as [type])) {
        Add-Type @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
public static class OwnedGameDirectoryArguments {
    [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    private static extern IntPtr CommandLineToArgvW(string commandLine, out int count);
    [DllImport("kernel32.dll")]
    private static extern IntPtr LocalFree(IntPtr memory);
    public static string[] Parse(string commandLine) {
        int count;
        IntPtr memory = CommandLineToArgvW(commandLine, out count);
        if (memory == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
        try {
            string[] arguments = new string[count];
            for (int index = 0; index < count; index++) {
                arguments[index] = Marshal.PtrToStringUni(Marshal.ReadIntPtr(memory, index * IntPtr.Size));
            }
            return arguments;
        } finally {
            LocalFree(memory);
        }
    }
}
'@
    }
    if ([string]::IsNullOrWhiteSpace($CommandLine)) { throw 'The process command line is required.' }
    [string[]]$arguments = [OwnedGameDirectoryArguments]::Parse($CommandLine)
    $directories = [Collections.Generic.List[string]]::new()
    for ($argumentIndex = 1; $argumentIndex -lt $arguments.Length; $argumentIndex++) {
        if ($arguments[$argumentIndex] -ceq '--gameDir') {
            if ($argumentIndex + 1 -ge $arguments.Length) { throw 'The game-directory argument has no value.' }
            $directories.Add($arguments[$argumentIndex + 1])
        } elseif ($arguments[$argumentIndex].StartsWith('--gameDir=', [StringComparison]::Ordinal)) {
            $directories.Add($arguments[$argumentIndex].Substring('--gameDir='.Length))
        }
    }
    if ($directories.Count -ne 1) { throw 'Exactly one game-directory argument is required.' }
    $argument = $directories[0]
    if (-not [System.IO.Path]::IsPathFullyQualified($argument)) { throw 'The process game directory must be absolute.' }
    $processDirectory = Get-Item -LiteralPath $argument -ErrorAction Stop
    if (-not $processDirectory.PSIsContainer -or -not [string]::Equals($processDirectory.FullName, $ownedDirectory.FullName, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The selected process game directory does not match the owned proof profile.'
    }
}
