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
