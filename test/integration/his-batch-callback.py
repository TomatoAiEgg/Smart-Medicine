#!/usr/bin/env python3
import argparse
import concurrent.futures
import datetime as dt
import hashlib
import hmac
import json
import os
import queue
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, List, Optional, Tuple, Type


@dataclass
class HttpResult:
    ok: bool
    status: int
    body: str
    elapsed_ms: int
    error: Optional[str] = None


@dataclass
class OrderResult:
    index: int
    external_order_no: str
    ok: bool
    duplicate: bool
    order_id: Optional[str]
    order_no: Optional[str]
    status: int
    elapsed_ms: int
    error: Optional[str]


@dataclass
class CallbackEvent:
    received_at: str
    path: str
    status: int
    order_id: Optional[str]
    order_no: Optional[str]
    callback_type: Optional[str]
    business_id: Optional[str]
    business_status: Optional[str]
    source: Optional[str]
    raw_body: str


@dataclass
class StageResult:
    stage: str
    order_no: str
    ok: bool
    elapsed_ms: int
    detail: str
    error: Optional[str] = None


def now_utc() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds').replace('+00:00', 'Z')


def compact_json(value: Dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def sign_body(app_key: str, app_secret: str, timestamp: str, body: str) -> str:
    body_hash = hashlib.sha256(body.encode('utf-8')).hexdigest()
    source = f'{app_key}\n{timestamp}\n{body_hash}'
    return hmac.new(app_secret.encode('utf-8'), source.encode('utf-8'), hashlib.sha256).hexdigest()


def request_json(method: str, url: str, payload: Optional[Dict[str, Any]] = None,
                 headers: Optional[Dict[str, str]] = None, timeout: float = 10.0) -> HttpResult:
    data = None
    if payload is not None:
        data = compact_json(payload).encode('utf-8')
    request_headers = {'Content-Type': 'application/json'}
    if headers:
        request_headers.update(headers)
    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode('utf-8', errors='replace')
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            return HttpResult(200 <= response.status < 300, response.status, body, elapsed_ms)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode('utf-8', errors='replace')
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return HttpResult(False, exc.code, body, elapsed_ms, body)
    except urllib.error.URLError as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return HttpResult(False, 0, '', elapsed_ms, str(exc.reason))
    except TimeoutError as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return HttpResult(False, 0, '', elapsed_ms, str(exc))
    except OSError as exc:
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        return HttpResult(False, 0, '', elapsed_ms, str(exc))


def parse_api_result(result: HttpResult) -> Tuple[bool, Any, str]:
    if not result.body:
        return result.ok, None, result.error or ''
    try:
        parsed = json.loads(result.body)
    except json.JSONDecodeError:
        return False, None, result.error or 'response is not json'
    if not isinstance(parsed, dict):
        return False, None, 'response is not object'
    code = str(parsed.get('code', ''))
    message = str(parsed.get('message', ''))
    if not result.ok or (code and code != 'SUCCESS'):
        return False, parsed.get('data'), message or result.error or f'HTTP {result.status}'
    return True, parsed.get('data'), message


def build_order_payload(batch_id: str, index: int) -> Dict[str, Any]:
    suffix = f'{index:04d}'
    return {
        'externalOrderNo': f'HIS-BATCH-{batch_id}-{suffix}',
        'patientName': f'Test Patient {suffix}',
        'patientPhone': '13800000000',
        'receiverName': f'Test Receiver {suffix}',
        'receiverPhone': '13800000000',
        'receiverAddress': f'Test Address {suffix}',
        'prescriptions': [
            {
                'externalPrescriptionNo': f'RX-{batch_id}-{suffix}',
                'doctorName': 'Test Doctor',
                'diagnosis': 'Test Diagnosis',
                'details': [
                    {
                        'drugCode': 'DRUG001',
                        'drugName': 'Test Herb',
                        'dose': '10',
                        'unit': 'g',
                    }
                ],
            }
        ],
    }


def create_order(index: int, args: argparse.Namespace, app_secret: str, batch_id: str) -> OrderResult:
    payload = build_order_payload(batch_id, index)
    body = compact_json(payload)
    timestamp = str(int(time.time()))
    signature = sign_body(args.app_key, app_secret, timestamp, body)
    headers = {
        'X-App-Key': args.app_key,
        'X-Timestamp': timestamp,
        'X-Signature': signature,
    }
    url = args.gateway_url.rstrip('/') + '/createOrder'
    result = request_json('POST', url, payload, headers, args.timeout)
    order_id = None
    order_no = None
    duplicate = False
    error = result.error
    try:
        parsed = json.loads(result.body) if result.body else {}
        data = parsed.get('data') if isinstance(parsed, dict) else None
        if isinstance(data, dict):
            order_id = data.get('orderId')
            order_no = data.get('orderNo')
            duplicate = bool(data.get('duplicate'))
        if not result.ok:
            error = parsed.get('message') if isinstance(parsed, dict) else result.error
    except json.JSONDecodeError:
        error = result.error or 'response is not json'

    ok = result.ok and order_no is not None
    return OrderResult(
        index=index,
        external_order_no=payload['externalOrderNo'],
        ok=ok,
        duplicate=duplicate,
        order_id=order_id,
        order_no=order_no,
        status=result.status,
        elapsed_ms=result.elapsed_ms,
        error=error if not ok else None,
    )


def percentile(values: List[int], percent: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    position = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * percent)))
    return ordered[position]


