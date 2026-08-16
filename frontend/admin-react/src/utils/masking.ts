export function maskPhone(value: string | null | undefined) {
  if (!value?.trim()) {
    return '-';
  }

  const trimmedValue = value.trim();
  const digits = trimmedValue.replace(/\D/g, '');

  if (digits.length === 11) {
    return `${digits.slice(0, 3)}****${digits.slice(-4)}`;
  }

  const chars = Array.from(trimmedValue);

  if (chars.length <= 4) {
    return '*'.repeat(chars.length);
  }

  if (chars.length <= 7) {
    return `${chars[0]}${'*'.repeat(chars.length - 2)}${chars[chars.length - 1]}`;
  }

  return `${chars.slice(0, 2).join('')}****${chars.slice(-2).join('')}`;
}

export function maskName(value: string | null | undefined) {
  if (!value?.trim()) {
    return '-';
  }

  const chars = Array.from(value.trim());

  if (chars.length === 1) {
    return '*';
  }

  return `${chars[0]}${'*'.repeat(chars.length - 1)}`;
}

export function maskAddress(value: string | null | undefined) {
  const trimmedValue = value?.trim();

  if (!trimmedValue) {
    return '-';
  }

  const chars = Array.from(trimmedValue);

  if (chars.length === 1) {
    return '*';
  }

  if (chars.length <= 4) {
    return `${chars[0]}${'*'.repeat(chars.length - 1)}`;
  }

  const visibleLength = Math.min(chars.length - 1, 6);

  return `${chars.slice(0, visibleLength).join('')}****`;
}

export function maskSecret(value: string | null | undefined) {
  if (!value?.trim()) {
    return '-';
  }

  return '******';
}

export function maskSensitiveValue(fieldName: string, value: unknown): string | null {
  const normalizedName = fieldName.toLowerCase();
  const text = typeof value === 'string' || typeof value === 'number' ? String(value) : null;

  if (text === null) {
    return null;
  }

  if (/password|secret|token|privatekey|apikey|appsecret|credential/.test(normalizedName)) {
    return maskSecret(text);
  }

  if (/phone|mobile|tel/.test(normalizedName)) {
    return maskPhone(text);
  }

  if (/address|addr/.test(normalizedName)) {
    return maskAddress(text);
  }

  if (/patientname|receivername|contactname/.test(normalizedName)) {
    return maskName(text);
  }

  if (/identity|idcard|certificate|certno/.test(normalizedName)) {
    const chars = Array.from(text.trim());
    if (chars.length <= 4) return maskSecret(text);
    return `${chars.slice(0, 2).join('')}****${chars.slice(-2).join('')}`;
  }

  return null;
}
