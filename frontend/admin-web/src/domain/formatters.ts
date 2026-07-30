export const EMPTY_VALUE = '-';

export function formatDate(value: string | null | undefined) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date);
}

export function formatNumber(value: number | null | undefined) {
  return new Intl.NumberFormat('zh-CN').format(value || 0);
}

export function displayValue(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined || value === '') return EMPTY_VALUE;
  if (typeof value === 'boolean') return value ? '是' : '否';
  return String(value);
}

export function labelFromMap(
  value: string | number | null | undefined,
  labels: Record<string, string>,
  fallback = EMPTY_VALUE,
) {
  if (value === null || value === undefined || value === '') return fallback;
  const key = String(value);
  return labels[key] ?? key;
}

export function enabledText(value: boolean) {
  return value ? '启用' : '停用';
}

export function enabledBooleanParam(value: '' | 'true' | 'false') {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

export function enabledStringParam(value: '' | 'true' | 'false') {
  return value === '' ? undefined : value;
}

export function boundedPositiveInteger(value: number, fallback: number, max: number) {
  if (!Number.isFinite(value) || value <= 0) return fallback;
  return Math.min(Math.trunc(value), max);
}

export function pageSummaryText(count: number, totalLabel = '项', totalCount = count) {
  return `显示第 ${count > 0 ? 1 : 0} 至 ${count} 项记录，共 ${totalCount} ${totalLabel}`;
}

export function defaultDate(offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

export function currentIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

export function dateInputToIso(value: string, endExclusive = false) {
  if (!value.trim()) return undefined;
  const date = new Date(`${value.trim()}T00:00:00.000Z`);
  if (Number.isNaN(date.getTime())) return undefined;
  if (endExclusive) {
    date.setUTCDate(date.getUTCDate() + 1);
  }
  return date.toISOString();
}
