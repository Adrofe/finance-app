import axios from 'axios';
import { Institution } from '../types/institution';

const API_BASE = '/v1/api/institutions';

export async function fetchInstitutions(token?: string): Promise<Institution[]> {
  const response = await axios.get<{ data: Institution[] }>(API_BASE, {
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  return response.data.data;
}