def run_create_only(args: argparse.Namespace) -> Dict[str, Any]:
    app_secret = read_app_secret(args)
    batch_id = args.batch_id or dt.datetime.now().strftime('%Y%m%d%H%M%S')
    print(f'[batch] id={batch_id} mode=create-only count={args.count} concurrency={args.concurrency}')
    results = create_orders(args, app_secret, batch_id)
    return build_create_summary(args, batch_id, results)


def create_orders(args: argparse.Namespace, app_secret: str, batch_id: str) -> List[OrderResult]:
    results: List[OrderResult] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [
            executor.submit(create_order, index, args, app_secret, batch_id)
            for index in range(1, args.count + 1)
        ]
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            results.append(result)
            state = 'ok' if result.ok else 'fail'
            print(
                f'[create] {state} externalOrderNo={result.external_order_no} '
                f'orderNo={result.order_no or "-"} status={result.status} elapsedMs={result.elapsed_ms}'
            )
            if result.error:
                print(f'[create] error externalOrderNo={result.external_order_no} reason={result.error}', file=sys.stderr)
    return results


def build_create_summary(args: argparse.Namespace, batch_id: str, results: List[OrderResult]) -> Dict[str, Any]:
    ok_count = sum(1 for item in results if item.ok)
    duplicate_count = sum(1 for item in results if item.duplicate)
    elapsed = [item.elapsed_ms for item in results if item.ok]
    summary = {
        'batchId': batch_id,
        'mode': 'create-only',
        'count': args.count,
        'concurrency': args.concurrency,
        'summary': {
            'created': ok_count,
            'failed': len(results) - ok_count,
            'duplicate': duplicate_count,
        },
        'metrics': {
            'createP95Ms': percentile(elapsed, 0.95),
            'createP99Ms': percentile(elapsed, 0.99),
        },
        'orders': [asdict(item) for item in sorted(results, key=lambda item: item.index)],
    }
    print(
        f'[summary] created={ok_count} failed={len(results) - ok_count} '
        f'duplicate={duplicate_count} p95CreateMs={summary["metrics"]["createP95Ms"]}'
    )
    write_report(args, summary)
    return summary


