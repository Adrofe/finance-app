import axios from 'axios';
import { Merchant, Category } from '../types/banking';

const API_BASE = '/v1/api';

export const fetchMerchants = async (token: string): Promise<Merchant[]> => {
  const res = await axios.get<{ data: Merchant[] }>(`${API_BASE}/merchants`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.data.data || [];
};

export const updateMerchant = async (token: string, id: number, merchant: Partial<Merchant>): Promise<Merchant> => {
  const res = await axios.put<{ data: Merchant }>(`${API_BASE}/merchants/${id}`, merchant, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.data.data;
};

export const createMerchant = async (token: string, merchant: Partial<Merchant>): Promise<Merchant> => {
  const res = await axios.post<{ data: Merchant }>(`${API_BASE}/merchants`, merchant, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.data.data;
};

export const deleteMerchant = async (token: string, id: number): Promise<void> => {
  await axios.delete(`${API_BASE}/merchants/${id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
};

export const fetchCategories = async (token: string): Promise<Category[]> => {
  const res = await axios.get<{ data: Category[] }>(`${API_BASE}/categories`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.data.data || [];
};
