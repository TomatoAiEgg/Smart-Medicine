#!/usr/bin/env python3
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Tuple


FAILED_CONSUME_STATUSES = {'FAILED', 'FAILED_RETRYABLE', 'FAILED_FATAL', 'DEAD'}
FAILED_CALLBACK_STATUSES = {'FAILED', 'DEAD'}
OPEN_DEAD_LETTER_STATUSES = {'OPEN'}


def request_json(url: str, timeout: float) -> Tuple[bool, int, Dict[str, Any], int, str]:
    started = time.perf_counter()
    request = urllib.request.Request(url, headers={'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode('utf-8', errors='replace')
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            return True, response.status, json.loads(body), elapsed_ms, ''
    except urllib.error.HTTPError as exc:
        body = exc.read().decode('utf-8', errors='replace')
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        try:
            parsed = json.loads(body)
        except json.JSONDecodeError:
            parsed = {}
        return False, exc.code, parsed, elapsed_ms, body[:300]
    except urllib.error.URLError as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return False, 0, {}, elapsed_ms, str(exc.reason)
    except (OSError, json.JSONDecodeError) as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return False, 0, {}, elapsed_ms, str(exc)


def count_items(data: Dict[str, Any], field: str) -> int:
    value = data.get(field)
    return len(value) if isinstance(value, list) else 0


def list_items(data: Dict[str, Any], field: str) -> List[Dict[str, Any]]:
    value = data.get(field)
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


def add_check(checks: List[Dict[str, Any]], name: str, ok: bool, detail: str) -> None:
    checks.append({'name': name, 'ok': ok, 'detail': detail})
    state = 'ok' if ok else 'fail'
    print(f'[{state}] {name} detail={detail}')


def build_url(args: argparse.Namespace) -> str:
    params = {'limit': str(args.limit)}
    if args.order_no:
        params['orderNo'] = args.order_no
    if args.external_order_no:
        params['externalOrderNo'] = args.external_order_no
    return (
        args.web_base_url.rstrip('/')
        + '/ops-api/api/admin/ops/order-observability?'
        + urllib.parse.urlencode(params)
    )


def verify_payload(args: argparse.Namespace, data: Dict[str, Any]) -> List[Dict[str, Any]]:
    checks: List[Dict[str, Any]] = []
    order = data.get('order') if isinstance(data.get('order'), dict) else {}
    add_check(checks, 'order-present', bool(order), 'order object exists')
    if args.order_no:
        add_check(
            checks,
            'order-no-match',
            order.get('orderNo') == args.order_no,
            f"expected={args.order_no} actual={order.get('orderNo')}",
        )
    if args.external_order_no:
        add_check(
            checks,
            'external-order-no-match',
            order.get('externalOrderNo') == args.external_order_no,
            f"expected={args.external_order_no} actual={order.get('externalOrderNo')}",
        )

    count_expectations = [
        ('status-logs', 'statusLogs', args.min_status_logs),
        ('workflow-tasks', 'workflowTasks', args.min_workflow_tasks),
        ('outbox-events', 'outboxEvents', args.min_outbox),
        ('message-consume-logs', 'messageConsumeLogs', args.min_consume),
        ('callback-records', 'callbackRecords', args.min_callbacks),
        ('access-logs', 'recentAccessLogs', args.min_access_logs),
    ]
    for name, field, minimum in count_expectations:
        actual = count_items(data, field)
        add_check(checks, name, actual >= minimum, f'count={actual} minimum={minimum}')

    failed_consumes = [
        item for item in list_items(data, 'messageConsumeLogs')
        if item.get('status') in FAILED_CONSUME_STATUSES
    ]
    add_check(
        checks,
        'no-failed-consume',
        args.allow_failed_consume or not failed_consumes,
        f'failed={len(failed_consumes)}',
    )

    failed_callbacks = [
        item for item in list_items(data, 'callbackRecords')
        if item.get('status') in FAILED_CALLBACK_STATUSES
    ]
    add_check(
        checks,
        'no-failed-callback',
        args.allow_failed_callback or not failed_callbacks,
        f'failed={len(failed_callbacks)}',
    )

    open_dead_letters = [
        item for item in list_items(data, 'deadLetters')
        if item.get('status') in OPEN_DEAD_LETTER_STATUSES
    ]
    add_check(
        checks,
        'no-open-dead-letter',
        args.allow_open_dead_letter or not open_dead_letters,
        f'open={len(open_dead_letters)}',
    )
    return checks


def write_report(args: argparse.Namespace, report: Dict[str, Any]) -> None:
    if not args.report:
        return
    directory = os.path.dirname(args.report)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(args.report, 'w', encoding='utf-8') as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print(f'[report] path={args.report}')


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description='Verify one order observability bundle from ops-service')
    parser.add_argument('--web-base-url', default='http://127.0.0.1')
    parser.add_argument('--order-no', default='')
    parser.add_argument('--external-order-no', default='')
    parser.add_argument('--limit', type=int, default=50)
    parser.add_argument('--timeout', type=float, default=5.0)
    parser.add_argument('--report', default='')
    parser.add_argument('--min-status-logs', type=int, default=1)
    parser.add_argument('--min-workflow-tasks', type=int, default=0)
    parser.add_argument('--min-outbox', type=int, default=1)
    parser.add_argument('--min-consume', type=int, default=0)
    parser.add_argument('--min-callbacks', type=int, default=0)
    parser.add_argument('--min-access-logs', type=int, default=0)
    parser.add_argument('--allow-failed-consume', action='store_true')
    parser.add_argument('--allow-failed-callback', action='store_true')
    parser.add_argument('--allow-open-dead-letter', action='store_true')
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if not args.order_no and not args.external_order_no:
        print('[fail] input detail=--order-no or --external-order-no is required')
        return 2

    url = build_url(args)
    ok, status, payload, elapsed_ms, detail = request_json(url, args.timeout)
    print(f'[http] status={status} elapsedMs={elapsed_ms} detail={detail or "ok"}')
    if not ok or payload.get('code') != 'SUCCESS':
        report = {
            'generatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
            'url': url,
            'httpStatus': status,
            'payload': payload,
            'error': detail,
        }
        write_report(args, report)
        return 1

    data = payload.get('data') if isinstance(payload.get('data'), dict) else {}
    checks = verify_payload(args, data)
    failed = [item for item in checks if not item['ok']]
    report = {
        'generatedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'url': url,
        'httpStatus': status,
        'summary': {
            'total': len(checks),
            'passed': len(checks) - len(failed),
            'failed': len(failed),
        },
        'checks': checks,
        'data': data,
    }
    write_report(args, report)
    print(f'[summary] total={len(checks)} passed={len(checks) - len(failed)} failed={len(failed)}')
    return 0 if not failed else 1


if __name__ == '__main__':
    sys.exit(main())
