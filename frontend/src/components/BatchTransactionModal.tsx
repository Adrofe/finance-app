import { useState, useEffect, useRef, useCallback } from 'react';
import {
  CatalogService,
  type Account,
  type TransactionCategory,
  type TransactionType,
  type Tag,
  type Merchant,
} from '../services/catalogService';
import { createTransaction } from '../services/transactionsService';
import { dispatchFinanceEvent, FINANCE_EVENTS } from '../events/financeEvents';
import { getInstitutionLogo, getCategoryVisual, getMerchantLogo } from '../constants/visualConfig';
import type { TaxType } from '../types/banking';
import axios from 'axios';
import './BatchTransactionModal.css';

// ── Types ─────────────────────────────────────────────────────────────────────

type BatchMode = 'mass' | 'flex';

type MassRow = {
  id: number;
  date: string;
  net: string;
  tax: string;
};

type FlexRow = {
  id: number;
  date: string;
  description: string;
  categoryId: number | '';
  merchantId: number | '';
  tagIds: number[];
  net: string;
  taxTypeId: number | '';
  tax: string;
};

type BatchTransactionModalProps = {
  accessToken: string;
  onClose: () => void;
};

// ── Helpers ───────────────────────────────────────────────────────────────────

let _nextId = 1;
const newRowId = () => _nextId++;

function toDateStr(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function addDays(dateStr: string, n: number): string {
  const d = new Date(dateStr + 'T00:00:00');
  d.setDate(d.getDate() + n);
  return toDateStr(d);
}

function parseNum(s: string): number {
  const n = parseFloat(s.replace(',', '.'));
  return isNaN(n) ? 0 : n;
}

function grossOf(net: string, tax: string): number {
  return parseNum(net) + parseNum(tax);
}

function fmtEur(n: number): string {
  return n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2 });
}

function makeBlankMassRow(date?: string): MassRow {
  return { id: newRowId(), date: date ?? toDateStr(new Date()), net: '', tax: '' };
}

function makeBlankFlexRow(date?: string): FlexRow {
  return {
    id: newRowId(),
    date: date ?? toDateStr(new Date()),
    description: '',
    categoryId: '',
    merchantId: '',
    tagIds: [],
    net: '',
    taxTypeId: '',
    tax: '',
  };
}

// ── Institution logo renderer ─────────────────────────────────────────────────

function InstLogo({ name }: { name?: string }) {
  const src = getInstitutionLogo(name || '');
  if (src.startsWith('/')) {
    return (
      <span className="btm-acc-logo">
        <img
          src={src}
          alt={name || ''}
          onError={e => {
            (e.currentTarget as HTMLImageElement).style.display = 'none';
            const fb = e.currentTarget.nextElementSibling as HTMLElement | null;
            if (fb) fb.style.display = 'inline';
          }}
        />
        <span style={{ display: 'none' }}>🏦</span>
      </span>
    );
  }
  return <span className="btm-acc-logo btm-acc-logo--emoji">{src}</span>;
}

// ── Component ─────────────────────────────────────────────────────────────────

