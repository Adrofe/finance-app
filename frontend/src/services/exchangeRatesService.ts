import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type { ExchangeRateEntry, ExchangeRateUpsertDraft } from '../types/investments';

const BASE = '/v1/api/investments/forex';

const headers = (token: string) => ({ Authorization: `Bearer ${token}` });

export async function fetchExchangeRates(
  token: string,
  params: { fromCurrency?: string; toCurrency?: string; from?: string; to?: string },
): Promise<ExchangeRateEntry[]> {
  const res = await axios.get<ApiResponse<ExchangeRateEntry[]>>(`${BASE}/rates`, {
    headers: headers(token),
    params,
  });
  return res.data.data ?? [];
}

export async function upsertExchangeRate(token: string, payload: ExchangeRateUpsertDraft): Promise<ExchangeRateEntry> {
  const res = await axios.post<ApiResponse<ExchangeRateEntry>>(`${BASE}/rates`, payload, {
    headers: headers(token),
  });
  return res.data.data;
}

export async function refreshExchangeRatesForDay(token: string, asOf: string): Promise<number> {
  const res = await axios.post<ApiResponse<number>>(`${BASE}/refresh-day`, undefined, {
    headers: headers(token),
    params: { asOf },
  });
  return res.data.data ?? 0;
}

export async function deleteExchangeRate(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/rates/${id}`, { headers: headers(token) });
}
