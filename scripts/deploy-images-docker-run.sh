#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ZHYF_ENV_FILE:-$ROOT_DIR/infra/.env}"

load_env_file() {
  local file="$1"
  [ -f "$file" ] || return 0

  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%"${line##*[![:space:]]}"}"
    line="${line#"${line%%[![:space:]]*}"}"
    case "$line" in
      "" | \#*) continue ;;
    esac

    local key="${line%%=*}"
    local value="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"
    value="${value%$'\r'}"

    case "$key" in
      "" | *[!A-Za-z0-9_]*)
        echo "skip invalid env key: $key" >&2
        continue
        ;;
    esac

    export "$key=$value"
  done < "$file"
}

load_env_file "$ENV_FILE"

IMAGE_NAMESPACE="${ZHYF_IMAGE_NAMESPACE:-${IMAGE_NAMESPACE:-zhyf}}"
IMAGE_TAG="${ZHYF_IMAGE_TAG:-${TAG:-dev}}"
NETWORK="${ZHYF_DOCKER_NETWORK:-zhyf-net}"
SUBNET="${ZHYF_DOCKER_SUBNET:-}"
START_MIDDLEWARE="${ZHYF_START_MIDDLEWARE:-false}"
WAIT_APP_HEALTH="${ZHYF_WAIT_APP_HEALTH:-true}"
WAIT_TIMEOUT_SECONDS="${ZHYF_WAIT_TIMEOUT_SECONDS:-240}"

DB_HOST="${ZHYF_DB_HOST:-zhyf-postgres}"
DB_PORT="${ZHYF_DB_PORT:-5432}"
DB_NAME="${ZHYF_DB_NAME:-zhyf_saas}"
DB_USERNAME="${ZHYF_DB_USERNAME:-postgres}"
DB_PASSWORD="${ZHYF_DB_PASSWORD:-${ZHYF_POSTGRES_PASSWORD:-123456}}"
REDIS_HOST="${ZHYF_REDIS_HOST:-zhyf-redis}"
REDIS_PORT="${ZHYF_REDIS_PORT:-6379}"
REDIS_PASSWORD="${ZHYF_REDIS_PASSWORD:-123456}"
ROCKETMQ_NAMESRV="${ZHYF_ROCKETMQ_NAMESRV:-zhyf-rocketmq-namesrv:9876}"

POSTGRES_IMAGE="${ZHYF_POSTGRES_IMAGE:-postgres:16-alpine}"
REDIS_IMAGE="${ZHYF_REDIS_IMAGE:-redis:7-alpine}"
ROCKETMQ_IMAGE="${ZHYF_ROCKETMQ_IMAGE:-apache/rocketmq:5.3.2}"

SKYWALKING_ENABLED="${ZHYF_SKYWALKING_ENABLED:-true}"
SKYWALKING_AGENT_DIR="${ZHYF_SKYWALKING_AGENT_DIR:-/opt/zhyf/skywalking/agent}"
SKYWALKING_AGENT_LOG_DIR="${ZHYF_SKYWALKING_AGENT_LOG_DIR:-/opt/zhyf/logs/skywalking-agent}"
SKYWALKING_COLLECTOR="${ZHYF_SKYWALKING_COLLECTOR:-zhyf-skywalking-oap:11800}"

DEFAULT_JAVA_OPTS="${ZHYF_JAVA_OPTS:--Xms128m -Xmx384m -XX:+UseG1GC -Dfile.encoding=UTF-8}"
SMALL_JAVA_OPTS="${ZHYF_SMALL_JAVA_OPTS:--Xms128m -Xmx256m -XX:+UseG1GC -Dfile.encoding=UTF-8}"

APP_CONTAINERS=(
  zhyf-admin-web
  zhyf-nginx
  zhyf-gateway
  zhyf-auth-institution
  zhyf-order-service
  zhyf-prescription-service
  zhyf-message-service
  zhyf-workflow-service
  zhyf-ops-service
  zhyf-decoction-service
  zhyf-logistics-service
  zhyf-callback-service
  zhyf-portal-service
  zhyf-report-service
  zhyf-integration-service
)

BACKEND_SERVICES=(
  auth-institution
  order-service
  prescription-service
  message-service
  workflow-service
  ops-service
  decoction-service
  logistics-service
  callback-service
  portal-service
  report-service
  integration-service
  gateway
)

