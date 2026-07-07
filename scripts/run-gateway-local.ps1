param(
    [string]$AuthInstitutionBaseUrl = "http://localhost:18081",
    [string]$OrderServiceBaseUrl = "http://localhost:18082",
    [string]$DbHost = "100.115.165.51",
    [string]$DbPort = "15432",
    [string]$DbName = "zhyf_saas",
    [string]$DbUsername = "postgres"
)

$env:JAVA_HOME = "D:\Javasource\javaTools\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:GATEWAY_PORT = "18080"
$env:AUTH_INSTITUTION_BASE_URL = $AuthInstitutionBaseUrl
$env:ORDER_SERVICE_BASE_URL = $OrderServiceBaseUrl
$env:ZHYF_DB_HOST = $DbHost
$env:ZHYF_DB_PORT = $DbPort
$env:ZHYF_DB_NAME = $DbName
$env:ZHYF_DB_USERNAME = $DbUsername

if (-not $env:ZHYF_DB_PASSWORD) {
    $env:ZHYF_DB_PASSWORD = Read-Host "PostgreSQL password"
}

Push-Location "$PSScriptRoot\..\backend"
try {
    mvn -pl gateway -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    java -jar ".\gateway\target\gateway-0.1.0-SNAPSHOT.jar"
}
finally {
    Pop-Location
}
