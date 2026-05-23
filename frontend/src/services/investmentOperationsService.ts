import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type {
  InvestmentOperation,
  InvestmentOperationDraft,
  InvestmentPosition,
  InvestmentSummary,
  InvestmentTaxSummary,
} from '../types/investments';

const OPERATIONS_BASE = '/v1/api/investments/operations';
const INVESTMENTS_BASE = '/v1/api/investments';

const headers = (token: string) => ({ Authorization: `Bearer ${token}` });

export async function fetchInvestmentPositions(token: string): Promise<InvestmentPosition[]> {
  const res = await axios.get<ApiResponse<InvestmentPosition[]>>(INVESTMENTS_BASE, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function fetchInvestmentSummary(token: string): Promise<InvestmentSummary> {
  const res = await axios.get<ApiResponse<InvestmentSummary>>(`${INVESTMENTS_BASE}/summary`, { headers: headers(token) });
  return res.data.data;
}

export async function fetchOperations(token: string): Promise<InvestmentOperation[]> {
  const res = await axios.get<ApiResponse<InvestmentOperation[]>>(OPERATIONS_BASE, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function fetchTaxSummary(token: string, year: number): Promise<InvestmentTaxSummary> {
  const res = await axios.get<ApiResponse<InvestmentTaxSummary>>(`${OPERATIONS_BASE}/tax-summary`, {
    headers: headers(token),
    params: { year },
  });
  return res.data.data;
}

export async function createOperation(token: string, payload: InvestmentOperationDraft): Promise<InvestmentOperation> {
  const res = await axios.post<ApiResponse<InvestmentOperation>>(OPERATIONS_BASE, payload, { headers: headers(token) });
  return res.data.data;
}

export async function updateOperation(token: string, id: number, payload: InvestmentOperationDraft): Promise<InvestmentOperation> {
  const res = await axios.put<ApiResponse<InvestmentOperation>>(`${OPERATIONS_BASE}/${id}`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function deleteOperation(token: string, id: number): Promise<void> {
  await axios.delete(`${OPERATIONS_BASE}/${id}`, { headers: headers(token) });
}

export async function recalculateAllPositions(token: string): Promise<number> {
  const res = await axios.post<ApiResponse<number>>(`${OPERATIONS_BASE}/recalculate-all`, null, { headers: headers(token) });
  return res.data.data ?? 0;
}