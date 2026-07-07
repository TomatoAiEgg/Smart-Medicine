param(
    [string]$HostName = "100.115.165.51",
    [string]$User = "tomatoegg"
)

Write-Host "Starting SSH tunnels to middleware host. Keep this window open."
Write-Host "Local ports:"
Write-Host "  PostgreSQL 127.0.0.1:15432 -> middleware host 15432"
Write-Host "  Redis      127.0.0.1:16379 -> middleware host 16379"
Write-Host "  RocketMQ   127.0.0.1:9876  -> middleware host 9876"
Write-Host "  RocketMQ   127.0.0.1:10911 -> middleware host 10911"
Write-Host "  Dashboard  127.0.0.1:18181 -> middleware host 18181"

ssh `
  -i "$PSScriptRoot\..\.codex-tmp\id_ed25519" `
  -N `
  -L 15432:127.0.0.1:15432 `
  -L 16379:127.0.0.1:16379 `
  -L 9876:127.0.0.1:9876 `
  -L 10911:127.0.0.1:10911 `
  -L 18181:127.0.0.1:18181 `
  "$User@$HostName"