def run_full_chain(args: argparse.Namespace) -> Dict[str, Any]:
    app_secret = read_app_secret(args)
    batch_id = args.batch_id or dt.datetime.now().strftime('%Y%m%d%H%M%S')
    print(f'[batch] id={batch_id} mode=full-chain count={args.count} concurrency={args.concurrency}')

    callback_context = start_callback_listener(args) if args.listen_callbacks else None
    try:
        orders = create_orders(args, app_secret, batch_id)
        target_order_nos = [item.order_no for item in orders if item.ok and item.order_no]
        stage_results: List[StageResult] = []

        if target_order_nos:
            stage_results.extend(process_workflow_stage(
                args,
                target_order_nos,
                'review',
                '/api/admin/workflow/review-tasks',
                '/api/admin/workflow/review-tasks/{taskId}/approve',
                'reviewer',
            ))
            stage_results.extend(process_workflow_stage(
                args,
                target_order_nos,
                'dispense',
                '/api/admin/workflow/dispense-tasks',
                '/api/admin/workflow/dispense-tasks/{taskId}/complete',
                'dispenser',
            ))
            stage_results.extend(process_workflow_stage(
                args,
                target_order_nos,
                'recheck',
                '/api/admin/workflow/recheck-tasks',
                '/api/admin/workflow/recheck-tasks/{taskId}/complete',
                'rechecker',
            ))
            stage_results.extend(process_decoction(args, target_order_nos))
            stage_results.extend(process_logistics(args, target_order_nos))

            if args.dispatch_callbacks:
                wait_and_dispatch_callbacks(args, callback_context.store if callback_context else None)

        callback_events: List[CallbackEvent] = []
        if callback_context:
            callback_events = stop_callback_listener(callback_context)

        summary = build_full_chain_summary(args, batch_id, orders, stage_results, callback_events)
        write_report(args, summary)
        print_full_chain_summary(summary)
        return summary
    except BaseException:
        if callback_context:
            stop_callback_listener(callback_context)
        raise


class CallbackStore:
    def __init__(self) -> None:
        self.events: List[CallbackEvent] = []
        self.messages: queue.Queue = queue.Queue()
        self.lock = threading.Lock()

    def add(self, event: CallbackEvent) -> None:
        with self.lock:
            self.events.append(event)
        self.messages.put(event)

    def snapshot(self) -> List[CallbackEvent]:
        with self.lock:
            return list(self.events)


@dataclass
class CallbackContext:
    store: CallbackStore
    server: ThreadingHTTPServer
    thread: threading.Thread


def make_callback_handler(store: CallbackStore) -> Type[BaseHTTPRequestHandler]:
    class CallbackHandler(BaseHTTPRequestHandler):
        server_version = 'ZhyfCallbackTest/1.0'

        def do_POST(self) -> None:
            length = int(self.headers.get('Content-Length', '0'))
            raw = self.rfile.read(length).decode('utf-8', errors='replace')
            parsed: Dict[str, Any] = {}
            try:
                value = json.loads(raw) if raw else {}
                if isinstance(value, dict):
                    parsed = value
            except json.JSONDecodeError:
                parsed = {}
            event = CallbackEvent(
                received_at=now_utc(),
                path=self.path,
                status=200,
                order_id=read_optional_text(parsed, 'orderId'),
                order_no=read_optional_text(parsed, 'orderNo'),
                callback_type=read_optional_text(parsed, 'callbackType'),
                business_id=read_optional_text(parsed, 'businessId'),
                business_status=read_optional_text(parsed, 'businessStatus'),
                source=read_optional_text(parsed, 'source'),
                raw_body=raw,
            )
            store.add(event)
            body = b'{"code":"SUCCESS","message":"received"}'
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt: str, *values: Any) -> None:
            return

    return CallbackHandler


def read_optional_text(value: Dict[str, Any], key: str) -> Optional[str]:
    item = value.get(key)
    if item is None:
        return None
    return str(item)


def parse_listen(value: str) -> Tuple[str, int]:
    if ':' not in value:
        raise argparse.ArgumentTypeError('listen address must be host:port')
    host, port_text = value.rsplit(':', 1)
    try:
        port = int(port_text)
    except ValueError as exc:
        raise argparse.ArgumentTypeError('listen port must be number') from exc
    return host, port


def start_callback_listener(args: argparse.Namespace) -> CallbackContext:
    host, port = parse_listen(args.callback_listen)
    store = CallbackStore()
    server = ThreadingHTTPServer((host, port), make_callback_handler(store))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    print(f'[callback] listening=http://{host}:{port}/callback')
    return CallbackContext(store, server, thread)


def stop_callback_listener(context: CallbackContext) -> List[CallbackEvent]:
    context.server.shutdown()
    context.thread.join(timeout=5)
    drain_callback_messages(context.store)
    return context.store.snapshot()


