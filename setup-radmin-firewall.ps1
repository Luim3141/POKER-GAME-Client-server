#Requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'
$ruleName = 'Poker Server - Radmin VPN TCP 19090'
$adapter = Get-NetAdapter -Name 'Radmin VPN' -ErrorAction Stop
if ($adapter.Status -ne 'Up') { throw 'Radmin VPN adapter is not connected.' }
Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Action Allow -Protocol TCP `
    -LocalPort 19090 -InterfaceAlias 'Radmin VPN' -RemoteAddress '26.0.0.0/8' -Profile Any | Out-Null
Write-Host "Created '$ruleName' for Radmin VPN only."
