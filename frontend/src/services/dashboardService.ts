import axios from 'axios';

import type { ApiResponse } from '../types/api';
import type { DashboardSummary, SpendingByCategory, TimeSeriesPoint } from '../types/dashboard';

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export async function fetchDashboardSummary(
  token: string,
  startDate?: string,
  endDate?: string,
): Promise<DashboardSummary> {
  const params: Record<string, string> = {};
  if (startDate) params.startDate = startDate;
  if (endDate)   params.endDate   = endDate;

  const res = await axios.get<ApiResponse<DashboardSummary>>(
    '/v1/api/dashboard/summary',
    { headers: authHeader(token), params },
  );
  return res.data.data;
}

export async function fetchSpendingByCategory(
  token: string,
  startDate?: string,
  endDate?: string,
): Promise<SpendingByCategory[]> {
  const params: Record<string, string> = {};
  if (startDate) params.startDate = startDate;
  if (endDate)   params.endDate   = endDate;

  const res = await axios.get<ApiResponse<SpendingByCategory[]>>(
    '/v1/api/dashboard/spending-by-category',
    { headers: authHeader(token), params },
  );
  return res.data.data ?? [];
}

export async function fetchTimeSeries(
  token: string,
  startDate?: string,
  endDate?: string,
  groupBy: 'MONTH' | 'DAY' = 'MONTH',
): Promise<TimeSeriesPoint[]> {
  const params: Record<string, string> = { groupBy };
  if (startDate) params.startDate = startDate;
  if (endDate)   params.endDate   = endDate;

  const res = await axios.get<ApiResponse<TimeSeriesPoint[]>>(
    '/v1/api/dashboard/time-series',
    { headers: authHeader(token), params },
  );
  return res.data.data ?? [];
}