def run_callback_only(args: argparse.Namespace) -> Dict[str, Any]:
    host, port = parse_listen(args.callback_listen)
    store = CallbackStore()
    server = ThreadingHTTPServer((host, port), make_callback_handler(store))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    print(f'[callback] listening=http://{host}:{port}/callback expected={args.expected_callbacks} timeout={args.wait_seconds}s')

    started = time.monotonic()
    next_dispatch = 0.0
    try:
        while time.monotonic() - started < args.wait_seconds:
            if args.dispatch_callbacks and time.monotonic() >= next_dispatch:
                dispatch_callbacks(args)
                next_dispatch = time.monotonic() + args.dispatch_interval_seconds
            drain_callback_messages(store)
            if args.expected_callbacks > 0 and len(store.snapshot()) >= args.expected_callbacks:
                break
            time.sleep(0.3)
    finally:
        server.shutdown()
        thread.join(timeout=5)

    events = store.snapshot()
    summary = {
        'batchId': args.batch_id or dt.datetime.now().strftime('%Y%m%d%H%M%S'),
        'mode': 'callback-only',
        'summary': {
            'callbacksReceived': len(events),
            'expectedCallbacks': args.expected_callbacks,
        },
        'callbacks': [asdict(item) for item in events],
    }
    print(f'[summary] callbacksReceived={len(events)} expectedCallbacks={args.expected_callbacks}')
    write_report(args, summary)
    return summary


def process_workflow_stage(
        args: argparse.Namespace,
        order_nos: List[str],
        stage: str,
        list_path: str,
        action_path_template: str,
        actor_role: str,
) -> List[StageResult]:
    pending = set(order_nos)
    results: List[StageResult] = []
    started = time.monotonic()
    while pending and time.monotonic() - started < args.stage_timeout_seconds:
        data, error = api_get(join_url(args.workflow_api_url, list_path), args)
        if error:
            print(f'[workflow] list {stage} error={error}', file=sys.stderr)
            time.sleep(args.poll_interval_seconds)
            continue
        tasks = data if isinstance(data, list) else []
        progressed = False
        for task in tasks:
            if not isinstance(task, dict):
                continue
            order_no = read_optional_text(task, 'orderNo')
            task_id = read_optional_text(task, 'taskId')
            if not order_no or not task_id or order_no not in pending:
                continue
            payload = {
                'reviewer': args.operator,
                'reviewComment': f'{stage} by his-batch-callback',
            }
            url = join_url(args.workflow_api_url, action_path_template.format(taskId=task_id))
            action_started = time.perf_counter()
            ok, _, message, http = api_request('PATCH', url, payload, args)
            elapsed_ms = int((time.perf_counter() - action_started) * 1000)
            if ok:
                pending.remove(order_no)
                progressed = True
                print(f'[workflow] {stage} ok orderNo={order_no} taskId={task_id} elapsedMs={elapsed_ms}')
                results.append(StageResult(stage, order_no, True, elapsed_ms, task_id))
            else:
                detail = message or http.error or f'HTTP {http.status}'
                print(f'[workflow] {stage} fail orderNo={order_no} taskId={task_id} reason={detail}', file=sys.stderr)
                results.append(StageResult(stage, order_no, False, elapsed_ms, task_id, detail))
        if pending and not progressed:
            time.sleep(args.poll_interval_seconds)

    for order_no in sorted(pending):
        print(f'[workflow] {stage} timeout orderNo={order_no}', file=sys.stderr)
        results.append(StageResult(stage, order_no, False, 0, 'timeout', 'WORKFLOW_TIMEOUT'))
    return results


def process_decoction(args: argparse.Namespace, order_nos: List[str]) -> List[StageResult]:
    pending = set(order_nos)
    results: List[StageResult] = []
    started = time.monotonic()
    while pending and time.monotonic() - started < args.stage_timeout_seconds:
        data, error = api_get(join_url(args.decoction_api_url, '/simulator/pda/prescriptions/can-operate?limit=200'), args)
        if error:
            print(f'[decoction] list can-operate error={error}', file=sys.stderr)
            time.sleep(args.poll_interval_seconds)
            continue
        prescriptions = data if isinstance(data, list) else []
        progressed = False
        for prescription in prescriptions:
            if not isinstance(prescription, dict):
                continue
            order_no = read_optional_text(prescription, 'orderNo')
            prescription_no = read_optional_text(prescription, 'prescriptionNo')
            if not order_no or not prescription_no or order_no not in pending:
                continue
            result = finish_decoction_for_prescription(args, order_no, prescription_no)
            results.append(result)
            if result.ok:
                pending.remove(order_no)
                progressed = True
        if pending and not progressed:
            time.sleep(args.poll_interval_seconds)

    for order_no in sorted(pending):
        print(f'[decoction] timeout orderNo={order_no}', file=sys.stderr)
        results.append(StageResult('decoction', order_no, False, 0, 'timeout', 'DECOCTION_TIMEOUT'))
    return results


