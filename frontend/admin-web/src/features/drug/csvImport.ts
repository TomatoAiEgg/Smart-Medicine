export interface CsvRow {
  rowNumber: number;
  values: Record<string, string>;
}

type CsvExportValue = string | number | null | undefined;

export function parseCsv(text: string): CsvRow[] {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentCell = '';
  let quoted = false;
  const normalizedText = text.replace(/^\uFEFF/, '');

  for (let index = 0; index < normalizedText.length; index += 1) {
    const char = normalizedText[index];
    const nextChar = normalizedText[index + 1];
    if (char === '"') {
      if (quoted && nextChar === '"') {
        currentCell += '"';
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (char === ',' && !quoted) {
      currentRow.push(currentCell);
      currentCell = '';
    } else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && nextChar === '\n') {
        index += 1;
      }
      currentRow.push(currentCell);
      rows.push(currentRow);
      currentRow = [];
      currentCell = '';
    } else {
      currentCell += char;
    }
  }

  if (quoted) {
    throw new Error('CSV 引号未闭合，请检查文件格式');
  }

  currentRow.push(currentCell);
  rows.push(currentRow);

  const header = rows.shift()?.map((value) => value.trim()) ?? [];
  if (header.length === 0 || header.every((value) => value === '')) return [];

  return rows
    .map((row, rowIndex) => {
      const values: Record<string, string> = {};
      header.forEach((key, cellIndex) => {
        if (key) {
          values[key] = (row[cellIndex] ?? '').trim();
        }
      });
      return {
        rowNumber: rowIndex + 2,
        values,
      };
    })
    .filter((row) => Object.values(row.values).some((value) => value !== ''));
}

export function csvCell(row: CsvRow, aliases: readonly string[]) {
  for (const alias of aliases) {
    const value = row.values[alias];
    if (value !== undefined && value !== '') {
      return value;
    }
  }
  return '';
}

export function parseEnabled(value: string) {
  const normalized = value.trim().toLowerCase();
  if (!normalized) return true;
  return ['true', '1', 'yes', 'y', '启用', '是'].includes(normalized);
}

function escapeCsvCell(value: CsvExportValue) {
  const text = value === null || value === undefined ? '' : String(value);
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

export function downloadCsv(filename: string, headers: readonly string[], rows: readonly Record<string, CsvExportValue>[]) {
  const content = [
    headers.map(escapeCsvCell).join(','),
    ...rows.map((row) => headers.map((header) => escapeCsvCell(row[header])).join(',')),
  ].join('\n');
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
