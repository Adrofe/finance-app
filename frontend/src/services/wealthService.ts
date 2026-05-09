import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type { WealthSnapshotCreateRequest, WealthSnapshotDTO } from '../types/wealth';

const BASE = '/v1/api/wealth/snapshots';
const hdrs = (token: string) => ({ Authorization: `Bearer ${token}` });

export async function fetchWealthSnapshots(token: string, from?: string, to?: string): Promise<WealthSnapshotDTO[]> {
  const res = await axios.get<ApiResponse<WealthSnapshotDTO[]>>(BASE, {
    headers: hdrs(token),
    params: { includeItems: true, ...(from && { from }), ...(to && { to }) },
  });
  return res.data.data ?? [];
}

export async function refreshWealthSnapshot(token: string): Promise<WealthSnapshotDTO> {
  const res = await axios.post<ApiResponse<WealthSnapshotDTO>>(`${BASE}/refresh`, null, {
    headers: hdrs(token),
  });
  return res.data.data;
}

export async function upsertWealthSnapshot(token: string, payload: WealthSnapshotCreateRequest): Promise<WealthSnapshotDTO> {
  const res = await axios.post<ApiResponse<WealthSnapshotDTO>>(BASE, payload, {
    headers: hdrs(token),
  });
  return res.data.data;
}

export async function deleteWealthSnapshot(token: string, id: number): Promise<void> {
  await axios.delete(`${BASE}/${id}`, { headers: hdrs(token) });
}