def finish_decoction_for_prescription(args: argparse.Namespace, order_no: str, prescription_no: str) -> StageResult:
    device_code = args.device_code or find_device_code(args)
    if not device_code:
        return StageResult('decoction', order_no, False, 0, prescription_no, 'DEVICE_NOT_FOUND')
    pail_no = f'PAIL-{order_no[-6:]}'
    commands = [
        ('bind', '/simulator/pda/bind-prescription'),
        ('start', '/simulator/pda/decoction/start'),
        ('finish', '/simulator/pda/decoction/finish'),
    ]
    total_elapsed = 0
    for action, path in commands:
        payload = {
            'operationId': f'{args.operator}-{action}-{order_no}-{int(time.time() * 1000)}',
            'deviceCode': device_code,
            'prescriptionNo': prescription_no,
            'pailNo': pail_no,
            'operator': args.operator,
            'timestamp': now_utc(),
            'sign': '',
        }
        action_started = time.perf_counter()
        ok, _, message, http = api_request('POST', join_url(args.decoction_api_url, path), payload, args)
        elapsed_ms = int((time.perf_counter() - action_started) * 1000)
        total_elapsed += elapsed_ms
        if not ok:
            detail = message or http.error or f'HTTP {http.status}'
            print(f'[decoction] {action} fail orderNo={order_no} prescriptionNo={prescription_no} reason={detail}',
                  file=sys.stderr)
            return StageResult('decoction', order_no, False, total_elapsed, prescription_no, detail)
    print(f'[decoction] finished orderNo={order_no} prescriptionNo={prescription_no} elapsedMs={total_elapsed}')
    return StageResult('decoction', order_no, True, total_elapsed, prescription_no)


def find_device_code(args: argparse.Namespace) -> Optional[str]:
    data, error = api_get(join_url(args.decoction_api_url, '/simulator/pda/decoction/devices'), args)
    if error:
        print(f'[decoction] list devices error={error}', file=sys.stderr)
        return None
    devices = data if isinstance(data, list) else []
    for device in devices:
        if isinstance(device, dict) and read_optional_text(device, 'deviceCode'):
            return read_optional_text(device, 'deviceCode')
    return None


def process_logistics(args: argparse.Namespace, order_nos: List[str]) -> List[StageResult]:
    pending = set(order_nos)
    results: List[StageResult] = []
    started = time.monotonic()
    while pending and time.monotonic() - started < args.stage_timeout_seconds:
        data, error = api_get(join_url(args.logistics_api_url, '/api/admin/logistics/orders/ready?limit=200'), args)
        if error:
            print(f'[logistics] list ready error={error}', file=sys.stderr)
            time.sleep(args.poll_interval_seconds)
            continue
        ready_orders = data if isinstance(data, list) else []
        progressed = False
        for item in ready_orders:
            if not isinstance(item, dict):
                continue
            order_no = read_optional_text(item, 'orderNo')
            if not order_no or order_no not in pending:
                continue
            result = finish_logistics_for_order(args, order_no)
            results.append(result)
            if result.ok:
                pending.remove(order_no)
                progressed = True
        if pending and not progressed:
            time.sleep(args.poll_interval_seconds)

    for order_no in sorted(pending):
        print(f'[logistics] timeout orderNo={order_no}', file=sys.stderr)
        results.append(StageResult('logistics', order_no, False, 0, 'timeout', 'LOGISTICS_TIMEOUT'))
    return results


