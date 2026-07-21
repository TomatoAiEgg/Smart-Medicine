param(
    [string]$HostName = "47.120.55.53",
    [string]$User = "root"
)

Write-Warning "This script name is kept for compatibility. Use scripts/start-middleware-tunnel.ps1 for the current cloud middleware host."
& "$PSScriptRoot\start-middleware-tunnel.ps1" -HostName $HostName -User $User
