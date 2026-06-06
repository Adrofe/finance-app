import React, { useMemo, useState } from 'react';
import { useInvestmentCatalog } from '../hooks/useInvestmentCatalog';
import {
  getInvestmentTypeVisual,
  INVESTMENT_CURRENCY_OPTIONS,
  INVESTMENT_MARKET_OPTIONS,
  INVESTMENT_PRICE_SOURCE_OPTIONS,
} from '../constants/visualConfig';
import type { CatalogOption, InvestmentInstrument, InvestmentInstrumentExposure, InvestmentPlatform, InvestmentType } from '../types/investments';
import './investment-catalog.css';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const fmtPrice = (n?: number) =>
  n != null
    ? n.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 6 })
    : '—';

const fmtDate = (s?: string) =>
  s ? new Date(s).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

type Section = 'instruments' | 'platforms' | 'classifications';
type ClassificationKind = 'countries' | 'regions' | 'sectors' | 'industries' | 'marketRegimes';
type ExposureMode = 'UNIQUE' | 'COMPOUND';

// ─── Instrument form state ────────────────────────────────────────────────────

const EMPTY_INSTRUMENT = {
  typeId: '',
  code: '',
  symbol: '',
  name: '',
  market: '',
  currency: 'EUR',
  lastPrice: '',
  lastPriceSource: '',
  lastPriceAt: '',
  scraperUrl: '',
  finectUrl: '',
  countryId: '',
  regionId: '',
  sectorId: '',
  industryId: '',
};

// ─── Platform form state ──────────────────────────────────────────────────────

const EMPTY_PLATFORM = { code: '', name: '' };
const EMPTY_CLASSIFICATION = { code: '', name: '' };
const EMPTY_EXPOSURE = {
  dimension: 'SECTOR' as InvestmentInstrumentExposure['dimension'],
  countryId: '',
  regionId: '',
  sectorId: '',
  industryId: '',
  marketRegimeId: '',
  weightPct: '',
};

// ─── Props ────────────────────────────────────────────────────────────────────

interface Props {
  token: string;
  onUnauthorized?: (message: string) => void;
}

const getTypeLabel = (type: InvestmentType) => {
  const visual = getInvestmentTypeVisual(type.code, type.name);
  return `${visual.emoji} ${type.name} (${type.code})`;
};

// ─── Sort helpers ─────────────────────────────────────────────────────────────
type SortColKey = 'name' | 'symbol' | 'code' | 'market' | 'currency' | 'countryCode' | 'region' | 'sector' | 'industry';

const SortIcon = ({ col, active, dir }: { col: SortColKey; active: SortColKey; dir: 'asc' | 'desc' }) => (
  col !== active
    ? <span className="ict-sort-icon ict-sort-icon--idle">↕</span>
    : <span className="ict-sort-icon">{dir === 'asc' ? '↑' : '↓'}</span>
);

// ─── Main component ───────────────────────────────────────────────────────────