def finish_logistics_for_order(args: argparse.Namespace, order_no: str) -> StageResult:
    started = time.perf_counter()
    pack_payload = {
        'orderNo': order_no,
        'logisticsCompany': args.logistics_company,
        'logisticsNo': f'MOCK-{order_no}',
        'payMethod': 'SENDER_PAY',
        'pkgWeight': 1.0,
        'pkgNum': 1,
        'operator': args.operator,
    }
    ok, data, message, http = api_request(
        'POST',
        join_url(args.logistics_api_url, '/api/admin/logistics/shipments/pack'),
        pack_payload,
        args,
    )
    if not ok or not isinstance(data, dict):
        detail = message or http.error or f'HTTP {http.status}'
        return StageResult('logistics', order_no, False, elapsed_since(started), 'pack', detail)
    shipment_id = read_optional_text(data, 'shipmentId')
    if not shipment_id:
        return StageResult('logistics', order_no, False, elapsed_since(started), 'pack', 'SHIPMENT_ID_EMPTY')

    for action in ['ship', 'sign']:
        payload = {'operator': args.operator, 'remark': f'{action} by his-batch-callback'}
        ok, _, message, http = api_request(
            'PATCH',
            join_url(args.logistics_api_url, f'/api/admin/logistics/shipments/{shipment_id}/{action}'),
            payload,
            args,
        )
        if not ok:
            detail = message or http.error or f'HTTP {http.status}'
            print(f'[logistics] {action} fail orderNo={order_no} shipmentId={shipment_id} reason={detail}',
                  file=sys.stderr)
            return StageResult('logistics', order_no, False, elapsed_since(started), shipment_id, detail)
    elapsed_ms = elapsed_since(started)
    print(f'[logistics] signed orderNo={order_no} shipmentId={shipment_id} elapsedMs={elapsed_ms}')
    return StageResult('logistics', order_no, True, elapsed_ms, shipment_id)


def wait_and_dispatch_callbacks(args: argparse.Namespace, store: Optional[CallbackStore]) -> None:
    started = time.monotonic()
    next_dispatch = 0.0
    while time.monotonic() - started < args.callback_wait_seconds:
        if time.monotonic() >= next_dispatch:
            dispatch_callbacks(args)
            next_dispatch = time.monotonic() + args.dispatch_interval_seconds
        if store:
            drain_callback_messages(store)
            if args.expected_callbacks > 0 and len(store.snapshot()) >= args.expected_callbacks:
                return
        time.sleep(0.3)


def api_get(url: str, args: argparse.Namespace) -> Tuple[Any, Optional[str]]:
    ok, data, message, http = api_request('GET', url, None, args)
    if ok:
        return data, None
    return None, message or http.error or f'HTTP {http.status}'


def join_url(base: str, path: str) -> str:
    return base.rstrip('/') + '/' + path.lstrip('/')


def api_request(
        method: str,
        url: str,
        payload: Optional[Dict[str, Any]],
        args: argparse.Namespace,
) -> Tuple[bool, Any, str, HttpResult]:
    result = request_json(method, url, payload, timeout=args.timeout)
    ok, data, message = parse_api_result(result)
    return ok, data, message, result


def elapsed_since(started: float) -> int:
    return int((time.perf_counter() - started) * 1000)


def build_full_chain_summary(
        args: argparse.Namespace,
        batch_id: str,
        orders: List[OrderResult],
        stage_results: List[StageResult],
        callbacks: List[CallbackEvent],
) -> Dict[str, Any]:
    stages = ['review', 'dispense', 'recheck', 'decoction', 'logistics']
    stage_summary = {}
    for stage in stages:
        rows = [item for item in stage_results if item.stage == stage]
        stage_summary[stage] = {
            'success': sum(1 for item in rows if item.ok),
            'failed': sum(1 for item in rows if not item.ok),
        }
    created = sum(1 for item in orders if item.ok)
    return {
        'batchId': batch_id,
        'mode': 'full-chain',
        'count': args.count,
        'concurrency': args.concurrency,
        'summary': {
            'created': created,
            'createFailed': len(orders) - created,
            'callbacksReceived': len(callbacks),
            'expectedCallbacks': args.expected_callbacks,
        },
        'stages': stage_summary,
        'orders': [asdict(item) for item in sorted(orders, key=lambda item: item.index)],
        'stageResults': [asdict(item) for item in stage_results],
        'callbacks': [asdict(item) for item in callbacks],
    }


def print_full_chain_summary(summary: Dict[str, Any]) -> None:
    base = summary['summary']
    print(
        f'[summary] created={base["created"]} createFailed={base["createFailed"]} '
        f'callbacksReceived={base["callbacksReceived"]} expectedCallbacks={base["expectedCallbacks"]}'
    )
    for stage, item in summary['stages'].items():
        print(f'[summary] stage={stage} success={item["success"]} failed={item["failed"]}')