container_exists() {
  docker inspect "$1" >/dev/null 2>&1
}

ensure_network() {
  if docker network inspect "$NETWORK" >/dev/null 2>&1; then
    return 0
  fi

  if [ -n "$SUBNET" ]; then
    docker network create --subnet "$SUBNET" "$NETWORK" >/dev/null
  else
    docker network create "$NETWORK" >/dev/null
  fi
}

connect_existing_container() {
  local name="$1"
  if container_exists "$name"; then
    docker network connect "$NETWORK" "$name" >/dev/null 2>&1 || true
  fi
}

connect_existing_dependencies() {
  connect_existing_container zhyf-postgres
  connect_existing_container zhyf-redis
  connect_existing_container zhyf-rocketmq-namesrv
  connect_existing_container zhyf-rocketmq-broker
  connect_existing_container zhyf-skywalking-oap
}

ensure_image() {
  local image="$1"
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "missing image: $image" >&2
    echo "build it first, for example: TAG=$IMAGE_TAG scripts/build-images.sh" >&2
    exit 1
  fi
}

ensure_named_container_running() {
  local name="$1"
  shift

  if container_exists "$name"; then
    docker start "$name" >/dev/null
  else
    "$@"
  fi
}

start_middleware() {
  ensure_named_container_running zhyf-postgres docker run -d \
    --name zhyf-postgres \
    --restart unless-stopped \
    --network "$NETWORK" \
    -p 127.0.0.1:15432:5432 \
    -e POSTGRES_DB="$DB_NAME" \
    -e POSTGRES_USER="$DB_USERNAME" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -e TZ=Asia/Hong_Kong \
    -v zhyf-postgres-data:/var/lib/postgresql/data \
    "$POSTGRES_IMAGE"

  ensure_named_container_running zhyf-redis docker run -d \
    --name zhyf-redis \
    --restart unless-stopped \
    --network "$NETWORK" \
    -p 127.0.0.1:16379:6379 \
    -e REDIS_PASSWORD="$REDIS_PASSWORD" \
    -v zhyf-redis-data:/data \
    "$REDIS_IMAGE" redis-server --requirepass "$REDIS_PASSWORD"

  ensure_named_container_running zhyf-rocketmq-namesrv docker run -d \
    --name zhyf-rocketmq-namesrv \
    --restart unless-stopped \
    --network "$NETWORK" \
    -p 127.0.0.1:9876:9876 \
    "$ROCKETMQ_IMAGE" sh mqnamesrv

  ensure_named_container_running zhyf-rocketmq-broker docker run -d \
    --name zhyf-rocketmq-broker \
    --restart unless-stopped \
    --network "$NETWORK" \
    -e JAVA_OPT_EXT="-server -Xms256m -Xmx512m -Xmn128m" \
    -e NAMESRV_ADDR="$ROCKETMQ_NAMESRV" \
    -v zhyf-rocketmq-store:/home/rocketmq/store \
    "$ROCKETMQ_IMAGE" sh mqbroker -n "$ROCKETMQ_NAMESRV"
}

remove_app_containers() {
  local name
  for name in "${APP_CONTAINERS[@]}"; do
    docker rm -f "$name" >/dev/null 2>&1 || true
  done
}

wait_for_postgres() {
  local elapsed=0
  until docker exec zhyf-postgres pg_isready -U "$DB_USERNAME" -d "$DB_NAME" >/dev/null 2>&1; do
    if [ "$elapsed" -ge "$WAIT_TIMEOUT_SECONDS" ]; then
      echo "postgres is not ready after ${WAIT_TIMEOUT_SECONDS}s" >&2
      exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
}

