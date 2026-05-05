import { useEffect, useState, useCallback } from 'react';
import axios from 'axios';
import type { InvestmentInstrument, InvestmentPlatform, InvestmentType } from '../types/investments';
import {
  fetchInvestmentTypes,
  fetchInstruments, createInstrument, updateInstrument, deleteInstrument,
  fetchPlatforms, createPlatform, updatePlatform, deletePlatform,
} from '../services/investmentCatalogService';

export function useInvestmentCatalog(token: string, onUnauthorized?: (message: string) => void) {
  const [types, setTypes]               = useState<InvestmentType[]>([]);
  const [instruments, setInstruments]   = useState<InvestmentInstrument[]>([]);
  const [platforms, setPlatforms]       = useState<InvestmentPlatform[]>([]);
  const [loading, setLoading]           = useState(true);
  const [error, setError]               = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) { setLoading(false); return; }
    setLoading(true);
    Promise.all([fetchInvestmentTypes(token), fetchInstruments(token), fetchPlatforms(token)])
      .then(([t, i, p]) => { setTypes(t); setInstruments(i); setPlatforms(p); })
      .catch((err) => {
        if (axios.isAxiosError(err) && err.response?.status === 401) {
          onUnauthorized?.('Session expired or invalid token. Please login again.');
          return;
        }
        setError(err?.response?.data?.message || err.message || 'Error loading catalog');
      })
      .finally(() => setLoading(false));
  }, [token, onUnauthorized]);

  useEffect(() => { load(); }, [load]);

  const clearError = useCallback(() => setError(null), []);

  // ── Instruments ──────────────────────────────────────────────────────────────

  const addInstrument = useCallback(async (payload: Omit<InvestmentInstrument, 'id'>) => {
    const created = await createInstrument(token, payload);
    setInstruments(prev => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
    return created;
  }, [token]);

  const editInstrument = useCallback(async (id: number, payload: Omit<InvestmentInstrument, 'id'>) => {
    const updated = await updateInstrument(token, id, payload);
    setInstruments(prev => prev.map(i => i.id === id ? updated : i));
    return updated;
  }, [token]);

  const removeInstrument = useCallback(async (id: number) => {
    await deleteInstrument(token, id);
    setInstruments(prev => prev.filter(i => i.id !== id));
  }, [token]);

  // ── Platforms ────────────────────────────────────────────────────────────────

  const addPlatform = useCallback(async (payload: Omit<InvestmentPlatform, 'id'>) => {
    const created = await createPlatform(token, payload);
    setPlatforms(prev => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
    return created;
  }, [token]);

  const editPlatform = useCallback(async (id: number, payload: Omit<InvestmentPlatform, 'id'>) => {
    const updated = await updatePlatform(token, id, payload);
    setPlatforms(prev => prev.map(p => p.id === id ? updated : p));
    return updated;
  }, [token]);

  const removePlatform = useCallback(async (id: number) => {
    await deletePlatform(token, id);
    setPlatforms(prev => prev.filter(p => p.id !== id));
  }, [token]);

  return {
    types, instruments, platforms,
    loading, error, clearError,
    addInstrument, editInstrument, removeInstrument,
    addPlatform, editPlatform, removePlatform,
  };
}
