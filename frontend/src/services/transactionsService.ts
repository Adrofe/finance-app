import axios from 'axios';

import type { ApiResponse } from '../types/api';
import type { Transaction, CreateTransactionRequest } from '../types/banking';

export type TransactionsSearchRequest = {
  accountId?: number;
  categoryId?: number;
  tagIds?: number[];
  merchantId?: number;
  statusId?: number;
  typeId?: number;
  currency?: string;
  startDate?: string;
  endDate?: string;
  minAmount?: number;
  maxAmount?: number;
  description?: string;
  page?: number;
  size?: number;
  sortBy?: 'bookingDate' | 'valueDate' | 'amount' | 'createdAt' | 'id';
  sortDirection?: 'ASC' | 'DESC';
};

export type PagedResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export async function fetchTransactions(accessToken: string): Promise<Transaction[]> {
  const response = await axios.get<ApiResponse<Transaction[]>>('/v1/api/transactions', {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  return response.data.data || [];
}

export async function searchTransactions(
  accessToken: string,
  filter: TransactionsSearchRequest
): Promise<PagedResponse<Transaction>> {
  const response = await axios.post<ApiResponse<PagedResponse<Transaction>>>(
    '/v1/api/transactions/search',
    filter,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`
      }
    }
  );

  return response.data.data;
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

export async function updateTransaction(
  accessToken: string,
  id: number,
  payload: Partial<CreateTransactionRequest>
): Promise<Transaction> {
  const response = await axios.put<ApiResponse<Transaction>>(
    `/v1/api/transactions/${id}`,
    payload,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  return response.data.data;
}
