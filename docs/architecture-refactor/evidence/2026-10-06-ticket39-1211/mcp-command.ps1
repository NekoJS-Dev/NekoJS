param([int]$ClientPid, [int]$BridgePort, [string]$Command)
if ($BridgePort -lt 1 -or -not $Command.StartsWith('/')) { throw 'A discovered bridge port and slash command are required.' }
$uri = "http://127.0.0.1:$BridgePort/api/cmd"
function Invoke-BridgeAction([string]$Action) {
    Invoke-RestMethod $uri -Method Post -ContentType 'application/json' -Body (@{ cmd = $Action } | ConvertTo-Json)
}
try {
    $control = Invoke-BridgeAction 'enter_control_mode'
    if ($control.control_mode -ne $true) { throw 'The user must activate MCP Take Over for this client first.' }
    $chat = Invoke-BridgeAction 'open_chat'
    if ($chat.chat_opened -ne $true) { throw 'The bridge could not open the native ChatScreen.' }
} finally {
    Invoke-BridgeAction 'exit_control_mode' | Out-Null
}
$inputScript = Join-Path $PSScriptRoot '..\2026-10-06-ticket48-e2e\native-input.ps1'
& $inputScript -ClientPid $ClientPid -Keys chat-command -FollowingKeys $Command
