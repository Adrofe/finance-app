import axios from 'axios';
import { AccountType } from '../types/accountType';

const API_BASE = '/v1/api/account-types';

export async function fetchAccountTypes(token?: string): Promise<AccountType[]> {
  const response = await axios.get<{ data: AccountType[] }>(API_BASE, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  return response.data.data;
}
