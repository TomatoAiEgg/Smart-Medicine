#!/usr/bin/env sh
set -eu

IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-zhyf}"
TAG="${TAG:-$(git rev-parse --short HEAD)}"
SKIP_PACKAGE="${SKIP_PACKAGE:-false}"
SERVICES="${SERVICES:-auth-institution order-service prescription-service message-service workflow-service ops-service decoction-service callback-service logistics-service portal-service report-service integration-service gateway}"

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$ROOT_DIR"

if [ "$SKIP_PACKAGE" != "true" ]; then
  (cd backend && mvn -DskipTests package)
fi

for service in $SERVICES; do
  jar="backend/$service/target/$service-0.1.0-SNAPSHOT.jar"
  if [ ! -f "$jar" ]; then
    echo "找不到 $service 的可执行 jar: $jar" >&2
    exit 1
  fi

  docker build \
    --file backend/Dockerfile \
    --build-arg "SERVICE_NAME=$service" \
    --build-arg "JAR_FILE=$jar" \
    --tag "$IMAGE_NAMESPACE/$service:$TAG" \
    .
done

docker build \
  --file frontend/admin-web/Dockerfile \
  --tag "$IMAGE_NAMESPACE/admin-web:$TAG" \
  .

echo "镜像构建完成: namespace=$IMAGE_NAMESPACE tag=$TAG"
