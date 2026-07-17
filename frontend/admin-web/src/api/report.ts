import { request } from './client';
import type { ReportOverview } from './types';

interface ReportOverviewQuery {
  from?: string;
  to?: string;
  trendDays?: number;
}

function buildQuery(params: ReportOverviewQuery) {
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
