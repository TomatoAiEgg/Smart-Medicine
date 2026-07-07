param(
    [string]$BaseUrl = "http://localhost:18082",
    [string]$AppKey = "demo-app",
    [string]$AppSecret = "demo-secret"
)

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$body = @{
    externalOrderNo = "EXT-" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    patientName = "Smoke Patient"
    patientPhone = "13800000000"
    receiverName = "Smoke Receiver"
    receiverPhone = "13800000000"
    receiverAddress = "Nanshan Smoke Address"
    prescriptions = @(
        @{
            externalPrescriptionNo = "RX-" + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            doctorName = "Smoke Doctor"
            diagnosis = "Smoke Diagnosis"
            details = @(
                @{
                    drugCode = "DRUG001"
                    drugName = "Smoke Herb"
                    dose = "10"
                    unit = "g"
                }
            )
        }
    )
} | ConvertTo-Json -Depth 8 -Compress

$sha = [System.Security.Cryptography.SHA256]::Create()
$bodyHashBytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($body))
$bodyHash = -join ($bodyHashBytes | ForEach-Object { $_.ToString("x2") })
$source = "$AppKey`n$timestamp`n$bodyHash"

$hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($AppSecret))
$signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($source))
$signature = -join ($signatureBytes | ForEach-Object { $_.ToString("x2") })

$headers = @{
    "X-App-Key" = $AppKey
    "X-Timestamp" = $timestamp
    "X-Signature" = $signature
}

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/institution/createOrder" -Headers $headers -Body $body -ContentType "application/json"
