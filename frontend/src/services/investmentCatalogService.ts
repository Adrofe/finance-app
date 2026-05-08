import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type { InvestmentInstrument, InvestmentPlatform, InvestmentType, PriceRefreshResult } from '../types/investments';

const BASE = '/v1/api/investments/catalog';
const INVESTMENTS_BASE = '/v1/api/investments';

const headers = (token: string) => ({ Authorization: `Bearer ${token}` });

// ─── Types ────────────────────────────────────────────────────────────────────

export async function fetchInvestmentTypes(token: string): Promise<InvestmentType[]> {
  const res = await axios.get<ApiResponse<InvestmentType[]>>(`${BASE}/types`, { headers: headers(token) });
  return res.data.data ?? [];
}

// ─── Instruments ──────────────────────────────────────────────────────────────

export async function fetchInstruments(token: string): Promise<InvestmentInstrument[]> {
  const res = await axios.get<ApiResponse<InvestmentInstrument[]>>(`${BASE}/instruments`, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function createInstrument(token: string, payload: Omit<InvestmentInstrument, 'id'>): Promise<InvestmentInstrument> {
  const res = await axios.post<ApiResponse<InvestmentInstrument>>(`${BASE}/instruments`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function updateInstrument(token: string, id: number, payload: Omit<InvestmentInstrument, 'id'>): Promise<InvestmentInstrument> {
  const res = await axios.put<ApiResponse<InvestmentInstrument>>(`${BASE}/instruments/${id}`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function deleteInstrument(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/instruments/${id}`, { headers: headers(token) });
}

// ─── Platforms ────────────────────────────────────────────────────────────────

export async function fetchPlatforms(token: string): Promise<InvestmentPlatform[]> {
  const res = await axios.get<ApiResponse<InvestmentPlatform[]>>(`${BASE}/platforms`, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function createPlatform(token: string, payload: Omit<InvestmentPlatform, 'id'>): Promise<InvestmentPlatform> {
  const res = await axios.post<ApiResponse<InvestmentPlatform>>(`${BASE}/platforms`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function updatePlatform(token: string, id: number, payload: Omit<InvestmentPlatform, 'id'>): Promise<InvestmentPlatform> {
  const res = await axios.put<ApiResponse<InvestmentPlatform>>(`${BASE}/platforms/${id}`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function deletePlatform(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/platforms/${id}`, { headers: headers(token) });
}

export async function refreshInstrumentPrices(token: string): Promise<PriceRefreshResult> {
  const res = await axios.post<ApiResponse<PriceRefreshResult>>(`${INVESTMENTS_BASE}/prices/refresh/auto`, undefined, { headers: headers(token) });
  return res.data.data;
}
