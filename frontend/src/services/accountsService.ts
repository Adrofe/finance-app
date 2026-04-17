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
