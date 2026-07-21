param(
    [string]$ImageNamespace = "zhyf",
    [string]$Tag = "",
    [string[]]$Services = @(
        "auth-institution",
        "order-service",
        "prescription-service",
        "message-service",
        "workflow-service",
        "ops-service",
        "decoction-service",
        "callback-service",
        "logistics-service",
        "portal-service",
        "report-service",
        "integration-service",
        "gateway"
    ),
    [switch]$SkipPackage
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Tag) {
    $Tag = (git rev-parse --short HEAD).Trim()
}

if (-not $SkipPackage) {
    Push-Location (Join-Path $root "backend")
    mvn -DskipTests package
    Pop-Location
}

foreach ($service in $Services) {
    $jar = Join-Path $root "backend/$service/target/$service-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        throw "找不到 $service 的可执行 jar: $jar"
    }

    $image = "$ImageNamespace/$service`:$Tag"
    docker build `
        --file backend/Dockerfile `
        --build-arg "SERVICE_NAME=$service" `
        --build-arg "JAR_FILE=backend/$service/target/$service-0.1.0-SNAPSHOT.jar" `
        --tag $image `
        .
}

docker build `
    --file frontend/admin-web/Dockerfile `
    --tag "$ImageNamespace/admin-web`:$Tag" `
    .

Write-Host "镜像构建完成: namespace=$ImageNamespace tag=$Tag"
