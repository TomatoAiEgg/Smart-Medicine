import { request } from './client';
import type {
  AuditPerformanceRecord,
  DecoctionPerformanceRecord,
  DispensePerformanceRecord,
  HerbDosageRecord,
  InstitutionHerbReconciliationRecord,
  InstitutionPrescriptionCountRecord,
  PrescriptionHerbDetailRecord,
  RecheckPerformanceRecord,
  ReportOverview,
} from './types';

interface ReportTimeRangeQuery {
  from?: string;
  to?: string;
}

interface ReportOverviewQuery extends ReportTimeRangeQuery {
  trendDays?: number;
}

function buildQuery(params: ReportOverviewQuery | ReportTimeRangeQuery) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.set(key, String(value).trim());
    }
  });
  return query.toString();
}

export function getReportOverview(params: ReportOverviewQuery = {}) {
  const query = buildQuery(params);
  return request<ReportOverview>(`/report-api/api/admin/reports/overview${query ? `?${query}` : ''}`);
}

export async function downloadReportOverviewCsv(params: ReportOverviewQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/overview.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listInstitutionPrescriptionCounts(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<InstitutionPrescriptionCountRecord[]>(
    `/report-api/api/admin/reports/institution-prescription-counts${query ? `?${query}` : ''}`,
  );
}

export async function downloadInstitutionPrescriptionCountsCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/institution-prescription-counts.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listDispensePerformance(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<DispensePerformanceRecord[]>(
    `/report-api/api/admin/reports/dispense-performance${query ? `?${query}` : ''}`,
  );
}

export async function downloadDispensePerformanceCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/dispense-performance.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listRecheckPerformance(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<RecheckPerformanceRecord[]>(
    `/report-api/api/admin/reports/recheck-performance${query ? `?${query}` : ''}`,
  );
}

export async function downloadRecheckPerformanceCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/recheck-performance.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listAuditPerformance(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<AuditPerformanceRecord[]>(
    `/report-api/api/admin/reports/audit-performance${query ? `?${query}` : ''}`,
  );
}

export async function downloadAuditPerformanceCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/audit-performance.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listDecoctionPerformance(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<DecoctionPerformanceRecord[]>(
    `/report-api/api/admin/reports/decoction-performance${query ? `?${query}` : ''}`,
  );
}

export async function downloadDecoctionPerformanceCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/decoction-performance.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listHerbDosage(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<HerbDosageRecord[]>(`/report-api/api/admin/reports/herb-dosage${query ? `?${query}` : ''}`);
}

export async function downloadHerbDosageCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/herb-dosage.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listInstitutionHerbReconciliation(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<InstitutionHerbReconciliationRecord[]>(
    `/report-api/api/admin/reports/institution-herb-reconciliation${query ? `?${query}` : ''}`,
  );
}

export async function downloadInstitutionHerbReconciliationCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/institution-herb-reconciliation.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}

export function listPrescriptionHerbDetails(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  return request<PrescriptionHerbDetailRecord[]>(
    `/report-api/api/admin/reports/prescription-herb-details${query ? `?${query}` : ''}`,
  );
}

export async function downloadPrescriptionHerbDetailsCsv(params: ReportTimeRangeQuery = {}) {
  const query = buildQuery(params);
  const response = await fetch(`/report-api/api/admin/reports/prescription-herb-details.csv${query ? `?${query}` : ''}`);
  if (!response.ok) {
    throw new Error(`导出失败：HTTP ${response.status}`);
  }
  return response.blob();
}
