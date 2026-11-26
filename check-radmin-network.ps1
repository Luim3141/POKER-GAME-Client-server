$ErrorActionPreference = 'Stop'
$address = Get-NetIPAddress -InterfaceAlias 'Radmin VPN' -AddressFamily IPv4 -ErrorAction Stop |
    Where-Object IPAddress -like '26.*' | Select-Object -First 1 -ExpandProperty IPAddress
if (-not $address) { throw 'No 26.x Radmin VPN IPv4 address was found.' }
$listener = Get-NetTCPConnection -LocalPort 19090 -State Listen -ErrorAction SilentlyContinue
$rule = Get-NetFirewallRule -DisplayName 'Poker Server - Radmin VPN TCP 19090' -ErrorAction SilentlyContinue
[pscustomobject]@{
    RadminIPv4 = $address
    PokerPortListening = [bool]$listener
    FirewallRuleEnabled = [bool]($rule -and $rule.Enabled -eq 'True')
    ClientTestCommand = "Test-NetConnection $address -Port 19090"
}
