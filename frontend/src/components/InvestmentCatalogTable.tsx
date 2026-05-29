import React, { useMemo, useState } from 'react';
import { useInvestmentCatalog } from '../hooks/useInvestmentCatalog';
import {
  getInvestmentTypeVisual,
  INVESTMENT_CURRENCY_OPTIONS,
  INVESTMENT_MARKET_OPTIONS,
  INVESTMENT_PRICE_SOURCE_OPTIONS,
} from '../constants/visualConfig';
import type { InvestmentInstrument, InvestmentPlatform, InvestmentType } from '../types/investments';
import './investment-catalog.css';

// ─── Helpers ──────────────────────────────────────────────────────────────────

const fmtPrice = (n?: number) =>
  n != null
    ? n.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 6 })
    : '—';

const fmtDate = (s?: string) =>
  s ? new Date(s).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

type Section = 'instruments' | 'platforms';

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
};

// ─── Platform form state ──────────────────────────────────────────────────────

const EMPTY_PLATFORM = { code: '', name: '' };

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
type SortColKey = 'name' | 'symbol' | 'code' | 'market' | 'currency';

const SortIcon = ({ col, active, dir }: { col: SortColKey; active: SortColKey; dir: 'asc' | 'desc' }) => (
  col !== active
    ? <span className="ict-sort-icon ict-sort-icon--idle">↕</span>
    : <span className="ict-sort-icon">{dir === 'asc' ? '↑' : '↓'}</span>
);

// ─── Main component ───────────────────────────────────────────────────────────

export const InvestmentCatalogTable: React.FC<Props> = ({ token, onUnauthorized }) => {
  const {
    types, instruments, platforms,
    loading, error, clearError,
    addInstrument, editInstrument, removeInstrument,
    refreshPrices,
    addManualPrice,
    addPlatform, editPlatform, removePlatform,
  } = useInvestmentCatalog(token, onUnauthorized);

  const [section, setSection] = useState<Section>('instruments');

  // ── shared modal state ────────────────────────────────────────────────────
  const [showForm, setShowForm]           = useState(false);
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
      (types.find(t => t.id === i.typeId)?.name ?? '').toLowerCase().includes(q)
    );
    return [...list].sort((a, b) => {
      const av = sortKey === 'symbol'   ? a.symbol
               : sortKey === 'code'     ? a.code
               : sortKey === 'market'   ? (a.market ?? '')
               : sortKey === 'currency' ? a.currency
               : a.name;
      const bv = sortKey === 'symbol'   ? b.symbol
               : sortKey === 'code'     ? b.code
               : sortKey === 'market'   ? (b.market ?? '')
               : sortKey === 'currency' ? b.currency
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

  const toggleSort = (col: SortColKey) => {
    if (sortKey === col) setSortDir(d => d === 'asc' ? 'desc' : 'asc');
    else { setSortKey(col); setSortDir('asc'); }
  };

  // ── open/close form ───────────────────────────────────────────────────────
  const openCreate = () => {
    setShowManualPrice(false);
    setManualError(null);
    setEditingId(null);
    setFormError(null);
    if (section === 'instruments') setInstrForm({ ...EMPTY_INSTRUMENT });
    else setPlatForm({ ...EMPTY_PLATFORM });
    setShowForm(true);
  };

  const openEdit = (item: InvestmentInstrument | InvestmentPlatform) => {
    setShowManualPrice(false);
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
      });
    } else {
      const p = item as InvestmentPlatform;
      setPlatForm({ code: p.code, name: p.name });
    }
    setShowForm(true);
  };

  const closeForm = () => { setShowForm(false); setEditingId(null); setFormError(null); };

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

  // ── delete handler ────────────────────────────────────────────────────────
  const handleDelete = async () => {
    if (!confirmDelete) return;
    setDeleting(true);
    try {
      if (section === 'instruments') await removeInstrument(confirmDelete.id);
      else await removePlatform(confirmDelete.id);
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
              : 'Buscar plataforma…'}
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
            {showForm ? '✕ Cancelar' : section === 'instruments' ? '+ Nuevo activo' : '+ Nueva plataforma'}
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
                <label>Scraper URL</label>
                <input
                  type="url"
                  maxLength={500}
                  placeholder="https://..."
                  value={instrForm.scraperUrl}
                  onChange={e => onInstrChange('scraperUrl', e.target.value)}
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
                <th className="ict-th ict-th--right">Last price</th>
                <th className="ict-th">Source</th>
                <th className="ict-th">Price date</th>
                <th className="ict-th">Scraper</th>
                <th className="ict-th ict-th--actions" aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              {filteredInstruments.length === 0 && (
                <tr><td colSpan={11} className="ict-empty">
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
    </div>
  );
};
