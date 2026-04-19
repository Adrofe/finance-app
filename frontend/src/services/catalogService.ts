import axios from 'axios';
import type { ApiResponse } from '../types/api';

export type TransactionStatus = { id: number; code: string; description: string };
export type TransactionType = { id: number; name: string; description: string };
export type TransactionCategory = { id: number; name: string; description: string };
export type Account = { id: number; name: string };

export class CatalogService {
  static async fetchStatuses(token: string): Promise<TransactionStatus[]> {
    const res = await axios.get<ApiResponse<TransactionStatus[]>>('/v1/api/transaction-statuses', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }

  static async fetchTypes(token: string): Promise<TransactionType[]> {
    const res = await axios.get<ApiResponse<TransactionType[]>>('/v1/api/transaction-types', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }

  static async fetchCategories(token: string): Promise<TransactionCategory[]> {
    const res = await axios.get<ApiResponse<TransactionCategory[]>>('/v1/api/categories', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }

  static async fetchAccounts(token: string): Promise<Account[]> {
    const res = await axios.get<ApiResponse<Account[]>>('/v1/api/accounts', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }
}
