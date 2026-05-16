import axios from 'axios';
import type { ApiResponse } from '../types/api';
import type { TaxType, TransactionTax, TransactionTaxRequest } from '../types/banking';

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

  static async updateTag(token: string, id: number, name: string): Promise<Tag> {
    const res = await axios.put<ApiResponse<Tag>>(`/v1/api/tags/${id}`, { name }, {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data;
  }

  static async deleteTag(token: string, id: number): Promise<void> {
    await axios.delete(`/v1/api/tags/${id}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
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

  // ── Tax types & withholding ───────────────────────────────────────────────

  static async fetchTaxTypes(token: string): Promise<TaxType[]> {
    const res = await axios.get<ApiResponse<TaxType[]>>('/v1/api/tax-types', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }

  static async getTransactionTax(token: string, transactionId: number): Promise<TransactionTax | null> {
    try {
      const res = await axios.get<ApiResponse<TransactionTax>>(
        `/v1/api/transactions/${transactionId}/tax`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      return res.data.data;
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 404) return null;
      throw err;
    }
  }

  static async saveTransactionTax(token: string, transactionId: number, request: TransactionTaxRequest): Promise<TransactionTax> {
    const res = await axios.put<ApiResponse<TransactionTax>>(
      `/v1/api/transactions/${transactionId}/tax`,
      request,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    return res.data.data;
  }

  static async deleteTransactionTax(token: string, transactionId: number): Promise<void> {
    await axios.delete(`/v1/api/transactions/${transactionId}/tax`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  }

  static async fetchTaxReport(token: string): Promise<TransactionTax[]> {
    const res = await axios.get<ApiResponse<TransactionTax[]>>('/v1/api/tax-report', {
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.data.data || [];
  }
}
