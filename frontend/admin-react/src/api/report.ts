import { request } from './client';

type QueryValue = string | number | boolean | null | undefined;

export interface ReportTimeRangeQuery {
  [key: string]: QueryValue;
  from?: string;
  to?: string;
}

export interface InstitutionPrescriptionCountRecord {
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  orderCount: number;
  prescriptionCount: number;
  doseCount: number;
  totalAmount: number | string | null;
}

export async function listInstitutionPrescriptionCounts(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const payload = await request<unknown>(
    `/report-api/api/admin/reports/institution-prescription-counts${query ? `?${query}` : ''}`,
  );
  return Array.isArray(payload) ? payload.filter(isInstitutionPrescriptionCountRecord) : [];
}

function buildQuery(params: Record<string, QueryValue>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null) return;
    const normalizedValue = typeof value === 'string' ? value.trim() : String(value);
    if (normalizedValue === '') return;
    query.set(key, normalizedValue);
  });
  return query.toString();
}

function isInstitutionPrescriptionCountRecord(value: unknown): value is InstitutionPrescriptionCountRecord {
  if (!isRecordObject(value)) return false;
  return (
    typeof value.institutionId === 'string' &&
    typeof value.institutionCode === 'string' &&
    typeof value.institutionName === 'string' &&
    typeof value.orderCount === 'number' &&
    typeof value.prescriptionCount === 'number' &&
    typeof value.doseCount === 'number'
  );
}

function isRecordObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
