param(
    [string]$HostName = "100.115.165.51",
    [string]$User = "tomatoegg"
)

Write-Warning "This script name is kept for compatibility. Use scripts/start-middleware-tunnel.ps1 for the current old-laptop middleware host."
& "$PSScriptRoot\start-middleware-tunnel.ps1" -HostName $HostName -User $User
