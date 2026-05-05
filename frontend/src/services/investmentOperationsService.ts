import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type { InvestmentOperation, InvestmentOperationDraft, InvestmentPosition } from '../types/investments';

const OPERATIONS_BASE = '/v1/api/investments/operations';
const INVESTMENTS_BASE = '/v1/api/investments';

const headers = (token: string) => ({ Authorization: `Bearer ${token}` });

export async function fetchInvestmentPositions(token: string): Promise<InvestmentPosition[]> {
  const res = await axios.get<ApiResponse<InvestmentPosition[]>>(INVESTMENTS_BASE, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function fetchOperations(token: string): Promise<InvestmentOperation[]> {
  const res = await axios.get<ApiResponse<InvestmentOperation[]>>(OPERATIONS_BASE, { headers: headers(token) });
  return res.data.data ?? [];
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