export const InvestmentCatalogTable: React.FC<Props> = ({ token, onUnauthorized }) => {
  const {
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
    exposuresByInstrument,
    loadInstrumentExposures,
    addInstrumentExposure,
    editInstrumentExposure,
    removeInstrumentExposure,
  } = useInvestmentCatalog(token, onUnauthorized);

  const [section, setSection] = useState<Section>('instruments');
  const [classificationKind, setClassificationKind] = useState<ClassificationKind>('countries');

  // ── shared modal state ────────────────────────────────────────────────────
  const [showForm, setShowForm]           = useState(false);
  const [showExposureModal, setShowExposureModal] = useState(false);
  const [editingId, setEditingId]         = useState<number | null>(null);
  const [submitting, setSubmitting]       = useState(false);
  const [formError, setFormError]         = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<{ id: number; name: string } | null>(null);
  const [deleting, setDeleting]           = useState(false);
  const [refreshingPrices, setRefreshingPrices] = useState(false);
  const [refreshMessage, setRefreshMessage]     = useState<string | null>(null);
  const [showManualPrice, setShowManualPrice]   = useState(false);
  const [manualSubmitting, setManualSubmitting] = useState(false);
  const [manualError, setManualError]           = useState<string | null>(null);
  const [searchQuery, setSearchQuery]           = useState('');
  const [sortKey, setSortKey]                   = useState<SortColKey>('name');
  const [sortDir, setSortDir]                   = useState<'asc' | 'desc'>('asc');

  // ── instrument form ───────────────────────────────────────────────────────
  const [instrForm, setInstrForm] = useState({ ...EMPTY_INSTRUMENT });
  const onInstrChange = (k: keyof typeof EMPTY_INSTRUMENT, v: string) =>
    setInstrForm(s => ({ ...s, [k]: v }));

  const selectedType = types.find(type => String(type.id) === instrForm.typeId);
  const selectedTypeVisual = selectedType
    ? getInvestmentTypeVisual(selectedType.code, selectedType.name)
    : null;

  // ── platform form ─────────────────────────────────────────────────────────
  const [platForm, setPlatForm] = useState({ ...EMPTY_PLATFORM });
  const onPlatChange = (k: keyof typeof EMPTY_PLATFORM, v: string) =>
    setPlatForm(s => ({ ...s, [k]: v }));

  // ── classification form ───────────────────────────────────────────────────
  const [classForm, setClassForm] = useState({ ...EMPTY_CLASSIFICATION });
  const onClassChange = (k: keyof typeof EMPTY_CLASSIFICATION, v: string) =>
    setClassForm(s => ({ ...s, [k]: v }));

  const [exposureForm, setExposureForm] = useState({ ...EMPTY_EXPOSURE });
  const [exposureEditingId, setExposureEditingId] = useState<number | null>(null);
  const [exposureSubmitting, setExposureSubmitting] = useState(false);
  const [exposureError, setExposureError] = useState<string | null>(null);
  const [exposureMode, setExposureMode] = useState<ExposureMode>('UNIQUE');
  const onExposureChange = (k: keyof typeof EMPTY_EXPOSURE, v: string) =>
    setExposureForm(s => ({ ...s, [k]: v }));

  const classificationItems = useMemo<CatalogOption[]>(() => {
    switch (classificationKind) {
      case 'countries': return countries;
      case 'regions': return regions;
      case 'sectors': return sectors;
      case 'industries': return industries;
    }
  }, [classificationKind, countries, regions, sectors, industries]);

  const classificationLabel = useMemo(() => {
    switch (classificationKind) {
      case 'countries': return 'Country';
      case 'regions': return 'Region';
      case 'sectors': return 'Sector';
      case 'industries': return 'Industry';
    }
  }, [classificationKind]);

  const [manualPriceForm, setManualPriceForm] = useState(() => ({
    instrumentId: '',
    price: '',
    asOfDate: new Date().toISOString().split('T')[0],
  }));
  const [manualAssetQuery, setManualAssetQuery] = useState('');
  const [manualAssetDropdownOpen, setManualAssetDropdownOpen] = useState(false);

  // ── filtered / sorted data ────────────────────────────────────────────────
  const filteredInstruments = useMemo(() => {
    const q = searchQuery.toLowerCase();
    const list = instruments.filter(i =>
      !q ||
      i.name.toLowerCase().includes(q) ||
      i.symbol.toLowerCase().includes(q) ||
      i.code.toLowerCase().includes(q) ||
      (i.countryName ?? i.countryCode ?? '').toLowerCase().includes(q) ||
      (i.regionName ?? i.regionCode ?? '').toLowerCase().includes(q) ||
      (i.sectorName ?? i.sectorCode ?? '').toLowerCase().includes(q) ||
      (i.industryName ?? i.industryCode ?? '').toLowerCase().includes(q) ||
      (types.find(t => t.id === i.typeId)?.name ?? '').toLowerCase().includes(q)
    );
    return [...list].sort((a, b) => {
      const av = sortKey === 'symbol'   ? a.symbol
               : sortKey === 'code'     ? a.code
               : sortKey === 'market'   ? (a.market ?? '')
               : sortKey === 'currency' ? a.currency
               : sortKey === 'countryCode' ? (a.countryName ?? a.countryCode ?? '')
               : sortKey === 'region' ? (a.regionName ?? a.regionCode ?? '')
               : sortKey === 'sector' ? (a.sectorName ?? a.sectorCode ?? '')
               : sortKey === 'industry' ? (a.industryName ?? a.industryCode ?? '')
               : a.name;
      const bv = sortKey === 'symbol'   ? b.symbol
               : sortKey === 'code'     ? b.code
               : sortKey === 'market'   ? (b.market ?? '')
               : sortKey === 'currency' ? b.currency
               : sortKey === 'countryCode' ? (b.countryName ?? b.countryCode ?? '')
               : sortKey === 'region' ? (b.regionName ?? b.regionCode ?? '')
               : sortKey === 'sector' ? (b.sectorName ?? b.sectorCode ?? '')
               : sortKey === 'industry' ? (b.industryName ?? b.industryCode ?? '')
               : b.name;
      return sortDir === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av);
    });
  }, [instruments, searchQuery, sortKey, sortDir, types]);

  const filteredPlatforms = useMemo(() => {
    const q = searchQuery.toLowerCase();
    return platforms.filter(p =>
      !q || p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q)
    );
  }, [platforms, searchQuery]);

  const filteredClassifications = useMemo(() => {
    const q = searchQuery.toLowerCase();
    return classificationItems.filter(item =>
      !q || item.name.toLowerCase().includes(q) || item.code.toLowerCase().includes(q)
    );
  }, [classificationItems, searchQuery]);

  const toggleSort = (col: SortColKey) => {
    if (sortKey === col) setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    else { setSortKey(col); setSortDir('asc'); }
  };

  // ── open/close form ───────────────────────────────────────────────────────
  const openCreate = () => {
    setShowManualPrice(false);
    setShowExposureModal(false);
    setManualError(null);
    setEditingId(null);
    setFormError(null);
    setExposureMode('UNIQUE');
    resetExposureForm();
    if (section === 'instruments') setInstrForm({ ...EMPTY_INSTRUMENT });
    else if (section === 'platforms') setPlatForm({ ...EMPTY_PLATFORM });
    else setClassForm({ ...EMPTY_CLASSIFICATION });
    setShowForm(true);
  };

  const openEdit = async (item: InvestmentInstrument | InvestmentPlatform | CatalogOption) => {
    setShowManualPrice(false);
    setShowExposureModal(false);
    setManualError(null);
    setEditingId(item.id);
    setFormError(null);
    if (section === 'instruments') {
      const i = item as InvestmentInstrument;
      setInstrForm({
        typeId: String(i.typeId),
        code: i.code,
        symbol: i.symbol,
        name: i.name,
        market: i.market ?? '',
        currency: i.currency,
        lastPrice: i.lastPrice != null ? String(i.lastPrice) : '',
        lastPriceSource: i.lastPriceSource ?? '',
        lastPriceAt: i.lastPriceAt ? i.lastPriceAt.slice(0, 16) : '',
        scraperUrl: i.scraperUrl ?? '',
        finectUrl: i.finectUrl ?? '',
        countryId: i.countryId != null ? String(i.countryId) : '',
        regionId: i.regionId != null ? String(i.regionId) : '',
        sectorId: i.sectorId != null ? String(i.sectorId) : '',
        industryId: i.industryId != null ? String(i.industryId) : '',
      });
      setExposureEditingId(null);
      setExposureError(null);
      setExposureForm({ ...EMPTY_EXPOSURE });
      try {
        const loadedExposures = await loadInstrumentExposures(i.id);
        setExposureMode(loadedExposures.length > 0 ? 'COMPOUND' : 'UNIQUE');
      } catch (err: unknown) {
        const eRes = err as { response?: { data?: { message?: string } }; message?: string };
        setExposureError(eRes?.response?.data?.message || eRes?.message || 'Error loading exposures');
        setExposureMode('UNIQUE');
      }
    } else {
      if (section === 'platforms') {
        const p = item as InvestmentPlatform;
        setPlatForm({ code: p.code, name: p.name });
      } else {
        const c = item as unknown as CatalogOption;
        setClassForm({ code: c.code, name: c.name });
      }
    }
    setShowForm(true);
  };

  const closeForm = () => {
    setShowForm(false);
    setShowExposureModal(false);
    setEditingId(null);
    setFormError(null);
  };

  const openManualPrice = () => {
    setShowForm(false);
    setEditingId(null);
    setFormError(null);
    setManualError(null);
    setManualPriceForm({
      instrumentId: '',
      price: '',
      asOfDate: new Date().toISOString().split('T')[0],
    });
    setManualAssetQuery('');
    setManualAssetDropdownOpen(false);
    setShowManualPrice(true);
  };

  const closeManualPrice = () => {
    setShowManualPrice(false);
    setManualError(null);
    setManualAssetDropdownOpen(false);
  };

  // ── submit handlers ───────────────────────────────────────────────────────
  const submitInstrument = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      const payload: Omit<InvestmentInstrument, 'id'> = {
        typeId: Number(instrForm.typeId),
        code: instrForm.code.trim(),
        symbol: instrForm.symbol.trim(),
        name: instrForm.name.trim(),
        market: instrForm.market.trim() || undefined,
        currency: instrForm.currency.trim().toUpperCase(),
        lastPrice: instrForm.lastPrice !== '' ? Number(instrForm.lastPrice) : undefined,
        lastPriceSource: instrForm.lastPriceSource.trim() || undefined,
        lastPriceAt: instrForm.lastPriceAt || undefined,
        scraperUrl: instrForm.scraperUrl.trim() || undefined,
        finectUrl: instrForm.finectUrl.trim() || undefined,
        countryId: supportsExposureEditing && exposureMode === 'COMPOUND'
          ? undefined
          : (instrForm.countryId ? Number(instrForm.countryId) : undefined),
        regionId: supportsExposureEditing && exposureMode === 'COMPOUND'
          ? undefined
          : (instrForm.regionId ? Number(instrForm.regionId) : undefined),
        sectorId: supportsExposureEditing && exposureMode === 'COMPOUND'
          ? undefined
          : (instrForm.sectorId ? Number(instrForm.sectorId) : undefined),
        industryId: supportsExposureEditing && exposureMode === 'COMPOUND'
          ? undefined
          : (instrForm.industryId ? Number(instrForm.industryId) : undefined),
      };
      if (editingId) await editInstrument(editingId, payload);
      else await addInstrument(payload);
      closeForm();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string };
      setFormError(e?.response?.data?.message || e?.message || 'Error saving instrument');
    } finally {
      setSubmitting(false);
    }
  };

  const submitPlatform = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      const payload: Omit<InvestmentPlatform, 'id'> = {
        code: platForm.code.trim(),
        name: platForm.name.trim(),
      };
      if (editingId) await editPlatform(editingId, payload);
      else await addPlatform(payload);
      closeForm();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string };
      setFormError(e?.response?.data?.message || e?.message || 'Error saving platform');
    } finally {
      setSubmitting(false);
    }
  };

  const submitClassification = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      const payload: Omit<CatalogOption, 'id'> = {
        code: classForm.code.trim(),
        name: classForm.name.trim(),
      };

      if (classificationKind === 'countries') {
        if (editingId) await editCountry(editingId, payload);
        else await addCountry(payload);
      } else if (classificationKind === 'regions') {
        if (editingId) await editRegion(editingId, payload);
        else await addRegion(payload);
      } else if (classificationKind === 'sectors') {
        if (editingId) await editSector(editingId, payload);
        else await addSector(payload);
      } else {
        if (editingId) await editIndustry(editingId, payload);
        else await addIndustry(payload);
      }

      closeForm();
    } catch (err: unknown) {
      const eRes = err as { response?: { data?: { message?: string } }; message?: string };
      setFormError(eRes?.response?.data?.message || eRes?.message || `Error saving ${classificationLabel.toLowerCase()}`);
    } finally {
      setSubmitting(false);
    }
  };

  const resetExposureForm = () => {
    setExposureEditingId(null);
    setExposureError(null);
    setExposureForm({ ...EMPTY_EXPOSURE });
  };

  const instrumentExposures = editingId ? exposuresByInstrument[editingId] ?? [] : [];
  const supportsExposureEditing = Boolean(selectedType && ['ETF', 'FUND'].includes(selectedType.code));

  const selectedExposureOptions = useMemo(() => {
    switch (exposureForm.dimension) {
      case 'COUNTRY': return countries;
      case 'REGION': return regions;
      case 'SECTOR': return sectors;
      case 'INDUSTRY': return industries;
    }
  }, [countries, exposureForm.dimension, industries, regions, sectors]);

  const exposureTotalWeight = useMemo(
    () => instrumentExposures.reduce((sum, item) => sum + Number(item.weightPct || 0), 0),
    [instrumentExposures],
  );

  const submitExposure = async () => {
    if (!editingId) return;
    setExposureError(null);
    setExposureSubmitting(true);
    try {
      const payload = {
        dimension: exposureForm.dimension,
        countryId: exposureForm.dimension === 'COUNTRY' && exposureForm.countryId ? Number(exposureForm.countryId) : undefined,
        regionId: exposureForm.dimension === 'REGION' && exposureForm.regionId ? Number(exposureForm.regionId) : undefined,
        sectorId: exposureForm.dimension === 'SECTOR' && exposureForm.sectorId ? Number(exposureForm.sectorId) : undefined,
        industryId: exposureForm.dimension === 'INDUSTRY' && exposureForm.industryId ? Number(exposureForm.industryId) : undefined,
        weightPct: Number(exposureForm.weightPct),
      } satisfies Omit<InvestmentInstrumentExposure, 'id' | 'instrumentId' | 'bucketCode' | 'bucketName'>;
      if (exposureEditingId) await editInstrumentExposure(editingId, exposureEditingId, payload);
      else await addInstrumentExposure(editingId, payload);
      resetExposureForm();
    } catch (err: unknown) {
      const eRes = err as { response?: { data?: { message?: string } }; message?: string };
      setExposureError(eRes?.response?.data?.message || eRes?.message || 'Error saving exposure');
    } finally {
      setExposureSubmitting(false);
    }
  };

  const editExposure = (item: InvestmentInstrumentExposure) => {
    setExposureEditingId(item.id);
    setExposureError(null);
    setExposureForm({
      dimension: item.dimension,
      countryId: item.countryId != null ? String(item.countryId) : '',
      regionId: item.regionId != null ? String(item.regionId) : '',
      sectorId: item.sectorId != null ? String(item.sectorId) : '',
      industryId: item.industryId != null ? String(item.industryId) : '',
      weightPct: String(item.weightPct),
    });
  };

  const deleteExposure = async (item: InvestmentInstrumentExposure) => {
    if (!editingId) return;
    setExposureError(null);
    try {
      await removeInstrumentExposure(editingId, item.id);
      if (exposureEditingId === item.id) resetExposureForm();
    } catch (err: unknown) {
      const eRes = err as { response?: { data?: { message?: string } }; message?: string };
      setExposureError(eRes?.response?.data?.message || eRes?.message || 'Error deleting exposure');
    }
  };

  const openExposureManager = async () => {
    if (!editingId) return;
    setExposureError(null);
    try {
      await loadInstrumentExposures(editingId);
    } catch (err: unknown) {
      const eRes = err as { response?: { data?: { message?: string } }; message?: string };
      setExposureError(eRes?.response?.data?.message || eRes?.message || 'Error loading exposures');
    }
    setShowExposureModal(true);
  };

  // ── delete handler ────────────────────────────────────────────────────────
  const handleDelete = async () => {
    if (!confirmDelete) return;
    setDeleting(true);
    try {
      if (section === 'instruments') await removeInstrument(confirmDelete.id);
      else if (section === 'platforms') await removePlatform(confirmDelete.id);
      else if (classificationKind === 'countries') await removeCountry(confirmDelete.id);
      else if (classificationKind === 'regions') await removeRegion(confirmDelete.id);
      else if (classificationKind === 'sectors') await removeSector(confirmDelete.id);
      else await removeIndustry(confirmDelete.id);
      setConfirmDelete(null);
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string };
      clearError();
      setConfirmDelete(null);
      // surface error via global error slot
      console.error(e?.response?.data?.message || e?.message);
    } finally {
      setDeleting(false);
    }
  };

  // ── section switch ────────────────────────────────────────────────────────
  const switchSection = (s: Section) => {
    setSection(s);
    setSearchQuery('');
    closeForm();
    closeManualPrice();
    setConfirmDelete(null);
    setRefreshMessage(null);
  };

  const handleRefreshPrices = async () => {
    setRefreshMessage(null);
    setRefreshingPrices(true);
    try {
      const result = await refreshPrices();
      setRefreshMessage(
        result.updatedInstruments > 0
          ? `Prices refreshed for ${result.updatedInstruments} instruments.`
          : 'Refresh finished. No instrument prices were updated.'
      );
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string };
      setRefreshMessage(e?.response?.data?.message || e?.message || 'Error refreshing instrument prices');
    } finally {
      setRefreshingPrices(false);
    }
  };

  const submitManualPrice = async (e: React.FormEvent) => {
    e.preventDefault();
    setManualError(null);

    const selectedInstrument = instruments.find(i => i.id === Number(manualPriceForm.instrumentId));
    if (!selectedInstrument) {
      setManualError('Selecciona un activo.');
      return;
    }
    const price = Number(manualPriceForm.price);
    if (!Number.isFinite(price) || price <= 0) {
      setManualError('El precio debe ser mayor que 0.');
      return;
    }
    if (!manualPriceForm.asOfDate) {
      setManualError('Selecciona una fecha válida.');
      return;
    }

    setManualSubmitting(true);
    try {
      const result = await addManualPrice({
        instrumentId: selectedInstrument.id,
        price,
        currency: selectedInstrument.currency,
        source: 'MANUAL_UI',
        asOf: `${manualPriceForm.asOfDate}T12:00:00`,
      });

      setRefreshMessage(
        result.updatedInstruments > 0
          ? `Precio manual guardado para ${selectedInstrument.symbol}.`
          : 'No se pudo actualizar el precio manual.',
      );
      setShowManualPrice(false);
    } catch (err: unknown) {
      const eRes = err as { response?: { data?: { message?: string } }; message?: string };
      setManualError(eRes?.response?.data?.message || eRes?.message || 'Error guardando precio manual');
    } finally {
      setManualSubmitting(false);
    }
  };

  const selectedManualInstrument = instruments.find(i => i.id === Number(manualPriceForm.instrumentId));
  const selectedManualType = selectedManualInstrument
    ? types.find(t => t.id === selectedManualInstrument.typeId)
    : undefined;
  const selectedManualVisual = getInvestmentTypeVisual(selectedManualType?.code, selectedManualType?.name);
  const manualFilteredInstruments = useMemo(() => {
    const query = manualAssetQuery.trim().toLowerCase();
    const sorted = instruments.slice().sort((a, b) => a.name.localeCompare(b.name));
    if (!query) {
      return sorted.slice(0, 40);
    }
    return sorted
      .filter(i =>
        i.name.toLowerCase().includes(query) ||
        i.symbol.toLowerCase().includes(query) ||
        i.code.toLowerCase().includes(query),
      )
      .slice(0, 40);
  }, [instruments, manualAssetQuery]);

  if (loading) return <div className="ict-empty">Cargando catálogo…</div>;

  return (
    <div className="ict-wrapper">

      {/* ── Section toggle ── */}
      <div className="ict-section-toggle">
        <button
          type="button"
          className={`ict-toggle-btn${section === 'instruments' ? ' active' : ''}`}
          onClick={() => switchSection('instruments')}
        >
          🏷 Instruments
        </button>
        <button
          type="button"
          className={`ict-toggle-btn${section === 'platforms' ? ' active' : ''}`}
          onClick={() => switchSection('platforms')}
        >
          🏦 Platforms
        </button>
        <button
          type="button"
          className={`ict-toggle-btn${section === 'classifications' ? ' active' : ''}`}
          onClick={() => switchSection('classifications')}
        >
          🌍 Classifications
        </button>
      </div>

      {/* ── KPI cards ────────────────────────────────────────────────────── */}
      <div className="ict-kpi-grid">
        <article className="ict-kpi ict-kpi--instruments">
          <span className="ict-kpi-icon">📦</span>
          <span className="ict-kpi-label">Total activos</span>
          <strong className="ict-kpi-value">{instruments.length}</strong>
          <span className="ict-kpi-sub">
            {searchQuery ? `${filteredInstruments.length} visibles` : 'instrumentos'}
          </span>
        </article>
        {types.map(t => {
          const count = instruments.filter(i => i.typeId === t.id).length;
          const visual = getInvestmentTypeVisual(t.code, t.name);
          return (
            <article key={t.id} className="ict-kpi" style={{ borderLeftColor: visual.color }}>
              <span className="ict-kpi-icon">{visual.emoji}</span>
              <span className="ict-kpi-label">{t.name}</span>
              <strong className="ict-kpi-value">{count}</strong>
              <span className="ict-kpi-sub">{t.code}</span>
            </article>
          );
        })}
        <article className="ict-kpi ict-kpi--platforms">
          <span className="ict-kpi-icon">🏦</span>
          <span className="ict-kpi-label">Plataformas</span>
          <strong className="ict-kpi-value">{platforms.length}</strong>
          <span className="ict-kpi-sub">brokers / custodios</span>
        </article>
      </div>

      {/* ── Toolbar ──────────────────────────────────────────────────────── */}
      <div className="ict-toolbar">
        <div className="ict-search-wrap">
          <span className="ict-search-icon">🔍</span>
          <input
            type="search"
            className="ict-search"
            placeholder={section === 'instruments'
              ? 'Buscar por nombre, símbolo, código o tipo…'
              : section === 'platforms'
                ? 'Buscar plataforma…'
                : 'Buscar clasificación…'}
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
        </div>
        <div className="ict-toolbar-actions">
          {section === 'instruments' && (
            <button
              type="button"
              className="ict-btn-manual"
              onClick={openManualPrice}
              disabled={showManualPrice}
            >
              + Añadir precio manual
            </button>
          )}
          {section === 'instruments' && (
            <button
              type="button"
              className="ict-btn-refresh"
              onClick={handleRefreshPrices}
              disabled={refreshingPrices}
            >
              {refreshingPrices ? '⏳ Actualizando…' : '↻ Refresh prices'}
            </button>
          )}
          <button
            type="button"
            className={`ict-btn-add${showForm ? ' ict-btn-add--cancel' : ''}`}
            onClick={showForm ? closeForm : openCreate}
          >
            {showForm
              ? '✕ Cancelar'
              : section === 'instruments'
                ? '+ Nuevo activo'
                : section === 'platforms'
                  ? '+ Nueva plataforma'
                  : `+ Nuevo ${classificationLabel}`}
          </button>
        </div>
      </div>

      {/* ── Error toast ── */}
      {error && (
        <div className="ict-toast-error" role="alert">
          <span>⚠ {error}</span>
          <button type="button" onClick={clearError}>Cerrar</button>
        </div>
      )}

      {refreshMessage && (
        <div className="ict-toast-info" role="status">
          <span>{refreshMessage}</span>
          <button type="button" onClick={() => setRefreshMessage(null)}>Cerrar</button>
        </div>
      )}

      {/* ══ MANUAL PRICE MODAL ══ */}
      {showManualPrice && section === 'instruments' && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal ict-manual-price-modal">
            <div className="modal-header">
              <h4>Añadir precio manual</h4>
              <button className="modal-close" type="button" onClick={closeManualPrice}>✕</button>
            </div>
            <form onSubmit={submitManualPrice} className="modal-body">
              <div className="modal-row">
                <label>Activo *</label>
                <div className="ict-manual-combo">
                  <input
                    type="text"
                    className="ict-manual-input"
                    placeholder="Buscar por símbolo, nombre o código…"
                    value={manualAssetQuery}
                    onChange={(e) => {
                      setManualAssetQuery(e.target.value);
                      setManualAssetDropdownOpen(true);
                      if (manualPriceForm.instrumentId) {
                        setManualPriceForm(s => ({ ...s, instrumentId: '' }));
                      }
                    }}
                    onFocus={() => setManualAssetDropdownOpen(true)}
                    onBlur={() => setTimeout(() => setManualAssetDropdownOpen(false), 140)}
                  />
                  {manualAssetDropdownOpen && (
                    <div className="ict-manual-dropdown">
                      {manualFilteredInstruments.length === 0 ? (
                        <div className="ict-manual-option-empty">Sin resultados</div>
                      ) : (
                        manualFilteredInstruments.map((instrument) => (
                          <div
                            key={instrument.id}
                            className={`ict-manual-option${manualPriceForm.instrumentId === String(instrument.id) ? ' selected' : ''}`}
                            onMouseDown={() => {
                              setManualPriceForm(s => ({ ...s, instrumentId: String(instrument.id) }));
                              setManualAssetQuery(`${instrument.symbol} · ${instrument.name}`);
                              setManualAssetDropdownOpen(false);
                            }}
                          >
                            <span className="ict-manual-option-symbol">{instrument.symbol}</span>
                            <span className="ict-manual-option-name">{instrument.name}</span>
                            <span className="ict-manual-option-code">{instrument.code}</span>
                          </div>
                        ))
                      )}
                    </div>
                  )}
                </div>
                {!manualPriceForm.instrumentId && (
                  <span className="ict-manual-help">Selecciona un activo de la lista para guardar el precio.</span>
                )}
              </div>

              {selectedManualInstrument && (
                <div className="ict-manual-preview">
                  <span className="ict-manual-preview__type" style={{ background: selectedManualVisual.background, color: selectedManualVisual.color }}>
                    {selectedManualVisual.emoji} {selectedManualType?.code ?? 'TYPE'}
                  </span>
                  <strong>{selectedManualInstrument.symbol}</strong>
                  <span>{selectedManualInstrument.name}</span>
                  <span className="ict-manual-preview__currency">Moneda: {selectedManualInstrument.currency}</span>
                </div>
              )}

              <div className="ict-manual-grid">
                <div className="modal-row">
                  <label>Fecha *</label>
                  <input
                    type="date"
                    required
                    value={manualPriceForm.asOfDate}
                    onChange={e => setManualPriceForm(s => ({ ...s, asOfDate: e.target.value }))}
                  />
                </div>
                <div className="modal-row">
                  <label>Precio *</label>
                  <input
                    type="number"
                    min="0"
                    step="any"
                    required
                    placeholder="0.00"
                    value={manualPriceForm.price}
                    onChange={e => setManualPriceForm(s => ({ ...s, price: e.target.value }))}
                  />
                </div>
              </div>

              {manualError && <div className="modal-error">{manualError}</div>}

              <div className="modal-actions">
                <button className="at-btn-primary" type="submit" disabled={manualSubmitting}>
                  {manualSubmitting ? 'Guardando…' : 'Guardar precio'}
                </button>
                <button className="at-btn-secondary" type="button" onClick={closeManualPrice}>Cancelar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ══ INSTRUMENT FORM MODAL ══ */}
      {showForm && section === 'instruments' && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <div className="modal-header">
              <h4>{editingId ? 'Edit instrument' : 'New instrument'}</h4>
              <button className="modal-close" type="button" onClick={closeForm}>✕</button>
            </div>
            <form onSubmit={submitInstrument} className="modal-body">
              <div className="modal-row">
                <label>Type *</label>
                <select
                  required
                  className="modal-select"
                  value={instrForm.typeId}
                  onChange={e => onInstrChange('typeId', e.target.value)}
                >
                  <option value="">-- Select type --</option>
                  {types.map(t => (
                    <option key={t.id} value={t.id}>{getTypeLabel(t)}</option>
                  ))}
                </select>
                {selectedType && selectedTypeVisual && (
                  <div
                    className="ict-type-preview"
                    style={{
                      color: selectedTypeVisual.color,
                      background: selectedTypeVisual.background,
                      borderColor: selectedTypeVisual.background,
                    }}
                  >
                    <span className="ict-type-preview__emoji">{selectedTypeVisual.emoji}</span>
                    <span>{selectedType.name}</span>
                    <span className="ict-type-preview__code">{selectedType.code}</span>
                  </div>
                )}
              </div>
              <div className="modal-row">
                <label>Code *</label>
                <input
                  required
                  maxLength={100}
                  placeholder="e.g. MSFT"
                  value={instrForm.code}
                  onChange={e => onInstrChange('code', e.target.value)}
                />
              </div>
              <div className="modal-row">
                <label>Symbol *</label>
                <input
                  required
                  maxLength={50}
                  placeholder="e.g. MSFT"
                  value={instrForm.symbol}
                  onChange={e => onInstrChange('symbol', e.target.value)}
                />
              </div>
              <div className="modal-row">
                <label>Name *</label>
                <input
                  required
                  maxLength={150}
                  placeholder="Full name"
                  value={instrForm.name}
                  onChange={e => onInstrChange('name', e.target.value)}
                />
              </div>
              <div className="modal-row">
                <label>Market</label>
                <select
                  className="modal-select"
                  value={instrForm.market}
                  onChange={e => onInstrChange('market', e.target.value)}
                >
                  {INVESTMENT_MARKET_OPTIONS.map(option => (
                    <option key={option.value || 'empty-market'} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </div>
              <div className="modal-row">
                <label>Currency *</label>
                <select
                  required
                  className="modal-select"
                  value={instrForm.currency}
                  onChange={e => onInstrChange('currency', e.target.value)}
                >
                  {INVESTMENT_CURRENCY_OPTIONS.map(option => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </div>
              <div className="modal-row">
                <label>Last price</label>
                <input
                  type="number"
                  min="0"
                  step="any"
                  placeholder="0.00"
                  value={instrForm.lastPrice}
                  onChange={e => onInstrChange('lastPrice', e.target.value)}
                />
              </div>
              <div className="modal-row">
                <label>Price source</label>
                <select
                  className="modal-select"
                  value={instrForm.lastPriceSource}
                  onChange={e => onInstrChange('lastPriceSource', e.target.value)}
                >
                  {INVESTMENT_PRICE_SOURCE_OPTIONS.map(option => (
                    <option key={option.value || 'empty-source'} value={option.value}>{option.label}</option>
                  ))}
                </select>
              </div>
              <div className="modal-row">
                <label>Price date</label>
                <input
                  type="datetime-local"
                  value={instrForm.lastPriceAt}
                  onChange={e => onInstrChange('lastPriceAt', e.target.value)}
                />
              </div>
              <div className="modal-row">
                <label>Price scraper URL</label>
                <input
                  type="url"
                  maxLength={500}
                  placeholder="https://..."
                  value={instrForm.scraperUrl}
                  onChange={e => onInstrChange('scraperUrl', e.target.value)}
                />
              </div>
              <div className="modal-row">
                <label>Finect URL</label>
                <input
                  type="url"
                  maxLength={500}
                  placeholder="https://..."
                  value={instrForm.finectUrl}
                  onChange={e => onInstrChange('finectUrl', e.target.value)}
                />
              </div>
              {supportsExposureEditing && (
                <div className="ict-exposure-mode-switch">
                  <span className="ict-exposure-mode-switch__label">Exposure mode</span>
                  <div className="ict-exposure-mode-switch__controls">
                    <button
                      type="button"
                      className={`ict-toggle-chip${exposureMode === 'UNIQUE' ? ' active' : ''}`}
                      onClick={() => setExposureMode('UNIQUE')}
                    >
                      Unique
                    </button>
                    <button
                      type="button"
                      className={`ict-toggle-chip${exposureMode === 'COMPOUND' ? ' active' : ''}`}
                      onClick={() => setExposureMode('COMPOUND')}
                    >
                      Compound
                    </button>
                  </div>
                </div>
              )}

              {(!supportsExposureEditing || exposureMode === 'UNIQUE') && (
                <>
                  <div className="modal-row">
                    <label>Country</label>
                    <select
                      className="modal-select"
                      value={instrForm.countryId}
                      onChange={e => onInstrChange('countryId', e.target.value)}
                    >
                      <option value="">-- Select country --</option>
                      {countries.map(option => (
                        <option key={option.id} value={option.id}>{option.name} ({option.code})</option>
                      ))}
                    </select>
                  </div>
                  <div className="modal-row">
                    <label>Region</label>
                    <select
                      className="modal-select"
                      value={instrForm.regionId}
                      onChange={e => onInstrChange('regionId', e.target.value)}
                    >
                      <option value="">-- Select region --</option>
                      {regions.map(option => (
                        <option key={option.id} value={option.id}>{option.name}</option>
                      ))}
                    </select>
                  </div>
                  <div className="modal-row">
                    <label>Sector</label>
                    <select
                      className="modal-select"
                      value={instrForm.sectorId}
                      onChange={e => onInstrChange('sectorId', e.target.value)}
                    >
                      <option value="">-- Select sector --</option>
                      {sectors.map(option => (
                        <option key={option.id} value={option.id}>{option.name}</option>
                      ))}
                    </select>
                  </div>
                  <div className="modal-row">
                    <label>Industry</label>
                    <select
                      className="modal-select"
                      value={instrForm.industryId}
                      onChange={e => onInstrChange('industryId', e.target.value)}
                    >
                      <option value="">-- Select industry --</option>
                      {industries.map(option => (
                        <option key={option.id} value={option.id}>{option.name}</option>
                      ))}
                    </select>
                  </div>
                </>
              )}

              {supportsExposureEditing && exposureMode === 'COMPOUND' && (
                <div className="ict-exposure-summary-row">
                  <div className="ict-exposure-summary-row__text">
                    <strong>Compound exposure enabled</strong>
                    {editingId
                      ? <span>{instrumentExposures.length} entries • total {exposureTotalWeight.toFixed(2)}%</span>
                      : <span>Save the instrument first to configure compound exposure.</span>}
                  </div>
                  <button
                    type="button"
                    className="ict-btn-manage-exposure"
                    onClick={openExposureManager}
                    disabled={!editingId}
                  >
                    Manage exposure
                  </button>
                </div>
              )}
              {formError && <div className="modal-error">{formError}</div>}
              <div className="modal-actions">
                <button className="at-btn-primary" type="submit" disabled={submitting}>
                  {submitting ? 'Saving…' : editingId ? 'Save changes' : 'Create'}
                </button>
                <button className="at-btn-secondary" type="button" onClick={closeForm}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showExposureModal && showForm && section === 'instruments' && supportsExposureEditing && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal ict-exposure-modal">
            <div className="modal-header">
              <h4>Compound exposure editor</h4>
              <button className="modal-close" type="button" onClick={() => setShowExposureModal(false)}>✕</button>
            </div>
            <form
              className="modal-body"
              onSubmit={(e) => {
                e.preventDefault();
                void submitExposure();
              }}
            >
              <div className="ict-exposure-form-grid">
                <div className="modal-row">
                  <label>Dimension</label>
                  <select
                    className="modal-select"
                    value={exposureForm.dimension}
                    onChange={e => onExposureChange('dimension', e.target.value)}
                  >
                    <option value="SECTOR">Sector</option>
                    <option value="REGION">Region</option>
                    <option value="COUNTRY">Country</option>
                    <option value="INDUSTRY">Industry</option>
                  </select>
                </div>
                <div className="modal-row">
                  <label>{exposureForm.dimension === 'COUNTRY' ? 'Country' : exposureForm.dimension === 'REGION' ? 'Region' : exposureForm.dimension === 'SECTOR' ? 'Sector' : 'Industry'}</label>
                  <select
                    className="modal-select"
                    value={exposureForm.dimension === 'COUNTRY' ? exposureForm.countryId : exposureForm.dimension === 'REGION' ? exposureForm.regionId : exposureForm.dimension === 'SECTOR' ? exposureForm.sectorId : exposureForm.industryId}
                    onChange={e => {
                      const next = e.target.value;
                      setExposureForm(current => ({
                        ...current,
                        countryId: exposureForm.dimension === 'COUNTRY' ? next : '',
                        regionId: exposureForm.dimension === 'REGION' ? next : '',
                        sectorId: exposureForm.dimension === 'SECTOR' ? next : '',
                        industryId: exposureForm.dimension === 'INDUSTRY' ? next : '',
                      }));
                    }}
                  >
                    <option value="">-- Select bucket --</option>
                    {selectedExposureOptions.map(option => (
                      <option key={option.id} value={option.id}>{option.name} ({option.code})</option>
                    ))}
                  </select>
                </div>
                <div className="modal-row">
                  <label>Weight %</label>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    value={exposureForm.weightPct}
                    onChange={e => onExposureChange('weightPct', e.target.value)}
                    placeholder="e.g. 12.5"
                  />
                </div>
              </div>

              <div className="modal-actions modal-actions--compact">
                <button className="at-btn-primary" type="submit" disabled={exposureSubmitting}>
                  {exposureSubmitting ? 'Saving…' : exposureEditingId ? 'Update exposure' : 'Add exposure'}
                </button>
                <button className="at-btn-secondary" type="button" onClick={resetExposureForm}>Clear</button>
              </div>

              {exposureError && <div className="modal-error">{exposureError}</div>}

              <div className="ict-exposure-list">
                {instrumentExposures.length === 0 ? (
                  <div className="ict-empty ict-empty--compact">No exposures added yet.</div>
                ) : instrumentExposures.map(item => (
                  <div key={item.id} className="ict-exposure-item">
                    <span className="ict-exposure-item__label">{item.dimension}</span>
                    <span className="ict-exposure-item__bucket">{item.bucketName ?? item.bucketCode ?? '—'}</span>
                    <span className="ict-exposure-item__weight">{Number(item.weightPct).toFixed(2)}%</span>
                    <div className="ict-actions">
                      <button type="button" className="ict-btn-icon" onClick={() => editExposure(item)}>✏️</button>
                      <button type="button" className="ict-btn-icon ict-btn-icon--danger" onClick={() => deleteExposure(item)}>🗑</button>
                    </div>
                  </div>
                ))}
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ══ PLATFORM FORM MODAL ══ */}
      {showForm && section === 'platforms' && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <div className="modal-header">
              <h4>{editingId ? 'Edit platform' : 'New platform'}</h4>
              <button className="modal-close" type="button" onClick={closeForm}>✕</button>
            </div>
            <form onSubmit={submitPlatform} className="modal-body">
              <div className="modal-row">
                <label>Code *</label>
                <input
                  required
                  maxLength={32}
                  placeholder="e.g. IBKR"
                  value={platForm.code}
                  onChange={e => onPlatChange('code', e.target.value)}
                />
              </div>
              <div className="modal-row">
                <label>Name *</label>
                <input
                  required
                  maxLength={120}
                  placeholder="Full name"
                  value={platForm.name}
                  onChange={e => onPlatChange('name', e.target.value)}
                />
              </div>
              {formError && <div className="modal-error">{formError}</div>}
              <div className="modal-actions">
                <button className="at-btn-primary" type="submit" disabled={submitting}>
                  {submitting ? 'Saving…' : editingId ? 'Save changes' : 'Create'}
                </button>
                <button className="at-btn-secondary" type="button" onClick={closeForm}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ══ CLASSIFICATION FORM MODAL ══ */}
      {showForm && section === 'classifications' && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <div className="modal-header">
              <h4>{editingId ? `Edit ${classificationLabel}` : `New ${classificationLabel}`}</h4>
              <button className="modal-close" type="button" onClick={closeForm}>✕</button>
            </div>
            <form onSubmit={submitClassification} className="modal-body">
              <div className="modal-row">
                <label>Code *</label>
                <input
                  required
                  maxLength={classificationKind === 'countries' ? 2 : classificationKind === 'regions' ? 40 : classificationKind === 'sectors' ? 60 : 80}
                  placeholder={`e.g. ${classificationKind === 'countries' ? 'US' : 'TECH'}`}
                  value={classForm.code}
                  onChange={e => onClassChange('code', e.target.value.toUpperCase())}
                />
              </div>
              <div className="modal-row">
                <label>Name *</label>
                <input
                  required
                  maxLength={classificationKind === 'countries' ? 120 : classificationKind === 'regions' ? 120 : classificationKind === 'sectors' ? 140 : 180}
                  placeholder={`Display name for ${classificationLabel.toLowerCase()}`}
                  value={classForm.name}
                  onChange={e => onClassChange('name', e.target.value)}
                />
              </div>
              {formError && <div className="modal-error">{formError}</div>}
              <div className="modal-actions">
                <button className="at-btn-primary" type="submit" disabled={submitting}>
                  {submitting ? 'Saving…' : editingId ? 'Save changes' : 'Create'}
                </button>
                <button className="at-btn-secondary" type="button" onClick={closeForm}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ══ DELETE CONFIRM MODAL ══ */}
      {confirmDelete && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal" style={{ maxWidth: 420 }}>
            <div className="modal-header">
              <h4>Confirm deletion</h4>
              <button className="modal-close" type="button" onClick={() => setConfirmDelete(null)}>✕</button>
            </div>
            <div className="modal-body ict-confirm-body">
              <p>
                Are you sure you want to delete{' '}
                <strong>{confirmDelete.name}</strong>?
                This action cannot be undone.
              </p>
              <div className="modal-actions">
                <button className="at-btn-primary" style={{ background: '#991b1b' }} type="button" onClick={handleDelete} disabled={deleting}>
                  {deleting ? 'Deleting…' : 'Delete'}
                </button>
                <button className="at-btn-secondary" type="button" onClick={() => setConfirmDelete(null)}>Cancel</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ══ INSTRUMENTS TABLE ══ */}
      {section === 'instruments' && (
        <div className="ict-container">
          <table className="ict-table">
            <thead>
              <tr>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('code')}>
                  Code <SortIcon col="code" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('name')}>
                  Name <SortIcon col="name" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('symbol')}>
                  Symbol <SortIcon col="symbol" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th">Type</th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('market')}>
                  Market <SortIcon col="market" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('currency')}>
                  Currency <SortIcon col="currency" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('countryCode')}>
                  Country <SortIcon col="countryCode" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('region')}>
                  Region <SortIcon col="region" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('sector')}>
                  Sector <SortIcon col="sector" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th-sortable" onClick={() => toggleSort('industry')}>
                  Industry <SortIcon col="industry" active={sortKey} dir={sortDir} />
                </th>
                <th className="ict-th ict-th--right">Last price</th>
                <th className="ict-th">Source</th>
                <th className="ict-th">Price date</th>
                <th className="ict-th">Price URL</th>
                <th className="ict-th">Finect URL</th>
                <th className="ict-th ict-th--actions" aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              {filteredInstruments.length === 0 && (
                <tr><td colSpan={16} className="ict-empty">
                  {instruments.length === 0
                    ? 'No hay activos. Añade uno para empezar.'
                    : 'Ningún activo coincide con la búsqueda.'}
                </td></tr>
              )}
              {filteredInstruments.map(instr => {
                const type = types.find(t => t.id === instr.typeId);
                const typeName = type?.name ?? String(instr.typeId);
                const typeVisual = getInvestmentTypeVisual(type?.code, type?.name);
                return (
                  <tr key={instr.id} className="ict-row">
                    <td className="ict-td"><span className="ict-code">{instr.code}</span></td>
                    <td className="ict-td"><span className="ict-name">{instr.name}</span></td>
                    <td className="ict-td"><span className="ict-symbol">{instr.symbol}</span></td>
                    <td className="ict-td">
                      <span
                        className="ict-badge ict-badge--type"
                        style={{
                          color: typeVisual.color,
                          background: typeVisual.background,
                          borderColor: typeVisual.background,
                        }}
                      >
                        <span className="ict-badge__emoji">{typeVisual.emoji}</span>
                        {typeName}
                      </span>
                    </td>
                    <td className="ict-td"><span className="ict-muted">{instr.market ?? '—'}</span></td>
                    <td className="ict-td"><span className="ict-badge" style={{ background: '#f5f3ff', color: '#5b21b6', borderColor: '#ddd6fe' }}>{instr.currency}</span></td>
                    <td className="ict-td"><span className="ict-muted">{instr.countryName ?? instr.countryCode ?? '—'}</span></td>
                    <td className="ict-td"><span className="ict-muted">{instr.regionName ?? instr.regionCode ?? '—'}</span></td>
                    <td className="ict-td"><span className="ict-muted">{instr.sectorName ?? instr.sectorCode ?? '—'}</span></td>
                    <td className="ict-td"><span className="ict-muted">{instr.industryName ?? instr.industryCode ?? '—'}</span></td>
                    <td className="ict-td ict-td--right"><span className="ict-price">{fmtPrice(instr.lastPrice)}</span></td>
                    <td className="ict-td"><span className="ict-muted">{instr.lastPriceSource ?? '—'}</span></td>
                    <td className="ict-td"><span className="ict-muted">{fmtDate(instr.lastPriceAt)}</span></td>
                    <td className="ict-td">
                      {instr.scraperUrl ? (
                        <a className="ict-scraper-link" href={instr.scraperUrl} target="_blank" rel="noreferrer">🔗 URL</a>
                      ) : (
                        <span className="ict-muted">—</span>
                      )}
                    </td>
                    <td className="ict-td">
                      {instr.finectUrl ? (
                        <a className="ict-scraper-link" href={instr.finectUrl} target="_blank" rel="noreferrer">🔗 Finect</a>
                      ) : (
                        <span className="ict-muted">—</span>
                      )}
                    </td>
                    <td className="ict-td">
                      <div className="ict-actions">
                        <button
                          type="button"
                          className="ict-btn-icon"
                          title="Edit"
                          onClick={() => openEdit(instr)}
                        >✏️</button>
                        <button
                          type="button"
                          className="ict-btn-icon ict-btn-icon--danger"
                          title="Delete"
                          onClick={() => setConfirmDelete({ id: instr.id, name: instr.name })}
                        >🗑</button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* ══ PLATFORMS TABLE ══ */}
      {section === 'platforms' && (
        <div className="ict-container">
          <table className="ict-table">
            <thead>
              <tr>
                <th className="ict-th">Code</th>
                <th className="ict-th">Name</th>
                <th className="ict-th ict-th--actions" aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              {filteredPlatforms.length === 0 && (
                <tr><td colSpan={3} className="ict-empty">
                  {platforms.length === 0
                    ? 'No hay plataformas. Añade una para empezar.'
                    : 'Ninguna plataforma coincide con la búsqueda.'}
                </td></tr>
              )}
              {filteredPlatforms.map(plat => (
                <tr key={plat.id} className="ict-row">
                  <td className="ict-td"><span className="ict-code">{plat.code}</span></td>
                  <td className="ict-td"><span className="ict-name">{plat.name}</span></td>
                  <td className="ict-td">
                    <div className="ict-actions">
                      <button
                        type="button"
                        className="ict-btn-icon"
                        title="Edit"
                        onClick={() => openEdit(plat)}
                      >✏️</button>
                      <button
                        type="button"
                        className="ict-btn-icon ict-btn-icon--danger"
                        title="Delete"
                        onClick={() => setConfirmDelete({ id: plat.id, name: plat.name })}
                      >🗑</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ══ CLASSIFICATION TABLE ══ */}
      {section === 'classifications' && (
        <>
          <div className="ict-section-toggle" style={{ marginTop: '-4px' }}>
            <button type="button" className={`ict-toggle-btn${classificationKind === 'countries' ? ' active' : ''}`} onClick={() => setClassificationKind('countries')}>Countries</button>
            <button type="button" className={`ict-toggle-btn${classificationKind === 'regions' ? ' active' : ''}`} onClick={() => setClassificationKind('regions')}>Regions</button>
            <button type="button" className={`ict-toggle-btn${classificationKind === 'sectors' ? ' active' : ''}`} onClick={() => setClassificationKind('sectors')}>Sectors</button>
            <button type="button" className={`ict-toggle-btn${classificationKind === 'industries' ? ' active' : ''}`} onClick={() => setClassificationKind('industries')}>Industries</button>
          </div>
          <div className="ict-container">
            <table className="ict-table">
              <thead>
                <tr>
                  <th className="ict-th">Code</th>
                  <th className="ict-th">Name</th>
                  <th className="ict-th ict-th--actions" aria-label="Actions"></th>
                </tr>
              </thead>
              <tbody>
                {filteredClassifications.length === 0 && (
                  <tr><td colSpan={3} className="ict-empty">No hay elementos para este catálogo.</td></tr>
                )}
                {filteredClassifications.map(item => (
                  <tr key={item.id} className="ict-row">
                    <td className="ict-td"><span className="ict-code">{item.code}</span></td>
                    <td className="ict-td"><span className="ict-name">{item.name}</span></td>
                    <td className="ict-td">
                      <div className="ict-actions">
                        <button type="button" className="ict-btn-icon" title="Edit" onClick={() => openEdit(item)}>✏️</button>
                        <button type="button" className="ict-btn-icon ict-btn-icon--danger" title="Delete" onClick={() => setConfirmDelete({ id: item.id, name: item.name })}>🗑</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
};