skywalking_args=()
skywalking_java_prefix=""
if [ "$SKYWALKING_ENABLED" = "true" ]; then
  if [ -f "$SKYWALKING_AGENT_DIR/skywalking-agent.jar" ]; then
    mkdir -p "$SKYWALKING_AGENT_LOG_DIR"
    chmod 777 "$SKYWALKING_AGENT_LOG_DIR" >/dev/null 2>&1 || true
    skywalking_args=(
      -v "$SKYWALKING_AGENT_DIR:/opt/skywalking/agent:ro"
      -v "$SKYWALKING_AGENT_LOG_DIR:/opt/skywalking/agent/logs"
      -e SW_LOGGING_DIR=/opt/skywalking/agent/logs
      -e SW_AGENT_COLLECTOR_BACKEND_SERVICES="$SKYWALKING_COLLECTOR"
    )
    skywalking_java_prefix="-javaagent:/opt/skywalking/agent/skywalking-agent.jar "
  else
    echo "skywalking agent not found, start services without javaagent: $SKYWALKING_AGENT_DIR" >&2
  fi
fi

common_env=(
  -e TZ=Asia/Hong_Kong
  -e SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-default}"
  -e ZHYF_DB_HOST="$DB_HOST"
  -e ZHYF_DB_PORT="$DB_PORT"
  -e ZHYF_DB_NAME="$DB_NAME"
  -e ZHYF_DB_USERNAME="$DB_USERNAME"
  -e ZHYF_DB_PASSWORD="$DB_PASSWORD"
  -e SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:-4}"
  -e SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="${SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE:-1}"
  -e ZHYF_REDIS_HOST="$REDIS_HOST"
  -e ZHYF_REDIS_PORT="$REDIS_PORT"
  -e ZHYF_REDIS_PASSWORD="$REDIS_PASSWORD"
  -e ZHYF_ROCKETMQ_NAMESRV="$ROCKETMQ_NAMESRV"
  -e ZHYF_MESSAGE_PUBLISHER="${ZHYF_MESSAGE_PUBLISHER:-rocketmq}"
  -e ZHYF_CALLBACK_DISPATCH_ENABLED="${ZHYF_CALLBACK_DISPATCH_ENABLED:-false}"
  -e ZHYF_INTEGRATION_DISPATCH_ENABLED="${ZHYF_INTEGRATION_DISPATCH_ENABLED:-false}"
  -e ORDER_SERVICE_BASE_URL="${ORDER_SERVICE_BASE_URL:-http://zhyf-order-service:18082}"
  -e CALLBACK_SERVICE_BASE_URL="${CALLBACK_SERVICE_BASE_URL:-http://zhyf-callback-service:18089}"
  -e AUTH_INSTITUTION_BASE_URL="${AUTH_INSTITUTION_BASE_URL:-http://zhyf-auth-institution:18081}"
)

java_opts_with_agent() {
  local opts="$1"
  printf '%s%s' "$skywalking_java_prefix" "$opts"
}

run_service() {
  local service="$1"
  local port="$2"
  local opts_env_name="$3"
  local default_opts="$4"
  shift 4

  local image="$IMAGE_NAMESPACE/$service:$IMAGE_TAG"
  local container="zhyf-$service"
  local java_opts="${!opts_env_name:-$default_opts}"
  ensure_image "$image"

  docker run -d \
    --name "$container" \
    --restart unless-stopped \
    --network "$NETWORK" \
    -p "127.0.0.1:${port}:${port}" \
    --health-cmd "curl -fsS http://127.0.0.1:${port}/actuator/health >/dev/null || exit 1" \
    --health-interval 15s \
    --health-timeout 5s \
    --health-retries 10 \
    --health-start-period 45s \
    "${common_env[@]}" \
    "${skywalking_args[@]}" \
    -e JAVA_OPTS="$(java_opts_with_agent "$java_opts")" \
    -e SW_AGENT_NAME="$container" \
    "$@" \
    "$image" >/dev/null
}

wait_healthy() {
  local container="$1"
  local elapsed=0
  local status=""

  while true; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [ "$status" = "healthy" ] || [ "$status" = "running" ]; then
      echo "healthy: $container"
      return 0
    fi

    if [ "$elapsed" -ge "$WAIT_TIMEOUT_SECONDS" ]; then
      echo "container is not healthy after ${WAIT_TIMEOUT_SECONDS}s: $container status=$status" >&2
      docker logs --tail 80 "$container" >&2 || true
      exit 1
    fi

    sleep 5
    elapsed=$((elapsed + 5))
  done
}

run_admin_web() {
  local image="$IMAGE_NAMESPACE/admin-web:$IMAGE_TAG"
  ensure_image "$image"

  docker run -d \
    --name zhyf-admin-web \
    --restart unless-stopped \
    --network "$NETWORK" \
    -p "${ZHYF_ADMIN_WEB_BIND:-80}:80" \
    "$image" >/dev/null
}

