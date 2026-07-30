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

export function enabledText(value: boolean) {
  return value ? '启用' : '停用';
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
