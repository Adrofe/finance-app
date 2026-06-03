import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type {
  CatalogOption,
  InvestmentInstrument,
  InvestmentPlatform,
  InvestmentType,
  PriceRefreshResult,
  PriceUpdateDraft,
} from '../types/investments';

const BASE = '/v1/api/investments/catalog';
const INVESTMENTS_BASE = '/v1/api/investments';

const headers = (token: string) => ({ Authorization: `Bearer ${token}` });

// ─── Types ────────────────────────────────────────────────────────────────────

export async function fetchInvestmentTypes(token: string): Promise<InvestmentType[]> {
  const res = await axios.get<ApiResponse<InvestmentType[]>>(`${BASE}/types`, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function fetchCountryCatalog(token: string): Promise<CatalogOption[]> {
  const res = await axios.get<ApiResponse<CatalogOption[]>>(`${BASE}/countries`, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function fetchRegionCatalog(token: string): Promise<CatalogOption[]> {
  const res = await axios.get<ApiResponse<CatalogOption[]>>(`${BASE}/regions`, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function fetchSectorCatalog(token: string): Promise<CatalogOption[]> {
  const res = await axios.get<ApiResponse<CatalogOption[]>>(`${BASE}/sectors`, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function fetchIndustryCatalog(token: string): Promise<CatalogOption[]> {
  const res = await axios.get<ApiResponse<CatalogOption[]>>(`${BASE}/industries`, { headers: headers(token) });
  return res.data.data ?? [];
}

export async function createCountryCatalogOption(token: string, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.post<ApiResponse<CatalogOption>>(`${BASE}/countries`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function updateCountryCatalogOption(token: string, id: number, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.put<ApiResponse<CatalogOption>>(`${BASE}/countries/${id}`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function deleteCountryCatalogOption(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/countries/${id}`, { headers: headers(token) });
}

export async function createRegionCatalogOption(token: string, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.post<ApiResponse<CatalogOption>>(`${BASE}/regions`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function updateRegionCatalogOption(token: string, id: number, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.put<ApiResponse<CatalogOption>>(`${BASE}/regions/${id}`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function deleteRegionCatalogOption(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/regions/${id}`, { headers: headers(token) });
}

export async function createSectorCatalogOption(token: string, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.post<ApiResponse<CatalogOption>>(`${BASE}/sectors`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function updateSectorCatalogOption(token: string, id: number, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.put<ApiResponse<CatalogOption>>(`${BASE}/sectors/${id}`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function deleteSectorCatalogOption(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/sectors/${id}`, { headers: headers(token) });
}

export async function createIndustryCatalogOption(token: string, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.post<ApiResponse<CatalogOption>>(`${BASE}/industries`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function updateIndustryCatalogOption(token: string, id: number, payload: Omit<CatalogOption, 'id'>): Promise<CatalogOption> {
  const res = await axios.put<ApiResponse<CatalogOption>>(`${BASE}/industries/${id}`, payload, { headers: headers(token) });
  return res.data.data;
}

export async function deleteIndustryCatalogOption(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/industries/${id}`, { headers: headers(token) });
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

export async function addManualInstrumentPrice(token: string, payload: PriceUpdateDraft): Promise<PriceRefreshResult> {
  const res = await axios.post<ApiResponse<PriceRefreshResult>>(
    `${INVESTMENTS_BASE}/prices/refresh`,
    [payload],
    { headers: headers(token) },
  );
  return res.data.data;
}
