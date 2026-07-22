#!/usr/bin/env sh
set -eu

HOST="${1:-127.0.0.1}"
TIMEOUT="${ZHYF_VERIFY_TIMEOUT_SECONDS:-5}"

failures=0

check_http() {
  name="$1"
  url="$2"
  expected="${3:-}"

  body="$(curl -fsS --max-time "$TIMEOUT" "$url" 2>/tmp/zhyf-verify-curl.err || true)"
  if [ -n "$expected" ]; then
    printf '%s' "$body" | grep -F "$expected" >/dev/null 2>&1 || body=""
  fi

  if [ -n "$body" ]; then
    echo "ok   $name"
  else
    echo "fail $name $url" >&2
    cat /tmp/zhyf-verify-curl.err >&2 || true
    failures=$((failures + 1))
  fi
}

check_http "gateway" "http://$HOST:18080/actuator/health"
check_http "auth-institution" "http://$HOST:18081/actuator/health"
check_http "order-service" "http://$HOST:18082/actuator/health"
check_http "message-service" "http://$HOST:18083/actuator/health"
check_http "prescription-service" "http://$HOST:18084/actuator/health"
check_http "workflow-service" "http://$HOST:18085/actuator/health"
check_http "ops-service" "http://$HOST:18086/actuator/health"
check_http "decoction-service" "http://$HOST:18087/actuator/health"
check_http "logistics-service" "http://$HOST:18088/actuator/health"
check_http "callback-service" "http://$HOST:18089/actuator/health"
check_http "portal-service" "http://$HOST:18090/actuator/health"
check_http "report-service" "http://$HOST:18091/actuator/health"
check_http "integration-service" "http://$HOST:18092/actuator/health"
check_http "admin-web" "http://$HOST/health" "ok"
check_http "ops-dead-letter-proxy" "http://$HOST/ops-api/api/admin/ops/dead-letters" "SUCCESS"

rm -f /tmp/zhyf-verify-curl.err

if [ "$failures" -gt 0 ]; then
  echo "verify failed: $failures check(s)" >&2
  exit 1
fi

echo "verify passed"
