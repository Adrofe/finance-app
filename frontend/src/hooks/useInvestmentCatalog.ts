import { useEffect, useState, useCallback } from 'react';
import axios from 'axios';
import type { CatalogOption, InvestmentInstrument, InvestmentInstrumentExposure, InvestmentPlatform, InvestmentType, PriceRefreshResult, PriceUpdateDraft } from '../types/investments';
import {
  fetchInvestmentTypes,
  fetchCountryCatalog,
  fetchRegionCatalog,
  fetchSectorCatalog,
  fetchIndustryCatalog,
  createCountryCatalogOption,
  updateCountryCatalogOption,
  deleteCountryCatalogOption,
  createRegionCatalogOption,
  updateRegionCatalogOption,
  deleteRegionCatalogOption,
  createSectorCatalogOption,
  updateSectorCatalogOption,
  deleteSectorCatalogOption,
  createIndustryCatalogOption,
  updateIndustryCatalogOption,
  deleteIndustryCatalogOption,
  fetchInstruments, createInstrument, updateInstrument, deleteInstrument,
  fetchPlatforms, createPlatform, updatePlatform, deletePlatform,
  refreshInstrumentPrices,
  addManualInstrumentPrice,
} from '../services/investmentCatalogService';

export function useInvestmentCatalog(token: string, onUnauthorized?: (message: string) => void) {
  const [types, setTypes]               = useState<InvestmentType[]>([]);
  const [countries, setCountries]       = useState<CatalogOption[]>([]);
  const [regions, setRegions]           = useState<CatalogOption[]>([]);
  const [sectors, setSectors]           = useState<CatalogOption[]>([]);
  const [industries, setIndustries]     = useState<CatalogOption[]>([]);
  const [instruments, setInstruments]   = useState<InvestmentInstrument[]>([]);
  const [platforms, setPlatforms]       = useState<InvestmentPlatform[]>([]);
  const [loading, setLoading]           = useState(true);
  const [error, setError]               = useState<string | null>(null);

  const load = useCallback(() => {
    if (!token) { setLoading(false); return; }
    setLoading(true);
    Promise.all([
      fetchInvestmentTypes(token),
      fetchCountryCatalog(token),
      fetchRegionCatalog(token),
      fetchSectorCatalog(token),
      fetchIndustryCatalog(token),
      fetchInstruments(token),
      fetchPlatforms(token),
    ])
      .then(([t, c, r, s, d, i, p]) => {
        setTypes(t);
        setCountries(c);
        setRegions(r);
        setSectors(s);
        setIndustries(d);
        setInstruments(i);
        setPlatforms(p);
      })
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

  const reloadClassificationCatalogs = useCallback(async () => {
    const [c, r, s, d] = await Promise.all([
      fetchCountryCatalog(token),
      fetchRegionCatalog(token),
      fetchSectorCatalog(token),
      fetchIndustryCatalog(token),
    ]);
    setCountries(c);
    setRegions(r);
    setSectors(s);
    setIndustries(d);
  }, [token]);

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

  const refreshPrices = useCallback(async (): Promise<PriceRefreshResult> => {
    try {
      setError(null);
      const result = await refreshInstrumentPrices(token);
      const updatedInstruments = await fetchInstruments(token);
      setInstruments(updatedInstruments);
      return result;
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
      }
      throw err;
    }
  }, [token, onUnauthorized]);

  const addManualPrice = useCallback(async (payload: PriceUpdateDraft): Promise<PriceRefreshResult> => {
    try {
      setError(null);
      const result = await addManualInstrumentPrice(token, payload);
      const updatedInstruments = await fetchInstruments(token);
      setInstruments(updatedInstruments);
      return result;
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
      }
      throw err;
    }
  }, [token, onUnauthorized]);

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

  // ── Classification catalogs ───────────────────────────────────────────────

  const addCountry = useCallback(async (payload: Omit<CatalogOption, 'id'>) => {
    const created = await createCountryCatalogOption(token, payload);
    await reloadClassificationCatalogs();
    return created;
  }, [token, reloadClassificationCatalogs]);

  const editCountry = useCallback(async (id: number, payload: Omit<CatalogOption, 'id'>) => {
    const updated = await updateCountryCatalogOption(token, id, payload);
    await reloadClassificationCatalogs();
    return updated;
  }, [token, reloadClassificationCatalogs]);

  const removeCountry = useCallback(async (id: number) => {
    await deleteCountryCatalogOption(token, id);
    await reloadClassificationCatalogs();
  }, [token, reloadClassificationCatalogs]);

  const addRegion = useCallback(async (payload: Omit<CatalogOption, 'id'>) => {
    const created = await createRegionCatalogOption(token, payload);
    await reloadClassificationCatalogs();
    return created;
  }, [token, reloadClassificationCatalogs]);

  const editRegion = useCallback(async (id: number, payload: Omit<CatalogOption, 'id'>) => {
    const updated = await updateRegionCatalogOption(token, id, payload);
    await reloadClassificationCatalogs();
    return updated;
  }, [token, reloadClassificationCatalogs]);

  const removeRegion = useCallback(async (id: number) => {
    await deleteRegionCatalogOption(token, id);
    await reloadClassificationCatalogs();
  }, [token, reloadClassificationCatalogs]);

  const addSector = useCallback(async (payload: Omit<CatalogOption, 'id'>) => {
    const created = await createSectorCatalogOption(token, payload);
    await reloadClassificationCatalogs();
    return created;
  }, [token, reloadClassificationCatalogs]);

  const editSector = useCallback(async (id: number, payload: Omit<CatalogOption, 'id'>) => {
    const updated = await updateSectorCatalogOption(token, id, payload);
    await reloadClassificationCatalogs();
    return updated;
  }, [token, reloadClassificationCatalogs]);

  const removeSector = useCallback(async (id: number) => {
    await deleteSectorCatalogOption(token, id);
    await reloadClassificationCatalogs();
  }, [token, reloadClassificationCatalogs]);

  const addIndustry = useCallback(async (payload: Omit<CatalogOption, 'id'>) => {
    const created = await createIndustryCatalogOption(token, payload);
    await reloadClassificationCatalogs();
    return created;
  }, [token, reloadClassificationCatalogs]);

  const editIndustry = useCallback(async (id: number, payload: Omit<CatalogOption, 'id'>) => {
    const updated = await updateIndustryCatalogOption(token, id, payload);
    await reloadClassificationCatalogs();
    return updated;
  }, [token, reloadClassificationCatalogs]);

  const removeIndustry = useCallback(async (id: number) => {
    await deleteIndustryCatalogOption(token, id);
    await reloadClassificationCatalogs();
  }, [token, reloadClassificationCatalogs]);

  return {
    types, countries, regions, sectors, industries, instruments, platforms,
    loading, error, clearError,
    addInstrument, editInstrument, removeInstrument,
    refreshPrices,
    addManualPrice,
    addPlatform, editPlatform, removePlatform,
    addCountry, editCountry, removeCountry,
    addRegion, editRegion, removeRegion,
    addSector, editSector, removeSector,
    addIndustry, editIndustry, removeIndustry,
  };
}
