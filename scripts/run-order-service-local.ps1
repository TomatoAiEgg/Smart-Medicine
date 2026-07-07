param(
    [string]$DbHost = "100.115.165.51",
    [string]$DbPort = "15432",
    [string]$DbName = "zhyf_saas",
    [string]$DbUsername = "postgres"
)

$env:JAVA_HOME = "D:\Javasource\javaTools\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:ORDER_SERVICE_PORT = "18082"
$env:ZHYF_DB_HOST = $DbHost
$env:ZHYF_DB_PORT = $DbPort
$env:ZHYF_DB_NAME = $DbName
$env:ZHYF_DB_USERNAME = $DbUsername

if (-not $env:ZHYF_DB_PASSWORD) {
    $env:ZHYF_DB_PASSWORD = Read-Host "PostgreSQL password"
}

Push-Location "$PSScriptRoot\..\backend"
try {
    mvn -pl order-service -am package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    java -jar ".\order-service\target\order-service-0.1.0-SNAPSHOT.jar"
}
finally {
    Pop-Location
}
