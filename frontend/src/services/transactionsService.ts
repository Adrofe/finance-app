import axios from 'axios';

import type { ApiResponse } from '../types/api';
import type { Transaction, CreateTransactionRequest } from '../types/banking';

export async function fetchTransactions(accessToken: string): Promise<Transaction[]> {
  const response = await axios.get<ApiResponse<Transaction[]>>('/v1/api/transactions', {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  return response.data.data || [];
}

export async function createTransaction(accessToken: string, transaction: CreateTransactionRequest): Promise<Transaction> {
  const response = await axios.post<ApiResponse<Transaction>>('/v1/api/transactions', transaction, {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  return response.data.data;
}

export async function deleteTransaction(accessToken: string, id: number): Promise<void> {
  await axios.delete(`/v1/api/transactions/${id}`, {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });
}
