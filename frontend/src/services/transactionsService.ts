import axios from 'axios';

import type { ApiResponse } from '../types/api';
import type { Transaction } from '../types/banking';

export async function fetchTransactions(accessToken: string): Promise<Transaction[]> {
  const response = await axios.get<ApiResponse<Transaction[]>>('/v1/api/transactions', {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  return response.data.data || [];
}
