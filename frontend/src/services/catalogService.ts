import axios from 'axios';
import type { ApiResponse } from '../types/api';

export type TransactionStatus = { id: number; code: string; description: string };
export type TransactionType = { id: number; name: string; description: string };
export type TransactionCategory = { 
  id: number; 
  name: string; 
  code?: string;
  description?: string; 
  parentId?: number; 
  parentName?: string; 
};
export type Account = { 
  id: number; 
  name: string; 
  currency?: string; 
  institutionName?: string; 
  institutionId?: number;
};
export type Tag = { id: number; name: string; description?: string };
export type Merchant = { id: number; name: string; description?: string };

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

  static async fetchTags(token: string): Promise<Tag[]> {
    const res = await axios.get<ApiResponse<Tag[]>>('/v1/api/tags', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }

  static async createTag(token: string, name: string): Promise<Tag> {
    const res = await axios.post<ApiResponse<Tag>>('/v1/api/tags', { name }, {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data;
  }

  static async fetchMerchants(token: string): Promise<Merchant[]> {
    const res = await axios.get<ApiResponse<Merchant[]>>('/v1/api/merchants', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }

  static async createMerchant(token: string, name: string): Promise<Merchant> {
    const res = await axios.post<ApiResponse<Merchant>>('/v1/api/merchants', { name }, {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data;
  }
}