ensure_network
connect_existing_dependencies

if [ "$START_MIDDLEWARE" = "true" ]; then
  start_middleware
  connect_existing_dependencies
fi

wait_for_postgres
remove_app_containers

for service in "${BACKEND_SERVICES[@]}"; do
  ensure_image "$IMAGE_NAMESPACE/$service:$IMAGE_TAG"
done
ensure_image "$IMAGE_NAMESPACE/admin-web:$IMAGE_TAG"

run_service auth-institution 18081 ZHYF_AUTH_JAVA_OPTS "$SMALL_JAVA_OPTS" -e AUTH_INSTITUTION_PORT=18081
run_service order-service 18082 ZHYF_ORDER_JAVA_OPTS "$DEFAULT_JAVA_OPTS" -e ORDER_SERVICE_PORT=18082
run_service prescription-service 18084 ZHYF_PRESCRIPTION_JAVA_OPTS "$SMALL_JAVA_OPTS" -e PRESCRIPTION_SERVICE_PORT=18084
run_service message-service 18083 ZHYF_MESSAGE_JAVA_OPTS "$DEFAULT_JAVA_OPTS" \
  -e MESSAGE_SERVICE_PORT=18083 \
  -e ZHYF_OUTBOX_SCAN_ENABLED="${ZHYF_OUTBOX_SCAN_ENABLED:-true}" \
  -e ZHYF_MESSAGE_CONSUMER_ENABLED="${ZHYF_MESSAGE_CONSUMER_ENABLED:-true}"
run_service workflow-service 18085 ZHYF_WORKFLOW_JAVA_OPTS "$DEFAULT_JAVA_OPTS" \
  -e WORKFLOW_SERVICE_PORT=18085 \
  -e ZHYF_WORKFLOW_CONSUMER_ENABLED="${ZHYF_WORKFLOW_CONSUMER_ENABLED:-true}"
run_service ops-service 18086 ZHYF_OPS_JAVA_OPTS "$SMALL_JAVA_OPTS" -e OPS_SERVICE_PORT=18086
run_service decoction-service 18087 ZHYF_DECOCTION_JAVA_OPTS "$SMALL_JAVA_OPTS" -e DECOCTION_SERVICE_PORT=18087
run_service logistics-service 18088 ZHYF_LOGISTICS_JAVA_OPTS "$SMALL_JAVA_OPTS" -e LOGISTICS_SERVICE_PORT=18088
run_service callback-service 18089 ZHYF_CALLBACK_JAVA_OPTS "$SMALL_JAVA_OPTS" -e CALLBACK_SERVICE_PORT=18089
run_service portal-service 18090 ZHYF_PORTAL_JAVA_OPTS "$SMALL_JAVA_OPTS" -e PORTAL_SERVICE_PORT=18090
run_service report-service 18091 ZHYF_REPORT_JAVA_OPTS "$SMALL_JAVA_OPTS" -e REPORT_SERVICE_PORT=18091
run_service integration-service 18092 ZHYF_INTEGRATION_JAVA_OPTS "$SMALL_JAVA_OPTS" -e INTEGRATION_SERVICE_PORT=18092
run_service gateway 18080 ZHYF_GATEWAY_JAVA_OPTS "$SMALL_JAVA_OPTS" \
  -e GATEWAY_PORT=18080 \
  -e AUTH_INSTITUTION_BASE_URL="${AUTH_INSTITUTION_BASE_URL:-http://zhyf-auth-institution:18081}" \
  -e ORDER_SERVICE_BASE_URL="${ORDER_SERVICE_BASE_URL:-http://zhyf-order-service:18082}"

if [ "$WAIT_APP_HEALTH" = "true" ]; then
  for service in "${BACKEND_SERVICES[@]}"; do
    wait_healthy "zhyf-$service"
  done
fi

run_admin_web

if [ "$WAIT_APP_HEALTH" = "true" ]; then
  wait_healthy zhyf-admin-web
fi

echo "image deploy completed: namespace=$IMAGE_NAMESPACE tag=$IMAGE_TAG network=$NETWORK"