def drain_callback_messages(store: CallbackStore) -> None:
    while True:
        try:
            event = store.messages.get_nowait()
        except queue.Empty:
            return
        print(
            f'[callback] received orderNo={event.order_no or "-"} '
            f'type={event.callback_type or "-"} status={event.business_status or "-"}'
        )


def dispatch_callbacks(args: argparse.Namespace) -> None:
    url = args.callback_api_url.rstrip('/') + f'/api/admin/callback-records/dispatch-due?limit={args.dispatch_limit}'
    result = request_json('POST', url, None, timeout=args.timeout)
    state = 'ok' if result.ok else 'fail'
    print(f'[dispatch] {state} status={result.status} elapsedMs={result.elapsed_ms}')
    if result.error:
        print(f'[dispatch] error reason={result.error}', file=sys.stderr)


def read_app_secret(args: argparse.Namespace) -> str:
    if args.app_secret:
        return args.app_secret
    secret = os.environ.get(args.app_secret_env)
    if not secret:
        raise SystemExit(f'missing app secret, set environment variable {args.app_secret_env}')
    return secret


def write_report(args: argparse.Namespace, report: Dict[str, Any]) -> None:
    if not args.report:
        return
    path = args.report
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print(f'[report] path={path}')


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description='HIS batch order and callback verification tool')
    parser.add_argument('--mode', choices=['create-only', 'callback-only', 'full-chain'], default='create-only')
    parser.add_argument('--gateway-url', default='http://127.0.0.1/api/institution')
    parser.add_argument('--workflow-api-url', default='http://127.0.0.1/workflow-api')
    parser.add_argument('--decoction-api-url', default='http://127.0.0.1/decoction-api')
    parser.add_argument('--logistics-api-url', default='http://127.0.0.1/logistics-api')
    parser.add_argument('--callback-api-url', default='http://127.0.0.1/callback-api')
    parser.add_argument('--app-key', default='demo-app')
    parser.add_argument('--app-secret', default='')
    parser.add_argument('--app-secret-env', default='ZHYF_TEST_APP_SECRET')
    parser.add_argument('--batch-id', default='')
    parser.add_argument('--count', type=int, default=1)
    parser.add_argument('--concurrency', type=int, default=1)
    parser.add_argument('--timeout', type=float, default=10.0)
    parser.add_argument('--report', default='')
    parser.add_argument('--operator', default='his-batch')
    parser.add_argument('--stage-timeout-seconds', type=int, default=180)
    parser.add_argument('--poll-interval-seconds', type=float, default=2.0)
    parser.add_argument('--device-code', default='')
    parser.add_argument('--logistics-company', default='MOCK')
    parser.add_argument('--callback-listen', default='0.0.0.0:19081')
    parser.add_argument('--listen-callbacks', action='store_true')
    parser.add_argument('--expected-callbacks', type=int, default=0)
    parser.add_argument('--wait-seconds', type=int, default=60)
    parser.add_argument('--callback-wait-seconds', type=int, default=60)
    parser.add_argument('--dispatch-callbacks', action='store_true')
    parser.add_argument('--dispatch-interval-seconds', type=int, default=5)
    parser.add_argument('--dispatch-limit', type=int, default=200)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.count <= 0:
        raise SystemExit('--count must be positive')
    if args.concurrency <= 0:
        raise SystemExit('--concurrency must be positive')
    if args.mode == 'create-only':
        summary = run_create_only(args)
        return 0 if summary['summary']['failed'] == 0 else 1
    if args.mode == 'full-chain':
        summary = run_full_chain(args)
        failed = summary['summary']['createFailed']
        failed += sum(item['failed'] for item in summary['stages'].values())
        if args.expected_callbacks > 0 and summary['summary']['callbacksReceived'] < args.expected_callbacks:
            failed += 1
        return 0 if failed == 0 else 1
    if args.mode == 'callback-only':
        summary = run_callback_only(args)
        if args.expected_callbacks > 0:
            return 0 if summary['summary']['callbacksReceived'] >= args.expected_callbacks else 1
        return 0
    raise SystemExit(f'unsupported mode: {args.mode}')


if __name__ == '__main__':
    raise SystemExit(main())