export function BatchTransactionModal({ accessToken, onClose }: BatchTransactionModalProps) {

  // ── Mode
  const [mode, setMode] = useState<BatchMode>('mass');

  // ── Catalog data
  const [accounts, setAccounts]     = useState<Account[]>([]);
  const [categories, setCategories] = useState<TransactionCategory[]>([]);
  const [taxTypes, setTaxTypes]     = useState<TaxType[]>([]);
  const [txnTypes, setTxnTypes]     = useState<TransactionType[]>([]);
  const [tags, setTags]             = useState<Tag[]>([]);
  const [merchants, setMerchants]   = useState<Merchant[]>([]);

  // ── Shared config
  const [accountId, setAccountId] = useState<number | ''>('');

  // ── Mass-mode config
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId]   = useState<number | ''>('');
  const [taxTypeId, setTaxTypeId]     = useState<number | ''>('');

  // ── Mass-mode dropdown states
  const [accountOpen, setAccountOpen]   = useState(false);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [accountDropdownPos, setAccountDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const accountRef  = useRef<HTMLDivElement>(null);

  // ── Rounding feature
  const [roundingEnabled, setRoundingEnabled]                     = useState(false);
  const [roundingAccountId, setRoundingAccountId]                 = useState<number | ''>('');
  const [roundingAccountOpen, setRoundingAccountOpen]             = useState(false);
  const [roundingAccountDropdownPos, setRoundingAccountDropdownPos] = useState<{ top: number; left: number; width: number } | null>(null);
  const roundingAccountRef = useRef<HTMLDivElement>(null);

  // ── Date range helper (mass mode)
  const [rangeFrom, setRangeFrom] = useState('');
  const [rangeTo, setRangeTo]     = useState('');

  // ── Rows
  const [massRows, setMassRows] = useState<MassRow[]>([makeBlankMassRow()]);
  const [flexRows, setFlexRows] = useState<FlexRow[]>([makeBlankFlexRow()]);

  // ── Flex mode per-row overlay states
  const [openCatRowId,   setOpenCatRowId]   = useState<number | null>(null);
  const [openMerchRowId, setOpenMerchRowId] = useState<number | null>(null);
  const [openTagRowId,   setOpenTagRowId]   = useState<number | null>(null);

  // ── Flex mode search state
  const [flexCatSearch,  setFlexCatSearch]  = useState('');
  const [flexMerchQuery, setFlexMerchQuery] = useState('');
  const [flexTagQuery,   setFlexTagQuery]   = useState('');

  // ── Mass mode category search
  const [massCatSearch,  setMassCatSearch]  = useState('');

  // ── Save state
  const [saving, setSaving]               = useState(false);
  const [progress, setProgress]           = useState(0);
  const [progressLabel, setProgressLabel] = useState('');
  const [resultMsg, setResultMsg]         = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const lastNetRef = useRef<HTMLInputElement | null>(null);

  // ── Load catalogs ─────────────────────────────────────────────────────────
  useEffect(() => {
    async function load() {
      try {
        const [accs, cats, txts, typs, tgs, mrchs] = await Promise.all([
          CatalogService.fetchAccounts(accessToken),
          CatalogService.fetchCategories(accessToken),
          CatalogService.fetchTaxTypes(accessToken),
          CatalogService.fetchTypes(accessToken),
          CatalogService.fetchTags(accessToken),
          CatalogService.fetchMerchants(accessToken),
        ]);
        setAccounts(accs);
        setCategories(cats);
        setTaxTypes(txts);
        setTxnTypes(typs);
        setTags(tgs);
        setMerchants(mrchs);
      } catch {
        // non-fatal
      }
    }
    load();
  }, [accessToken]);

  // ── Close dropdowns on outside click ─────────────────────────────────────
  useEffect(() => {
    const handle = (e: MouseEvent) => {
      if (accountRef.current && !accountRef.current.contains(e.target as Node)) setAccountOpen(false);
      if (roundingAccountRef.current && !roundingAccountRef.current.contains(e.target as Node)) setRoundingAccountOpen(false);
    };
    document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, []);

  // ── Derived catalog helpers ───────────────────────────────────────────────
  const selectedAccount  = accounts.find(a => a.id === accountId);
  const selectedCategory = categories.find(c => c.id === categoryId);
  const parentCategories = categories.filter(c => !c.parentId);
  const childrenByParent = categories.reduce<Record<number, TransactionCategory[]>>((acc, c) => {
    if (c.parentId) (acc[c.parentId] ??= []).push(c);
    return acc;
  }, {});

  const catVisual = (cat?: TransactionCategory) => {
    if (!cat) return { emoji: '📂', color: '#9e9e9e' };
    return getCategoryVisual(cat.code ?? '') ?? { emoji: '📂', color: '#9e9e9e' };
  };

  // ── Category picker helpers (flex mode) ───────────────────────────────────

  const filteredParentsForPicker = (search: string) => {
    const q = search.trim().toLowerCase();
    return parentCategories.filter(p =>
      !q || p.name.toLowerCase().includes(q) ||
      (childrenByParent[p.id] || []).some(c => c.name.toLowerCase().includes(q))
    );
  };

  const getVisibleChildren = (parentId: number, search: string): TransactionCategory[] => {
    const children = childrenByParent[parentId] || [];
    if (!search.trim()) return children;
    const q = search.toLowerCase();
    return children.filter(c => c.name.toLowerCase().includes(q));
  };

  // ── Merchant helpers (flex mode) ──────────────────────────────────────────

  const filteredMerchants = flexMerchQuery.trim()
    ? merchants.filter(m => m.name.toLowerCase().includes(flexMerchQuery.toLowerCase()))
    : merchants;

  const handleCreateMerchant = async (name: string) => {
    const cleanName = name.trim();
    if (!cleanName) return;
    try {
      const created = await CatalogService.createMerchant(accessToken, cleanName);
      setMerchants(prev => [...prev, created]);
      if (openMerchRowId !== null) {
        updateFlexRow(openMerchRowId, { merchantId: created.id });
      }
      setOpenMerchRowId(null);
      setFlexMerchQuery('');
    } catch (err) {
      const message = axios.isAxiosError(err) ? (err.response?.data?.message || err.message) : 'Error';
      alert(`Error al crear comerciante: ${message}`);
    }
  };

  // ── Tag helpers (flex mode) ───────────────────────────────────────────────

  const filteredTags = flexTagQuery.trim()
    ? tags.filter(t => t.name.toLowerCase().includes(flexTagQuery.toLowerCase()))
    : tags;

  const handleCreateTag = async (name: string) => {
    const cleanName = name.trim();
    if (!cleanName) return;
    try {
      const created = await CatalogService.createTag(accessToken, cleanName);
      setTags(prev => [...prev, created]);
      if (openTagRowId !== null) {
        updateFlexRow(openTagRowId, { tagIds: [...(flexRows.find(r => r.id === openTagRowId)?.tagIds ?? []), created.id] });
      }
      setFlexTagQuery('');
    } catch (err) {
      const message = axios.isAxiosError(err) ? (err.response?.data?.message || err.message) : 'Error';
      alert(`Error al crear etiqueta: ${message}`);
    }
  };

  // ── Mass row helpers ──────────────────────────────────────────────────────

  const updateMassRow = useCallback((id: number, field: keyof Omit<MassRow, 'id'>, value: string) => {
    setMassRows(prev => prev.map(r => r.id === id ? { ...r, [field]: value } : r));
  }, []);

  const deleteMassRow = useCallback((id: number) => {
    setMassRows(prev => {
      const next = prev.filter(r => r.id !== id);
      return next.length ? next : [makeBlankMassRow()];
    });
  }, []);

  const addBlankMassRow = useCallback(() => {
    setMassRows(prev => {
      const lastDate = prev[prev.length - 1]?.date;
      const newRow = makeBlankMassRow(lastDate);
      setTimeout(() => lastNetRef.current?.focus(), 50);
      return [...prev, newRow];
    });
  }, []);

  const addRangeRows = useCallback(() => {
    if (!rangeFrom || !rangeTo) return;
    const from = new Date(rangeFrom + 'T00:00:00');
    const to   = new Date(rangeTo   + 'T00:00:00');
    if (from > to) return;
    const newRows: MassRow[] = [];
    let cur = toDateStr(from);
    while (new Date(cur + 'T00:00:00') <= to) {
      newRows.push(makeBlankMassRow(cur));
      cur = addDays(cur, 1);
    }
    setMassRows(prev => {
      const base = prev.length === 1 && prev[0].net === '' && prev[0].tax === '' ? [] : prev;
      return [...base, ...newRows];
    });
  }, [rangeFrom, rangeTo]);

  // ── Flex row helpers ──────────────────────────────────────────────────────

  const updateFlexRow = useCallback((id: number, patch: Partial<Omit<FlexRow, 'id'>>) => {
    setFlexRows(prev => prev.map(r => r.id === id ? { ...r, ...patch } : r));
  }, []);

  const deleteFlexRow = useCallback((id: number) => {
    setFlexRows(prev => {
      const next = prev.filter(r => r.id !== id);
      return next.length ? next : [makeBlankFlexRow()];
    });
  }, []);

  const addBlankFlexRow = useCallback(() => {
    setFlexRows(prev => {
      const lastDate = prev[prev.length - 1]?.date;
      return [...prev, makeBlankFlexRow(lastDate)];
    });
  }, []);

  const toggleFlexRowTag = useCallback((rowId: number, tagId: number) => {
    setFlexRows(prev => prev.map(r => {
      if (r.id !== rowId) return r;
      const has = r.tagIds.includes(tagId);
      return { ...r, tagIds: has ? r.tagIds.filter(t => t !== tagId) : [...r.tagIds, tagId] };
    }));
  }, []);

  // ── Totals ────────────────────────────────────────────────────────────────

  const activeRows     = mode === 'mass' ? massRows : flexRows;
  const totalNet       = activeRows.reduce((s, r) => s + parseNum(r.net), 0);
  const totalTax       = activeRows.reduce((s, r) => s + parseNum(r.tax), 0);
  const totalGross     = activeRows.reduce((s, r) => s + grossOf(r.net, r.tax), 0);
  const validMassRows  = massRows.filter(r => r.date && parseNum(r.net) !== 0);
  const validFlexRows  = flexRows.filter(r => r.date && parseNum(r.net) !== 0);
  const validRowCount  = mode === 'mass' ? validMassRows.length : validFlexRows.length;

  // ── Type ID helper ────────────────────────────────────────────────────────

  const getTypeId = (net: number) =>
    net > 0
      ? txnTypes.find(t => t.name.toLowerCase().includes('income') || t.name.toLowerCase().includes('ingreso'))?.id
      : txnTypes.find(t => t.name.toLowerCase().includes('expense') || t.name.toLowerCase().includes('gasto'))?.id;

  const getTransferTypeId = () =>
    txnTypes.find(t => t.name.toLowerCase().includes('transfer') || t.name.toLowerCase().includes('transferencia'))?.id;

  // ── Save all ──────────────────────────────────────────────────────────────

  const handleSave = async () => {
    if (!accountId || validRowCount === 0) return;
    setSaving(true);
    setResultMsg(null);

    const rowsToSave = mode === 'mass' ? validMassRows : validFlexRows;
    let saved = 0, failed = 0;
    const errors: string[] = [];

    for (let i = 0; i < rowsToSave.length; i++) {
      const row = rowsToSave[i];
      const net = parseNum(row.net);
      const tax = parseNum(row.tax);

      setProgress(Math.round((i / rowsToSave.length) * 100));
      setProgressLabel(`Guardando ${i + 1} de ${rowsToSave.length}…`);

      try {
        const typeId = getTypeId(net);
        const flexRow = row as FlexRow;

        const created = await createTransaction(accessToken, {
          sourceAccountId: accountId as number,
          bookingDate: row.date + 'T00:00:00',
          amount: net,
          currency: selectedAccount?.currency ?? 'EUR',
          typeId,
          description: mode === 'mass'
            ? (description.trim() || undefined)
            : (flexRow.description.trim() || undefined),
          categoryId: mode === 'mass'
            ? (categoryId !== '' ? (categoryId as number) : undefined)
            : (flexRow.categoryId !== '' ? (flexRow.categoryId as number) : undefined),
          merchantId: mode === 'flex' && flexRow.merchantId !== ''
            ? (flexRow.merchantId as number)
            : undefined,
          tagIds: mode === 'flex' && flexRow.tagIds.length > 0 ? flexRow.tagIds : undefined,
        });

        const rowTaxTypeId = mode === 'mass' ? taxTypeId : flexRow.taxTypeId;
        if (tax > 0 && rowTaxTypeId !== '' && created.id != null) {
          await CatalogService.saveTransactionTax(accessToken, created.id, {
            grossAmount: net + tax,
            taxAmount: tax,
            taxTypeId: rowTaxTypeId as number,
            notes: '',
          });
        }

        // ── Rounding transfer
        if (roundingEnabled && roundingAccountId !== '') {
          const absNet = Math.abs(net);
          const roundUpAmount = parseFloat((Math.ceil(absNet) - absNet).toFixed(2));
          if (roundUpAmount >= 0.01) {
            await createTransaction(accessToken, {
              sourceAccountId: accountId as number,
              destinationAccountId: roundingAccountId as number,
              bookingDate: row.date + 'T00:00:00',
              amount: -roundUpAmount,
              currency: selectedAccount?.currency ?? 'EUR',
              typeId: getTransferTypeId(),
              description: 'Redondeo automático',
            });
          }
        }

        saved++;
      } catch (err: unknown) {
        failed++;
        let msg = `Fila ${i + 1} (${row.date}): error desconocido`;
        if (axios.isAxiosError(err)) {
          msg = `Fila ${i + 1} (${row.date}): ${err.response?.data?.message || err.message}`;
        }
        errors.push(msg);
      }
    }

    setProgress(100);
    setSaving(false);
    dispatchFinanceEvent(FINANCE_EVENTS.TRANSACTIONS_UPDATED);

    if (failed === 0) {
      setResultMsg({ type: 'success', text: `✓ ${saved} transacciones guardadas correctamente.` });
      if (mode === 'mass') setMassRows([makeBlankMassRow()]);
      else setFlexRows([makeBlankFlexRow()]);
    } else {
      setResultMsg({
        type: 'error',
        text: `${saved} guardadas, ${failed} fallidas: ${errors.slice(0, 3).join(' | ')}${errors.length > 3 ? ` (+${errors.length - 3} más)` : ''}`,
      });
    }
  };

  // ── Escape key ────────────────────────────────────────────────────────────
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !saving) {
        // Close open overlays first, then modal
        if (categoryOpen) { setCategoryOpen(false); return; }
        if (openCatRowId !== null) { setOpenCatRowId(null); return; }
        if (openMerchRowId !== null) { setOpenMerchRowId(null); return; }
        if (openTagRowId !== null) { setOpenTagRowId(null); return; }
        onClose();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [saving, onClose, categoryOpen, openCatRowId, openMerchRowId, openTagRowId]);

  // ── Render helpers ────────────────────────────────────────────────────────

  const canSave = accountId !== '' && validRowCount > 0 && !saving;

  const renderAccountDropdown = () => (
    <div className="btm-field btm-field--account">
      <label>Cuenta <span className="btm-required">*</span></label>
      <div className="btm-dropdown" ref={accountRef}>
        <button
          type="button"
          className={`btm-dropdown-trigger${accountOpen ? ' open' : ''}${!selectedAccount ? ' placeholder' : ''}`}
          onClick={() => {
            if (saving) return;
            if (!accountOpen) {
              const rect = accountRef.current?.getBoundingClientRect();
              if (rect) setAccountDropdownPos({ top: rect.bottom + 4, left: rect.left, width: rect.width });
            }
            setAccountOpen(v => !v);
          }}
          disabled={saving}
        >
          {selectedAccount ? (
            <span className="btm-acc-option">
              <InstLogo name={selectedAccount.institutionName} />
              <span className="btm-acc-info">
                <span className="btm-acc-name">{selectedAccount.name}</span>
                <span className="btm-acc-bank">{selectedAccount.institutionName || 'Sin banco'}</span>
              </span>
            </span>
          ) : (
            <span className="btm-placeholder">Selecciona cuenta…</span>
          )}
          <span className="btm-dropdown-arrow">{accountOpen ? '▲' : '▼'}</span>
        </button>
      </div>
    </div>
  );

  const renderRoundingConfig = () => {
    const roundingAccount = accounts.find(a => a.id === roundingAccountId);
    return (
      <div className="btm-rounding-config">
        <label className="btm-rounding-toggle">
          <input
            type="checkbox"
            checked={roundingEnabled}
            onChange={e => { setRoundingEnabled(e.target.checked); if (!e.target.checked) setRoundingAccountId(''); }}
            disabled={saving}
          />
          <span>🪙 Redondeo</span>
        </label>
        {roundingEnabled && (
          <div className="btm-rounding-account">
            <span className="btm-rounding-account-label">→</span>
            <div className="btm-dropdown btm-dropdown--sm" ref={roundingAccountRef}>
              <button
                type="button"
                className={`btm-dropdown-trigger btm-dropdown-trigger--sm${roundingAccountOpen ? ' open' : ''}${!roundingAccount ? ' placeholder' : ''}`}
                onClick={() => {
                  if (saving) return;
                  if (!roundingAccountOpen) {
                    const rect = roundingAccountRef.current?.getBoundingClientRect();
                    if (rect) setRoundingAccountDropdownPos({ top: rect.bottom + 4, left: rect.left, width: Math.max(rect.width, 260) });
                  }
                  setRoundingAccountOpen(v => !v);
                }}
                disabled={saving}
              >
                {roundingAccount ? (
                  <span className="btm-acc-option">
                    <InstLogo name={roundingAccount.institutionName} />
                    <span className="btm-acc-info">
                      <span className="btm-acc-name">{roundingAccount.name}</span>
                    </span>
                  </span>
                ) : (
                  <span className="btm-placeholder">Cuenta destino…</span>
                )}
                <span className="btm-dropdown-arrow">{roundingAccountOpen ? '▲' : '▼'}</span>
              </button>
            </div>
          </div>
        )}
      </div>
    );
  };

  const renderMassCategoryDropdown = () => (
    <div className="btm-field btm-field--wide">
      <label>Categoría</label>
      <button
        type="button"
        className={`btm-cell-picker-btn${selectedCategory ? ' has-value' : ''}`}
        style={{ minWidth: 160 }}
        onClick={() => !saving && (setCategoryOpen(true), setMassCatSearch(''))}
        disabled={saving}
      >
        {selectedCategory ? (
          <>
            <span
              className="btm-cell-cat-emoji"
              style={{ background: catVisual(selectedCategory).color + '22', color: catVisual(selectedCategory).color }}
            >
              {catVisual(selectedCategory).emoji}
            </span>
            <span className="btm-cell-picker-label">
              {selectedCategory.parentName
                ? <><span style={{ opacity: 0.6 }}>{selectedCategory.parentName}</span> › </>
                : null}
              {selectedCategory.name}
            </span>
          </>
        ) : (
          <span className="btm-cell-picker-placeholder">Sin categoría</span>
        )}
      </button>
    </div>
  );

  // ── Render ────────────────────────────────────────────────────────────────

  const activeFlexRowForCat   = flexRows.find(r => r.id === openCatRowId);
  const activeFlexRowForMerch = flexRows.find(r => r.id === openMerchRowId);
  const activeFlexRowForTags  = flexRows.find(r => r.id === openTagRowId);

  return (
    <>
    <div className="btm-overlay" onClick={e => { if (e.target === e.currentTarget && !saving) onClose(); }}>
      <div className={`btm-modal${mode === 'flex' ? ' btm-modal--flex' : ''}`} style={{ position: 'relative' }}>

        {/* Progress overlay */}
        {saving && (
          <div className="btm-progress-overlay">
            <div className="btm-progress-label">{progressLabel}</div>
            <div className="btm-progress-bar-outer">
              <div className="btm-progress-bar-inner" style={{ width: `${progress}%` }} />
            </div>
          </div>
        )}

        {/* Header */}
        <div className="btm-header">
          <div className="btm-header-inner">
            <div className="btm-header-title">
              <span style={{ fontSize: 24 }}>📋</span>
              <h2>Asistente de entrada en lote</h2>
            </div>
            <div className="btm-header-sub">Añade múltiples transacciones a la vez</div>
          </div>

          <div className="btm-mode-tabs">
            <button
              className={`btm-mode-tab${mode === 'mass' ? ' active' : ''}`}
              onClick={() => !saving && setMode('mass')}
              title="Mismo concepto para todas las filas"
            >
              📊 Masivo
            </button>
            <button
              className={`btm-mode-tab${mode === 'flex' ? ' active' : ''}`}
              onClick={() => !saving && setMode('flex')}
              title="Cada fila con sus propios campos"
            >
              ✏️ Personalizado
            </button>
          </div>

          <button className="btm-close" onClick={() => !saving && onClose()} title="Cerrar">✕</button>
        </div>

        {/* Result banner */}
        {resultMsg && (
          <div className={`btm-result-banner ${resultMsg.type}`}>{resultMsg.text}</div>
        )}

        {/* ══════════════════════ MASS MODE ══════════════════════ */}
        {mode === 'mass' && (
          <>
            <div className="btm-config">
              {renderAccountDropdown()}
              <div className="btm-field btm-field--grow">
                <label>Descripción (compartida)</label>
                <input
                  type="text"
                  className="btm-input"
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  placeholder="Ej. Intereses Revolut"
                  disabled={saving}
                />
              </div>
              {renderMassCategoryDropdown()}
              <div className="btm-field">
                <label>Tipo retención</label>
                <select
                  className="btm-select"
                  value={taxTypeId}
                  onChange={e => setTaxTypeId(e.target.value === '' ? '' : Number(e.target.value))}
                  disabled={saving}
                >
                  <option value="">— Sin retención —</option>
                  {taxTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              </div>
              {renderRoundingConfig()}
            </div>

            <div className="btm-date-helper">
              <span className="btm-date-helper-label">📅 Añadir una fila por día:</span>
              <input type="date" className="btm-date-input" value={rangeFrom} onChange={e => setRangeFrom(e.target.value)} disabled={saving} title="Desde" />
              <span className="btm-date-helper-sep">→</span>
              <input type="date" className="btm-date-input" value={rangeTo} onChange={e => setRangeTo(e.target.value)} disabled={saving} title="Hasta" />
              <button className="btm-btn-add-range" onClick={addRangeRows} disabled={!rangeFrom || !rangeTo || saving}>
                + Añadir rango
              </button>
              {rangeFrom && rangeTo && new Date(rangeFrom) <= new Date(rangeTo) && (
                <span className="btm-date-range-count">
                  {Math.round((new Date(rangeTo).getTime() - new Date(rangeFrom).getTime()) / 86400000) + 1} días
                </span>
              )}
            </div>

            <div className="btm-table-area">
              <table className="btm-table">
                <thead>
                  <tr>
                    <th className="col-num">#</th>
                    <th className="col-date">Fecha</th>
                    <th className="col-net">Neto (€)</th>
                    <th className="col-tax">Retención (€)</th>
                    <th className="col-gross">Bruto (auto)</th>
                    <th className="col-del"></th>
                  </tr>
                </thead>
                <tbody>
                  {massRows.length === 0 ? (
                    <tr className="btm-row--empty"><td colSpan={6}>Sin filas. Usa el rango o añade una fila.</td></tr>
                  ) : massRows.map((row, idx) => {
                    const gross  = grossOf(row.net, row.tax);
                    const isLast = idx === massRows.length - 1;
                    return (
                      <tr key={row.id}>
                        <td className="btm-row-num">{idx + 1}</td>
                        <td><input type="date" className="btm-cell-input btm-cell-input--date" value={row.date} onChange={e => updateMassRow(row.id, 'date', e.target.value)} disabled={saving} /></td>
                        <td><input type="number" className="btm-cell-input" value={row.net} onChange={e => updateMassRow(row.id, 'net', e.target.value)} placeholder="0.00" step="0.01" disabled={saving} ref={isLast ? lastNetRef : undefined} /></td>
                        <td><input type="number" className="btm-cell-input" value={row.tax} onChange={e => updateMassRow(row.id, 'tax', e.target.value)} placeholder="0.00" step="0.01" disabled={saving} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addBlankMassRow(); } }} /></td>
                        <td><span className={`btm-cell-gross${gross === 0 ? ' zero' : ''}`}>{gross !== 0 ? fmtEur(gross) : '—'}</span></td>
                        <td><button className="btm-btn-del-row" onClick={() => deleteMassRow(row.id)} disabled={saving} title="Eliminar fila">✕</button></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}

        {/* ══════════════════════ FLEX MODE ══════════════════════ */}
        {mode === 'flex' && (
          <>
            <div className="btm-config btm-config--flex">
              {renderAccountDropdown()}
              {renderRoundingConfig()}
              <div className="btm-config-hint">
                <span>✏️</span>
                <span>Modo personalizado: cada fila tiene su propia fecha, descripción, categoría, comercio, tags e importes.</span>
              </div>
            </div>

            <div className="btm-table-area btm-table-area--flex">
              <table className="btm-table btm-table--flex">
                <thead>
                  <tr>
                    <th className="col-num">#</th>
                    <th className="col-date">Fecha</th>
                    <th className="col-desc">Descripción</th>
                    <th className="col-cat">Categoría</th>
                    <th className="col-merch">Comercio</th>
                    <th className="col-tags">Tags</th>
                    <th className="col-net">Neto (€)</th>
                    <th className="col-taxtype">Tipo ret.</th>
                    <th className="col-tax">Ret. (€)</th>
                    <th className="col-gross">Bruto</th>
                    <th className="col-del"></th>
                  </tr>
                </thead>
                <tbody>
                  {flexRows.map((row, idx) => {
                    const gross   = grossOf(row.net, row.tax);
                    const rowCat  = categories.find(c => c.id === row.categoryId);
                    const cv      = catVisual(rowCat);
                    const rowMrch = merchants.find(m => m.id === row.merchantId);
                    return (
                      <tr key={row.id}>
                        <td className="btm-row-num">{idx + 1}</td>

                        {/* Date */}
                        <td>
                          <input type="date" className="btm-cell-input btm-cell-input--date" value={row.date}
                            onChange={e => updateFlexRow(row.id, { date: e.target.value })} disabled={saving} />
                        </td>

                        {/* Description */}
                        <td>
                          <input type="text" className="btm-cell-input btm-cell-input--desc" value={row.description}
                            onChange={e => updateFlexRow(row.id, { description: e.target.value })}
                            placeholder="Descripción…" disabled={saving} />
                        </td>

                        {/* Category — trigger opens fixed overlay */}
                        <td>
                          <button
                            type="button"
                            className={`btm-cell-picker-btn${rowCat ? ' has-value' : ''}`}
                            onClick={() => { if (!saving) { setOpenCatRowId(row.id); setFlexCatSearch(''); } }}
                            disabled={saving}
                          >
                            {rowCat ? (
                              <>
                                <span className="btm-cell-cat-emoji" style={{ background: cv.color + '18', color: cv.color }}>{cv.emoji}</span>
                                <span className="btm-cell-picker-label">
                                  {rowCat.parentName ? `${rowCat.parentName} › ` : ''}{rowCat.name}
                                </span>
                              </>
                            ) : <span className="btm-cell-picker-placeholder">— categoría</span>}
                          </button>
                        </td>

                        {/* Merchant — trigger opens fixed overlay */}
                        <td>
                          {rowMrch ? (
                            <div className="btm-cell-chip">
                              <span className="btm-cell-chip-logo">{getMerchantLogo(rowMrch.name)}</span>
                              <span className="btm-cell-chip-label">{rowMrch.name}</span>
                              <button type="button" className="btm-cell-chip-clear"
                                onClick={() => updateFlexRow(row.id, { merchantId: '' })}>×</button>
                            </div>
                          ) : (
                            <button
                              type="button"
                              className="btm-cell-picker-btn"
                              onClick={() => { if (!saving) { setOpenMerchRowId(row.id); setFlexMerchQuery(''); } }}
                              disabled={saving}
                            >
                              <span className="btm-cell-picker-placeholder">— comercio</span>
                            </button>
                          )}
                        </td>

                        {/* Tags — trigger opens fixed overlay */}
                        <td>
                          <button
                            type="button"
                            className={`btm-cell-picker-btn btm-cell-tags-btn${row.tagIds.length > 0 ? ' has-value' : ''}`}
                            onClick={() => { if (!saving) { setOpenTagRowId(row.id); setFlexTagQuery(''); } }}
                            disabled={saving}
                          >
                            {row.tagIds.length === 0 ? (
                              <span className="btm-cell-picker-placeholder">— tags</span>
                            ) : (
                              <div className="btm-cell-tag-chips">
                                {row.tagIds.map(tid => {
                                  const t = tags.find(x => x.id === tid);
                                  return t ? <span key={tid} className="btm-tag-chip">{t.name}</span> : null;
                                })}
                              </div>
                            )}
                          </button>
                        </td>

                        {/* Net */}
                        <td>
                          <input type="number" className="btm-cell-input" value={row.net}
                            onChange={e => updateFlexRow(row.id, { net: e.target.value })}
                            placeholder="0.00" step="0.01" disabled={saving} />
                        </td>

                        {/* Tax type */}
                        <td>
                          <select className="btm-cell-select" value={row.taxTypeId}
                            onChange={e => updateFlexRow(row.id, { taxTypeId: e.target.value === '' ? '' : Number(e.target.value) })}
                            disabled={saving}>
                            <option value="">—</option>
                            {taxTypes.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                          </select>
                        </td>

                        {/* Tax amount */}
                        <td>
                          <input type="number" className="btm-cell-input" value={row.tax}
                            onChange={e => updateFlexRow(row.id, { tax: e.target.value })}
                            placeholder="0.00" step="0.01" disabled={saving}
                            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addBlankFlexRow(); } }} />
                        </td>

                        {/* Gross */}
                        <td>
                          <span className={`btm-cell-gross${gross === 0 ? ' zero' : ''}`}>
                            {gross !== 0 ? fmtEur(gross) : '—'}
                          </span>
                        </td>

                        {/* Delete */}
                        <td>
                          <button className="btm-btn-del-row" onClick={() => deleteFlexRow(row.id)} disabled={saving} title="Eliminar fila">✕</button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}

        {/* Footer (shared) */}
        <div className="btm-footer">
          <div className="btm-footer-stats">
            <div className="btm-stat">
              <span className="btm-stat-label">Filas válidas</span>
              <span className="btm-stat-value">{validRowCount} / {activeRows.length}</span>
            </div>
            <div className="btm-stat">
              <span className="btm-stat-label">Total neto</span>
              <span className={`btm-stat-value ${totalNet >= 0 ? 'income' : 'expense'}`}>{fmtEur(totalNet)}</span>
            </div>
            <div className="btm-stat">
              <span className="btm-stat-label">Total retención</span>
              <span className="btm-stat-value">{fmtEur(totalTax)}</span>
            </div>
            <div className="btm-stat">
              <span className="btm-stat-label">Total bruto</span>
              <span className={`btm-stat-value ${totalGross >= 0 ? 'income' : 'expense'}`}>{fmtEur(totalGross)}</span>
            </div>
          </div>
          <div className="btm-footer-actions">
            <button className="btm-btn-add-row" onClick={mode === 'mass' ? addBlankMassRow : addBlankFlexRow} disabled={saving}>
              + Añadir fila
            </button>
            <button className="btm-btn-cancel" onClick={() => !saving && onClose()} disabled={saving}>Cancelar</button>
            <button className="btm-btn-save" onClick={handleSave} disabled={!canSave}>
              {saving ? 'Guardando…' : `Guardar todas (${validRowCount})`}
            </button>
          </div>
        </div>
      </div>
    </div>

    {/* ══════════════════════ FLEX OVERLAYS (fixed, outside modal) ══════════════════════ */}

    {/* Mass mode category picker overlay */}
    {categoryOpen && (
      <div className="category-picker-overlay" style={{ zIndex: 1600 }} onClick={() => setCategoryOpen(false)}>
        <div className="category-picker-dialog" onClick={e => e.stopPropagation()}>
          <div className="category-picker-header">
            <span>Seleccionar categoría</span>
            <button type="button" className="category-picker-close" onClick={() => setCategoryOpen(false)}>×</button>
          </div>
          <div className="category-picker-search-bar">
            <span className="category-search-icon">🔍</span>
            <input
              className="category-search-input"
              placeholder="Filtrar categorías…"
              value={massCatSearch}
              onChange={e => setMassCatSearch(e.target.value)}
              autoFocus
            />
            {massCatSearch && (
              <button type="button" className="category-search-clear" onClick={() => setMassCatSearch('')}>×</button>
            )}
          </div>
          <div className="category-picker-list">
            <div
              className={`category-none-item${categoryId === '' ? ' selected' : ''}`}
              onClick={() => { setCategoryId(''); setCategoryOpen(false); }}
            >
              Sin categoría
            </div>
            <div className="category-picker-grid">
              {filteredParentsForPicker(massCatSearch).map(parent => {
                const pv = catVisual(parent);
                const children = getVisibleChildren(parent.id, massCatSearch);
                const totalChildren = (childrenByParent[parent.id] || []).length;
                return (
                  <div key={parent.id} className="category-group">
                    <div
                      className={`category-parent-row${categoryId === parent.id ? ' selected' : ''}`}
                      onClick={() => { setCategoryId(parent.id); setCategoryOpen(false); setMassCatSearch(''); }}
                    >
                      <span className="cat-emoji">{pv.emoji}</span>
                      <span className="cat-name">{parent.name}</span>
                      {totalChildren > 0 && <span className="cat-count">{totalChildren}</span>}
                    </div>
                    {children.map(child => {
                      const cv = catVisual(child);
                      return (
                        <div
                          key={child.id}
                          className={`category-child-row${categoryId === child.id ? ' selected' : ''}`}
                          onClick={() => { setCategoryId(child.id); setCategoryOpen(false); setMassCatSearch(''); }}
                        >
                          <span className="cat-emoji">{cv.emoji}</span>
                          <span className="cat-name">{child.name}</span>
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
            {filteredParentsForPicker(massCatSearch).length === 0 && (
              <div className="category-no-results">Sin resultados para "{massCatSearch}"</div>
            )}
          </div>
        </div>
      </div>
    )}

    {/* Category picker overlay */}
    {openCatRowId !== null && (
      <div className="category-picker-overlay" style={{ zIndex: 1600 }} onClick={() => setOpenCatRowId(null)}>
        <div className="category-picker-dialog" onClick={e => e.stopPropagation()}>
          <div className="category-picker-header">
            <span>Seleccionar categoría</span>
            <button type="button" className="category-picker-close" onClick={() => setOpenCatRowId(null)}>×</button>
          </div>
          <div className="category-picker-search-bar">
            <span className="category-search-icon">🔍</span>
            <input
              className="category-search-input"
              placeholder="Filtrar categorías…"
              value={flexCatSearch}
              onChange={e => setFlexCatSearch(e.target.value)}
              autoFocus
            />
            {flexCatSearch && (
              <button type="button" className="category-search-clear" onClick={() => setFlexCatSearch('')}>×</button>
            )}
          </div>
          <div className="category-picker-list">
            <div
              className={`category-none-item${activeFlexRowForCat?.categoryId === '' ? ' selected' : ''}`}
              onClick={() => { updateFlexRow(openCatRowId, { categoryId: '' }); setOpenCatRowId(null); }}
            >
              Sin categoría
            </div>
            <div className="category-picker-grid">
              {filteredParentsForPicker(flexCatSearch).map(parent => {
                const pv = catVisual(parent);
                const children = getVisibleChildren(parent.id, flexCatSearch);
                const totalChildren = (childrenByParent[parent.id] || []).length;
                return (
                  <div key={parent.id} className="category-group">
                    <div
                      className={`category-parent-row${activeFlexRowForCat?.categoryId === parent.id ? ' selected' : ''}`}
                      onClick={() => { updateFlexRow(openCatRowId, { categoryId: parent.id }); setOpenCatRowId(null); setFlexCatSearch(''); }}
                    >
                      <span className="cat-emoji">{pv.emoji}</span>
                      <span className="cat-name">{parent.name}</span>
                      {totalChildren > 0 && <span className="cat-count">{totalChildren}</span>}
                    </div>
                    {children.map(child => {
                      const cv = catVisual(child);
                      return (
                        <div
                          key={child.id}
                          className={`category-child-row${activeFlexRowForCat?.categoryId === child.id ? ' selected' : ''}`}
                          onClick={() => { updateFlexRow(openCatRowId, { categoryId: child.id }); setOpenCatRowId(null); setFlexCatSearch(''); }}
                        >
                          <span className="cat-emoji">{cv.emoji}</span>
                          <span className="cat-name">{child.name}</span>
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
            {filteredParentsForPicker(flexCatSearch).length === 0 && (
              <div className="category-no-results">Sin resultados para "{flexCatSearch}"</div>
            )}
          </div>
        </div>
      </div>
    )}

    {/* Merchant picker overlay */}
    {openMerchRowId !== null && (
      <div className="btm-flex-overlay" style={{ zIndex: 1600 }} onClick={() => setOpenMerchRowId(null)}>
        <div className="btm-flex-dialog" onClick={e => e.stopPropagation()}>
          <div className="btm-flex-dialog-header">
            <span>🏪 Seleccionar comercio</span>
            <button type="button" className="category-picker-close" onClick={() => setOpenMerchRowId(null)}>×</button>
          </div>
          <div className="category-picker-search-bar">
            <span className="category-search-icon">🔍</span>
            <input
              className="category-search-input"
              placeholder="Buscar comercio…"
              value={flexMerchQuery}
              onChange={e => setFlexMerchQuery(e.target.value)}
              autoFocus
            />
            {flexMerchQuery && (
              <button type="button" className="category-search-clear" onClick={() => setFlexMerchQuery('')}>×</button>
            )}
          </div>
          <div className="btm-flex-dialog-list">
            {/* Clear option */}
            <div
              className={`btm-flex-dialog-item${activeFlexRowForMerch?.merchantId === '' ? ' selected' : ''}`}
              onClick={() => { updateFlexRow(openMerchRowId, { merchantId: '' }); setOpenMerchRowId(null); setFlexMerchQuery(''); }}
            >
              <span>—</span>
              <span>Sin comercio</span>
            </div>
            {filteredMerchants.slice(0, 30).map(m => (
              <div
                key={m.id}
                className={`btm-flex-dialog-item${activeFlexRowForMerch?.merchantId === m.id ? ' selected' : ''}`}
                onClick={() => { updateFlexRow(openMerchRowId, { merchantId: m.id }); setOpenMerchRowId(null); setFlexMerchQuery(''); }}
              >
                <span className="btm-merch-logo">{getMerchantLogo(m.name)}</span>
                <span>{m.name}</span>
              </div>
            ))}
            {flexMerchQuery.trim() && filteredMerchants.length === 0 && (
              <>
                <div className="btm-flex-dialog-noresults">Sin resultados para "{flexMerchQuery}"</div>
                <div
                  className="btm-flex-dialog-item btm-flex-dialog-item--create"
                  onClick={() => handleCreateMerchant(flexMerchQuery.trim())}
                >
                  ➕ Crear "{flexMerchQuery.trim()}"
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    )}

    {/* Account dropdown — fixed, escapes modal overflow:hidden */}
    {accountOpen && accountDropdownPos && (
      <div
        className="btm-account-dropdown-fixed"
        style={{ top: accountDropdownPos.top, left: accountDropdownPos.left, width: accountDropdownPos.width }}
        onMouseDown={e => { e.preventDefault(); e.stopPropagation(); }}
      >
        {accounts.map(a => (
          <div
            key={a.id}
            className={`btm-dropdown-item${accountId === a.id ? ' selected' : ''}`}
            onClick={() => { setAccountId(a.id); setAccountOpen(false); }}
          >
            <InstLogo name={a.institutionName} />
            <span className="btm-acc-info">
              <span className="btm-acc-name">{a.name}</span>
              <span className="btm-acc-bank">{a.institutionName || 'Sin banco'}</span>
            </span>
          </div>
        ))}
      </div>
    )}

    {/* Rounding account dropdown — fixed */}
    {roundingAccountOpen && roundingAccountDropdownPos && (
      <div
        className="btm-account-dropdown-fixed"
        style={{ top: roundingAccountDropdownPos.top, left: roundingAccountDropdownPos.left, width: roundingAccountDropdownPos.width }}
        onMouseDown={e => { e.preventDefault(); e.stopPropagation(); }}
      >
        {accounts.filter(a => a.id !== accountId).map(a => (
          <div
            key={a.id}
            className={`btm-dropdown-item${roundingAccountId === a.id ? ' selected' : ''}`}
            onClick={() => { setRoundingAccountId(a.id); setRoundingAccountOpen(false); }}
          >
            <InstLogo name={a.institutionName} />
            <span className="btm-acc-info">
              <span className="btm-acc-name">{a.name}</span>
              <span className="btm-acc-bank">{a.institutionName || 'Sin banco'}</span>
            </span>
          </div>
        ))}
      </div>
    )}

    {/* Tags picker overlay */}
    {openTagRowId !== null && (
      <div className="btm-flex-overlay" style={{ zIndex: 1600 }} onClick={() => setOpenTagRowId(null)}>
        <div className="btm-flex-dialog" onClick={e => e.stopPropagation()}>
          <div className="btm-flex-dialog-header">
            <span>🏷️ Etiquetas</span>
            <button type="button" className="category-picker-close" onClick={() => setOpenTagRowId(null)}>×</button>
          </div>
          <div className="category-picker-search-bar">
            <span className="category-search-icon">🔍</span>
            <input
              className="category-search-input"
              placeholder="Buscar etiqueta…"
              value={flexTagQuery}
              onChange={e => setFlexTagQuery(e.target.value)}
              autoFocus
            />
            {flexTagQuery && (
              <button type="button" className="category-search-clear" onClick={() => setFlexTagQuery('')}>×</button>
            )}
          </div>
          <div className="btm-flex-dialog-list">
            {filteredTags.slice(0, 30).map(t => {
              const isSelected = activeFlexRowForTags?.tagIds.includes(t.id) ?? false;
              return (
                <div
                  key={t.id}
                  className={`btm-flex-dialog-item${isSelected ? ' selected' : ''}`}
                  onClick={() => toggleFlexRowTag(openTagRowId, t.id)}
                >
                  <span>🏷️</span>
                  <span style={{ flex: 1 }}>{t.name}</span>
                  {isSelected && <span className="btm-flex-check">✓</span>}
                </div>
              );
            })}
            {flexTagQuery.trim() && filteredTags.length === 0 && (
              <>
                <div className="btm-flex-dialog-noresults">Sin resultados para "{flexTagQuery}"</div>
                <div
                  className="btm-flex-dialog-item btm-flex-dialog-item--create"
                  onClick={() => handleCreateTag(flexTagQuery.trim())}
                >
                  ➕ Crear "{flexTagQuery.trim()}"
                </div>
              </>
            )}
            {!flexTagQuery.trim() && tags.length === 0 && (
              <div className="btm-flex-dialog-noresults">No hay etiquetas disponibles</div>
            )}
          </div>
          {activeFlexRowForTags && activeFlexRowForTags.tagIds.length > 0 && (
            <div className="btm-flex-dialog-footer">
              <div className="btm-cell-tag-chips">
                {activeFlexRowForTags.tagIds.map(tid => {
                  const t = tags.find(x => x.id === tid);
                  return t ? (
                    <span key={tid} className="btm-tag-chip btm-tag-chip--removable" onClick={() => toggleFlexRowTag(openTagRowId, tid)}>
                      {t.name} ×
                    </span>
                  ) : null;
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    )}
    </>
  );
}
