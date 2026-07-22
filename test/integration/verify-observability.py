#!/usr/bin/env python3
import argparse
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional, Tuple


SERVICES = [
    ('gateway', 18080),
    ('auth-institution', 18081),
    ('order-service', 18082),
    ('message-service', 18083),
    ('prescription-service', 18084),
    ('workflow-service', 18085),
    ('ops-service', 18086),
    ('decoction-service', 18087),
    ('logistics-service', 18088),
    ('callback-service', 18089),
    ('portal-service', 18090),
    ('report-service', 18091),
    ('integration-service', 18092),
]


OPS_ENDPOINTS = [
    ('ops-health-overview', '/ops-api/api/admin/ops/health-overview?recentHours=24', 'SUCCESS'),
    ('ops-api-access-logs', '/ops-api/api/admin/ops/api-access-logs?limit=1', 'SUCCESS'),
    ('ops-outbox', '/ops-api/api/admin/ops/outbox?limit=1', 'SUCCESS'),
    ('ops-message-consume-logs', '/ops-api/api/admin/ops/message-consume-logs?limit=1', 'SUCCESS'),
    ('ops-dead-letters', '/ops-api/api/admin/ops/dead-letters?limit=1', 'SUCCESS'),
    ('ops-callback-issues', '/ops-api/api/admin/ops/logistics-callback-issues?limit=1', 'SUCCESS'),
]


@dataclass
class CheckResult:
    name: str
    ok: bool
    status: int
    elapsed_ms: int
    detail: str


def request_text(url: str, timeout: float) -> Tuple[bool, int, str, int, str]:
    started = time.perf_counter()
    request = urllib.request.Request(url, headers={'Accept': 'application/json,text/plain,*/*'})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode('utf-8', errors='replace')
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            return 200 <= response.status < 300, response.status, body, elapsed_ms, ''
    except urllib.error.HTTPError as exc:
        body = exc.read().decode('utf-8', errors='replace')
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return False, exc.code, body, elapsed_ms, body[:300]
    except urllib.error.URLError as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return False, 0, '', elapsed_ms, str(exc.reason)
    except OSError as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return False, 0, '', elapsed_ms, str(exc)


def check_url(name: str, url: str, timeout: float, contains: Optional[str] = None) -> CheckResult:
    ok, status, body, elapsed_ms, error = request_text(url, timeout)
    detail = 'ok'
    if error:
        detail = error
    if ok and contains and contains not in body:
        ok = False
        detail = f'missing text: {contains}'
    print_result(name, ok, status, elapsed_ms, detail)
    return CheckResult(name, ok, status, elapsed_ms, detail)


def print_result(name: str, ok: bool, status: int, elapsed_ms: int, detail: str) -> None:
    state = 'ok' if ok else 'fail'
    print(f'[{state}] {name} status={status} elapsedMs={elapsed_ms} detail={detail}')


def check_service_health(args: argparse.Namespace) -> List[CheckResult]:
    results = []
    for service, port in SERVICES:
        url = f'{args.service_scheme}://{args.service_host}:{port}/actuator/health'
        results.append(check_url(f'health:{service}', url, args.timeout, 'UP'))
    return results


def check_service_metrics(args: argparse.Namespace) -> List[CheckResult]:
    results = []
    for service, port in SERVICES:
        url = f'{args.service_scheme}://{args.service_host}:{port}/actuator/prometheus'
        result = check_url(f'metrics:{service}', url, args.timeout, 'jvm_memory_used_bytes')
        results.append(result)
    return results


def check_ops(args: argparse.Namespace) -> List[CheckResult]:
    results = []
    base = args.web_base_url.rstrip('/')
    results.append(check_url('admin-web-health', base + '/health', args.timeout, 'ok'))
    for name, path, expected in OPS_ENDPOINTS:
        results.append(check_url(name, base + path, args.timeout, expected))
    return results


def check_prometheus(args: argparse.Namespace) -> List[CheckResult]:
    if args.skip_prometheus:
        return []
    results = []
    base = args.prometheus_url.rstrip('/')
    targets_url = base + '/api/v1/targets'
    ok, status, body, elapsed_ms, error = request_text(targets_url, args.timeout)
    detail = error or 'ok'
    targets_ok = False
    if ok:
        try:
            parsed = json.loads(body)
            active_targets = parsed.get('data', {}).get('activeTargets', [])
            unhealthy = [
                target.get('scrapeUrl') or target.get('labels', {}).get('instance', '')
                for target in active_targets
                if target.get('health') != 'up'
            ]
            targets_ok = len(active_targets) > 0 and not unhealthy
            detail = f'activeTargets={len(active_targets)} unhealthy={len(unhealthy)}'
            if unhealthy:
                detail += ' firstUnhealthy=' + str(unhealthy[:3])
        except (json.JSONDecodeError, AttributeError):
            targets_ok = False
            detail = 'invalid prometheus targets response'
    print_result('prometheus-targets', targets_ok, status, elapsed_ms, detail)
    results.append(CheckResult('prometheus-targets', targets_ok, status, elapsed_ms, detail))

    query = urllib.parse.quote('up{job="zhyf-services"}')
    results.append(check_url('prometheus-query-up', base + '/api/v1/query?query=' + query, args.timeout, '"status":"success"'))
    return results


def check_grafana(args: argparse.Namespace) -> List[CheckResult]:
    if args.skip_grafana:
        return []
    return [check_url('grafana-health', args.grafana_url.rstrip('/') + '/api/health', args.timeout, 'database')]


def write_report(args: argparse.Namespace, results: List[CheckResult]) -> None:
    if not args.report:
        return
    directory = os.path.dirname(args.report)
    if directory:
        os.makedirs(directory, exist_ok=True)
    report = {
        'generatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'summary': {
            'total': len(results),
            'passed': sum(1 for item in results if item.ok),
            'failed': sum(1 for item in results if not item.ok),
        },
        'checks': [asdict(item) for item in results],
    }
    with open(args.report, 'w', encoding='utf-8') as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print(f'[report] path={args.report}')


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description='Verify logging and monitoring observability surface')
    parser.add_argument('--service-host', default='127.0.0.1')
    parser.add_argument('--service-scheme', default='http')
    parser.add_argument('--web-base-url', default='http://127.0.0.1')
    parser.add_argument('--prometheus-url', default='http://127.0.0.1:19090')
    parser.add_argument('--grafana-url', default='http://127.0.0.1:13000')
    parser.add_argument('--timeout', type=float, default=5.0)
    parser.add_argument('--report', default='')
    parser.add_argument('--skip-health', action='store_true')
    parser.add_argument('--skip-metrics', action='store_true')
    parser.add_argument('--skip-ops', action='store_true')
    parser.add_argument('--skip-prometheus', action='store_true')
    parser.add_argument('--skip-grafana', action='store_true')
    return parser


def main() -> int:
    args = build_parser().parse_args()
    results: List[CheckResult] = []
    if not args.skip_health:
        results.extend(check_service_health(args))
    if not args.skip_metrics:
        results.extend(check_service_metrics(args))
    if not args.skip_ops:
        results.extend(check_ops(args))
    results.extend(check_prometheus(args))
    results.extend(check_grafana(args))
    write_report(args, results)
    failed = [item for item in results if not item.ok]
    print(f'[summary] total={len(results)} passed={len(results) - len(failed)} failed={len(failed)}')
    return 0 if not failed else 1


if __name__ == '__main__':
    raise SystemExit(main())
