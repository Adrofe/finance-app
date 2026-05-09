import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type {
  BudgetPlanDTO,
  BudgetPlanRequest,
  BudgetSnapshotDTO,
} from '../types/budget';

const BASE = '/v1/api/budget';
const hdrs = (token: string) => ({ Authorization: `Bearer ${token}` });

export async function fetchBudgetPlans(token: string): Promise<BudgetPlanDTO[]> {
  const res = await axios.get<ApiResponse<BudgetPlanDTO[]>>(`${BASE}/plans`, {
    headers: hdrs(token),
  });
  return res.data.data ?? [];
}

export async function fetchBudgetPlan(token: string, planId: number): Promise<BudgetPlanDTO> {
  const res = await axios.get<ApiResponse<BudgetPlanDTO>>(`${BASE}/plans/${planId}`, {
    headers: hdrs(token),
  });
  return res.data.data;
}

export async function createBudgetPlan(token: string, payload: BudgetPlanRequest): Promise<BudgetPlanDTO> {
  const res = await axios.post<ApiResponse<BudgetPlanDTO>>(`${BASE}/plans`, payload, {
    headers: hdrs(token),
  });
  return res.data.data;
}

export async function deleteBudgetPlan(token: string, planId: number): Promise<void> {
  await axios.delete(`${BASE}/plans/${planId}`, { headers: hdrs(token) });
}

export async function refreshBudgetSnapshot(
  token: string,
  planId: number,
  startDate: string,
  endDate: string,
): Promise<BudgetSnapshotDTO> {
  const res = await axios.post<ApiResponse<BudgetSnapshotDTO>>(
    `${BASE}/plans/${planId}/snapshots/refresh`,
    null,
    { headers: hdrs(token), params: { startDate, endDate } },
  );
  return res.data.data;
}

export async function fetchLatestSnapshot(token: string, planId: number): Promise<BudgetSnapshotDTO | null> {
  try {
    const res = await axios.get<ApiResponse<BudgetSnapshotDTO>>(
      `${BASE}/plans/${planId}/snapshots/latest`,
      { headers: hdrs(token) },
    );
    return res.data.data ?? null;
  } catch {
    return null;
  }
}
