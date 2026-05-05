import React, { useState } from 'react';
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

// ─── Main component ───────────────────────────────────────────────────────────

export const InvestmentCatalogTable: React.FC<Props> = ({ token, onUnauthorized }) => {
  const {
    types, instruments, platforms,
    loading, error, clearError,
    addInstrument, editInstrument, removeInstrument,
    refreshPrices,
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
  const [refreshMessage, setRefreshMessage] = useState<string | null>(null);

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

  // ── open/close form ───────────────────────────────────────────────────────
  const openCreate = () => {
    setEditingId(null);
    setFormError(null);
    if (section === 'instruments') setInstrForm({ ...EMPTY_INSTRUMENT });
    else setPlatForm({ ...EMPTY_PLATFORM });
    setShowForm(true);
  };

  const openEdit = (item: InvestmentInstrument | InvestmentPlatform) => {
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
      });
    } else {
      const p = item as InvestmentPlatform;
      setPlatForm({ code: p.code, name: p.name });
    }
    setShowForm(true);
  };

  const closeForm = () => { setShowForm(false); setEditingId(null); setFormError(null); };

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
    closeForm();
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

      {/* ── Summary bar ── */}
      <div className="ict-bar">
        <div className="ict-stats">
          {section === 'instruments' ? (
            <>
              <div className="ict-stat">
                <span className="ict-stat-label">Instruments</span>
                <span className="ict-stat-value">{instruments.length}</span>
              </div>
              {types.map(t => (
                <div key={t.id} className="ict-stat">
                  <span className="ict-stat-label">{t.name}</span>
                  <span className="ict-stat-value">{instruments.filter(i => i.typeId === t.id).length}</span>
                </div>
              ))}
            </>
          ) : (
            <div className="ict-stat">
              <span className="ict-stat-label">Platforms</span>
              <span className="ict-stat-value">{platforms.length}</span>
            </div>
          )}
        </div>
        <button
          type="button"
          className={`ict-btn-add${showForm ? ' ict-btn-add--cancel' : ''}`}
          onClick={showForm ? closeForm : openCreate}
        >
          {showForm ? '✕ Cancelar' : section === 'instruments' ? '+ New instrument' : '+ New platform'}
        </button>
        {section === 'instruments' && (
          <button
            type="button"
            className="ict-btn-refresh"
            onClick={handleRefreshPrices}
            disabled={refreshingPrices}
          >
            {refreshingPrices ? 'Refreshing prices…' : '↻ Refresh prices'}
          </button>
        )}
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
                <th className="ict-th">Code</th>
                <th className="ict-th">Name</th>
                <th className="ict-th">Symbol</th>
                <th className="ict-th">Type</th>
                <th className="ict-th">Market</th>
                <th className="ict-th">Currency</th>
                <th className="ict-th ict-th--right">Last price</th>
                <th className="ict-th">Source</th>
                <th className="ict-th">Price date</th>
                <th className="ict-th ict-th--actions" aria-label="Actions"></th>
              </tr>
            </thead>
            <tbody>
              {instruments.length === 0 && (
                <tr><td colSpan={10} className="ict-empty">No instruments found. Add one to get started.</td></tr>
              )}
              {instruments.map(instr => {
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
              {platforms.length === 0 && (
                <tr><td colSpan={3} className="ict-empty">No platforms found. Add one to get started.</td></tr>
              )}
              {platforms.map(plat => (
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
