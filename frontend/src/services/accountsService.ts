import axios from 'axios';
import { Account } from '../types/account';

const API_BASE = '/v1/api/accounts';

export async function fetchAccounts(token: string): Promise<Account[]> {
  const response = await axios.get<{ data: Account[] }>(API_BASE, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  return response.data.data;
}

export async function createAccount(token: string, account: Partial<Account>): Promise<Account> {
  try {
    const response = await axios.post<{ data: Account }>(API_BASE, account, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    return response.data.data;
  } catch (err: any) {
    // Normalize axios error to a clear message coming from backend if available
    const resp = err?.response;
    const backendMessage = resp?.data?.message || resp?.data?.error || resp?.data?.details;
    const status = resp?.status;
    const msg = backendMessage || err.message || `Request failed with status ${status}`;
    const e = new Error(msg);
    // attach status for callers if needed
    (e as any).status = status;
    throw e;
  }
}

export async function updateAccount(token: string, id: number, account: Partial<Account>): Promise<Account> {
  try {
    const response = await axios.put<{ data: Account }>(`${API_BASE}/${id}`, account, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    return response.data.data;
  } catch (err: any) {
    const resp = err?.response;
    const backendMessage = resp?.data?.message || resp?.data?.error || resp?.data?.details;
    const status = resp?.status;
    const msg = backendMessage || err.message || `Request failed with status ${status}`;
    const e = new Error(msg);
    (e as any).status = status;
    throw e;
  }
}
