import React, { useMemo, useState, useEffect, useCallback, useRef } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, PieChart, Pie, Cell, Legend,
} from 'recharts';
import { CatalogService } from '../services/catalogService';
import { fetchTransactions } from '../services/transactionsService';
import type { Account, Tag, TransactionCategory } from '../services/catalogService';
import type { Transaction } from '../types/banking';
import { getCategoryVisual } from '../constants/visualConfig';
import { dispatchNavigateToTransaction } from '../events/financeEvents';
import './TagsPanel.css';

// ─── Props ────────────────────────────────────────────────────────────────────

interface TagsPanelProps {
  token: string;
  onUnauthorized?: (msg: string) => void;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const fmt = (n: number) =>
  n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });

const fmtFull = (n: number) =>
  n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });

const fmtTransactionAmount = (amount?: number, currency?: string) => {
  if (amount == null) return '—';
  return currency && /^[A-Z]{3}$/.test(currency)
    ? amount.toLocaleString('es-ES', { style: 'currency', currency })
    : amount.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

const fmtTransactionDate = (value?: string) => {
  if (!value) return '—';
  const [year, month, day] = value.slice(0, 10).split('-').map(Number);
  if (!year || !month || !day) return '—';
  return new Date(year, month - 1, day).toLocaleDateString('es-ES');
};

const normalizeSearch = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('es');

const compareTagNames = (left: Tag, right: Tag) => {
  const leftYear = left.name.match(/^(\d{4})(?=[_\s-]|$)/)?.[1];
  const rightYear = right.name.match(/^(\d{4})(?=[_\s-]|$)/)?.[1];
  if (leftYear && rightYear && leftYear !== rightYear) return Number(rightYear) - Number(leftYear);
  if (leftYear && !rightYear) return -1;
  if (!leftYear && rightYear) return 1;
  return left.name.localeCompare(right.name, 'es', { sensitivity: 'base', numeric: true });
};

function monthLabel(ym: string) {
  const [year, month] = ym.split('-');
  const date = new Date(Number(year), Number(month) - 1, 1);
  return date.toLocaleDateString('es-ES', { month: 'short', year: '2-digit' });
}

const PIE_COLORS = [
  '#6366f1', '#10b981', '#f59e0b', '#ef4444', '#3b82f6',
  '#8b5cf6', '#ec4899', '#14b8a6', '#f97316', '#84cc16',
];

// ─── Custom tooltip ───────────────────────────────────────────────────────────

function BarTooltip({ active, payload, label }: { active?: boolean; payload?: { value: number; name: string }[]; label?: string }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="tp-tooltip">
      <p className="tp-tooltip__label">{label}</p>
      {payload.map((p, i) => (
        <p key={i} className="tp-tooltip__row" style={{ color: p.name === 'Gastos' ? '#ef4444' : '#10b981' }}>
          {p.name}: <strong>{fmtFull(Math.abs(p.value))}</strong>
        </p>
      ))}
    </div>
  );
}

function PieTooltip({ active, payload }: { active?: boolean; payload?: { name: string; value: number }[] }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="tp-tooltip">
      <p className="tp-tooltip__label">{payload[0].name}</p>
      <p className="tp-tooltip__row"><strong>{fmtFull(payload[0].value)}</strong></p>
    </div>
  );
}

// ─── Component ────────────────────────────────────────────────────────────────

export const TagsPanel: React.FC<TagsPanelProps> = ({ token, onUnauthorized }) => {
  const [tags, setTags] = useState<Tag[]>([]);
  const [categories, setCategories] = useState<TransactionCategory[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // CRUD state
  const [selectedTagId, setSelectedTagId] = useState<number | null>(null);
  const [tagSearch, setTagSearch] = useState('');
  const [newTagName, setNewTagName] = useState('');
  const [creating, setCreating] = useState(false);

  // Edit state
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [savingId, setSavingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  // Chart state
  const [pieMode, setPieMode] = useState<'parent' | 'child'>('parent');
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null | undefined>(undefined);
  const [transactionSearch, setTransactionSearch] = useState('');
  const [transactionPage, setTransactionPage] = useState(0);
  const [categoryDropdownOpen, setCategoryDropdownOpen] = useState(false);
  const categoryDropdownRef = useRef<HTMLDetailsElement>(null);
  const categoryFocusRef = useRef<'first' | 'last' | null>(null);

  // ── Data loading ─────────────────────────────────────────────────────────

  const loadAll = useCallback(async () => {
    if (!token) return;
    try {
      setLoading(true);
      setError(null);
      const [fetchedTags, fetchedCats, fetchedTx, fetchedAccounts] = await Promise.all([
        CatalogService.fetchTags(token),
        CatalogService.fetchCategories(token),
        fetchTransactions(token),
        CatalogService.fetchAccounts(token),
      ]);
      setTags(fetchedTags);
      setCategories(fetchedCats);
      setTransactions(fetchedTx);
      setAccounts(fetchedAccounts);
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
      if (e?.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      } else {
        setError(e?.response?.data?.message || e?.message || 'Error cargando datos');
      }
    } finally {
      setLoading(false);
    }
  }, [token, onUnauthorized]);

  useEffect(() => { loadAll(); }, [loadAll]);

  useEffect(() => {
    const closeOutside = (event: MouseEvent) => {
      const dropdown = categoryDropdownRef.current;
      if (dropdown && !dropdown.contains(event.target as Node)) setCategoryDropdownOpen(false);
    };
    document.addEventListener('mousedown', closeOutside);
    return () => document.removeEventListener('mousedown', closeOutside);
  }, []);

  useEffect(() => {
    if (!categoryDropdownOpen) return;
    categoryDropdownRef.current?.querySelector<HTMLElement>('.tp-category-menu')?.scrollIntoView?.({ block: 'nearest' });
    if (!categoryFocusRef.current) return;
    const options = categoryDropdownRef.current?.querySelectorAll<HTMLButtonElement>('.tp-category-option');
    if (options?.length) options[categoryFocusRef.current === 'first' ? 0 : options.length - 1].focus();
    categoryFocusRef.current = null;
  }, [categoryDropdownOpen]);

  // ── Derived data ─────────────────────────────────────────────────────────

  const sortedTags = useMemo(
    () => [...tags].sort(compareTagNames),
    [tags],
  );

  const filteredTags = useMemo(() => {
    const q = tagSearch.trim().toLowerCase();
    return q ? sortedTags.filter(t => t.name.toLowerCase().includes(q)) : sortedTags;
  }, [sortedTags, tagSearch]);

  const selectedTag = useMemo(
    () => tags.find(t => t.id === selectedTagId) ?? null,
    [tags, selectedTagId],
  );

  // Transactions that belong to the selected tag
  const tagTx = useMemo(() => {
    if (!selectedTagId) return [];
    return transactions.filter(tx => tx.tagIds?.includes(selectedTagId));
  }, [transactions, selectedTagId]);

  // Exclude internal transfers (both accounts set = transfer between own accounts)
  const tagTxFiltered = useMemo(() =>
    tagTx.filter(tx =>
      tx.linkedTransactionId == null &&
      !(tx.sourceAccountId != null && tx.destinationAccountId != null)
    ),
  [tagTx]);

  // Summary stats (transfers excluded)
  const stats = useMemo(() => {
    if (!tagTxFiltered.length) return { total: 0, spent: 0, income: 0, count: 0, avg: 0 };
    const spent  = tagTxFiltered.filter(t => (t.amount ?? 0) < 0).reduce((s, t) => s + (t.amount ?? 0), 0);
    const income = tagTxFiltered.filter(t => (t.amount ?? 0) > 0).reduce((s, t) => s + (t.amount ?? 0), 0);
    const total  = spent + income;
    const months = new Set(tagTxFiltered.map(t => (t.bookingDate ?? '').slice(0, 7))).size || 1;
    return { total, spent, income, count: tagTxFiltered.length, avg: spent / months };
  }, [tagTxFiltered]);

  // Monthly bar chart data (transfers excluded)
  const monthlyData = useMemo(() => {
    const map: Record<string, { gastos: number; ingresos: number }> = {};
    tagTxFiltered.forEach(tx => {
      const ym = (tx.bookingDate ?? '').slice(0, 7);
      if (!ym) return;
      if (!map[ym]) map[ym] = { gastos: 0, ingresos: 0 };
      const amt = tx.amount ?? 0;
      if (amt < 0) map[ym].gastos += Math.abs(amt);
      else         map[ym].ingresos += amt;
    });
    return Object.entries(map)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([ym, v]) => ({ month: monthLabel(ym), ...v }));
  }, [tagTxFiltered]);

  // Category map: id → parent category
  const catById = useMemo(() => Object.fromEntries(categories.map(c => [c.id, c])), [categories]);
  const accountById = useMemo(() => Object.fromEntries(accounts.map(account => [account.id, account])), [accounts]);

  const getCategoryGroupId = (categoryId?: number): number | null => {
    const category = categoryId != null ? catById[categoryId] : undefined;
    if (!category) return null;
    return pieMode === 'parent' && category.parentId && catById[category.parentId]
      ? category.parentId : category.id;
  };

  const getParentName = (catId?: number) => {
    if (!catId) return 'Sin categoría';
    const cat = catById[catId];
    if (!cat) return 'Sin categoría';
    if (!cat.parentId) return cat.name;
    return catById[cat.parentId]?.name ?? cat.name;
  };

  const getParentCode = (catId?: number) => {
    if (!catId) return 'OTH';
    const cat = catById[catId];
    if (!cat) return 'OTH';
    if (!cat.parentId) return cat.code ?? 'OTH';
    return catById[cat.parentId]?.code ?? cat.code ?? 'OTH';
  };

  const getChildName = (catId?: number) => {
    if (!catId) return 'Sin categoría';
    const category = catById[catId];
    if (!category) return 'Sin categoría';
    const parent = category.parentId ? catById[category.parentId] : undefined;
    return parent ? `${parent.name} › ${category.name}` : category.name;
  };

  const getChildCode = (catId?: number) => {
    if (!catId) return 'OTH';
    return catById[catId]?.code ?? 'OTH';
  };

  // Category breakdown table (transfers excluded, adapts to pieMode)
  const categoryBreakdown = useMemo(() => {
    const map = new Map<number | null, { id: number | null; parentName: string; parentCode: string; amount: number; count: number }>();
    tagTxFiltered
      .filter(tx => (tx.amount ?? 0) < 0)
      .forEach(tx => {
        const key = getCategoryGroupId(tx.categoryId);
        const name = pieMode === 'parent' ? getParentName(tx.categoryId) : getChildName(tx.categoryId);
        const code = pieMode === 'parent' ? getParentCode(tx.categoryId)  : getChildCode(tx.categoryId);
        const row = map.get(key) ?? { id: key, parentName: name, parentCode: code, amount: 0, count: 0 };
        row.amount += Math.abs(tx.amount ?? 0);
        row.count++;
        map.set(key, row);
      });
    return [...map.values()].sort((a, b) => b.amount - a.amount);
  }, [tagTxFiltered, categories, pieMode]); // eslint-disable-line react-hooks/exhaustive-deps

  const categoryPieData = useMemo(() => categoryBreakdown.map(row => ({ name: row.parentName, value: Math.round(row.amount * 100) / 100 })), [categoryBreakdown]);

  const transactionCategoryOptions = useMemo(() => {
    const options = new Map<number | null, string>();
    tagTx.forEach(transaction => {
      const id = getCategoryGroupId(transaction.categoryId);
      options.set(id, pieMode === 'parent' ? getParentName(transaction.categoryId) : getChildName(transaction.categoryId));
    });
    return [...options].map(([id, name]) => ({ id, name })).sort((left, right) => left.name.localeCompare(right.name, 'es'));
  }, [tagTx, categories, pieMode]); // eslint-disable-line react-hooks/exhaustive-deps

  const selectedCategory = transactionCategoryOptions.find(option => option.id === selectedCategoryId);
  const categoryVisual = (id: number | null) => getCategoryVisual(id != null ? catById[id]?.code ?? 'OTH' : 'OTH');

  const selectTransactionCategory = (id: number | null | undefined) => {
    setSelectedCategoryId(id);
    if (categoryDropdownRef.current) {
      setCategoryDropdownOpen(false);
      categoryDropdownRef.current.querySelector('summary')?.focus();
    }
  };

  const handleCategoryDropdownKey = (event: React.KeyboardEvent<HTMLDetailsElement>) => {
    const dropdown = categoryDropdownRef.current;
    if (!dropdown) return;
    if (event.key === 'Escape' && categoryDropdownOpen) {
      event.preventDefault();
      setCategoryDropdownOpen(false);
      dropdown.querySelector('summary')?.focus();
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!categoryDropdownOpen) {
        categoryFocusRef.current = event.key === 'ArrowDown' ? 'first' : 'last';
        setCategoryDropdownOpen(true);
        return;
      }
      const options = [...dropdown.querySelectorAll<HTMLButtonElement>('.tp-category-option')];
      const current = options.indexOf(document.activeElement as HTMLButtonElement);
      const next = current === -1 ? (event.key === 'ArrowDown' ? 0 : options.length - 1)
        : (current + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
      options[next]?.focus();
    }
  };

  const visibleTransactions = useMemo(() => {
    const query = normalizeSearch(transactionSearch.trim());
    return tagTx.filter(tx => {
      if (selectedCategoryId !== undefined && getCategoryGroupId(tx.categoryId) !== selectedCategoryId) return false;
      const account = tx.sourceAccountId != null ? accountById[tx.sourceAccountId] : undefined;
      const destination = tx.destinationAccountId != null ? accountById[tx.destinationAccountId] : undefined;
      const category = tx.categoryId != null ? catById[tx.categoryId] : undefined;
      return !query || [tx.description, tx.merchantName, account?.name, destination?.name, category?.name, tx.currency]
        .some(value => value && normalizeSearch(value).includes(query));
    }).sort((left, right) => (right.bookingDate ?? '').localeCompare(left.bookingDate ?? '') || (right.id ?? 0) - (left.id ?? 0));
  }, [tagTx, selectedCategoryId, transactionSearch, accountById, catById, pieMode]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    setTransactionPage(0);
  }, [selectedTagId, selectedCategoryId, transactionSearch, pieMode]);

  const pageSize = 20;
  const totalPages = Math.max(1, Math.ceil(visibleTransactions.length / pageSize));
  const currentPage = Math.min(transactionPage, totalPages - 1);
  const pagedTransactions = visibleTransactions.slice(currentPage * pageSize, (currentPage + 1) * pageSize);

  // ── CRUD handlers ─────────────────────────────────────────────────────────

  const handleCreate = async () => {
    const name = newTagName.trim();
    if (!name) return;
    try {
      setCreating(true);
      setError(null);
      const created = await CatalogService.createTag(token, name);
      setTags(prev => [...prev, created]);
      setNewTagName('');
      setSelectedTagId(created.id);
      setSelectedCategoryId(undefined);
      setTransactionSearch('');
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
      if (e?.response?.status === 401) onUnauthorized?.('Sesión expirada');
      else setError(e?.response?.data?.message || e?.message || 'Error creando tag');
    } finally {
      setCreating(false);
    }
  };

  const startEdit = (tag: Tag) => {
    setEditingId(tag.id);
    setEditDraft(tag.name);
  };

  const cancelEdit = () => { setEditingId(null); setEditDraft(''); };

  const handleSaveEdit = async (id: number) => {
    const name = editDraft.trim();
    if (!name) return;
    try {
      setSavingId(id);
      setError(null);
      const updated = await CatalogService.updateTag(token, id, name);
      setTags(prev => prev.map(t => (t.id === id ? updated : t)));
      setEditingId(null);
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
      if (e?.response?.status === 401) onUnauthorized?.('Sesión expirada');
      else setError(e?.response?.data?.message || e?.message || 'Error actualizando tag');
    } finally {
      setSavingId(null);
    }
  };

  const handleDelete = async (tag: Tag) => {
    if (!window.confirm(`¿Borrar la tag "${tag.name}"? Las transacciones asociadas perderán esta tag.`)) return;
    try {
      setDeletingId(tag.id);
      setError(null);
      await CatalogService.deleteTag(token, tag.id);
      setTags(prev => prev.filter(t => t.id !== tag.id));
      if (selectedTagId === tag.id) setSelectedTagId(null);
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { message?: string } }; message?: string };
      if (e?.response?.status === 401) onUnauthorized?.('Sesión expirada');
      else setError(e?.response?.data?.message || e?.message || 'Error borrando tag');
    } finally {
      setDeletingId(null);
    }
  };

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="tp-loading">
        <div className="tp-loading__spinner" />
        <span>Cargando tags…</span>
      </div>
    );
  }

  return (
    <div className="tp-root">
      {/* ── Left sidebar: tag list ────────────────────────────────────────── */}
      <aside className="tp-sidebar">
        <div className="tp-sidebar__header">
          <h2 className="tp-sidebar__title">Tags</h2>
          <span className="tp-sidebar__count">{tags.length}</span>
        </div>

        {/* Create new tag */}
        <form
          className="tp-create-form"
          onSubmit={e => { e.preventDefault(); handleCreate(); }}
        >
          <input
            type="text"
            className="tp-create-input"
            placeholder="Nueva tag…"
            value={newTagName}
            onChange={e => setNewTagName(e.target.value)}
            maxLength={64}
          />
          <button
            type="submit"
            className="tp-create-btn"
            disabled={creating || !newTagName.trim()}
          >
            {creating ? '…' : '+'}
          </button>
        </form>

        {/* Search */}
        <div className="tp-search-wrap">
          <span className="tp-search-icon">🔍</span>
          <input
            type="text"
            className="tp-search-input"
            placeholder="Buscar tag…"
            value={tagSearch}
            onChange={e => setTagSearch(e.target.value)}
          />
          {tagSearch && (
            <button className="tp-search-clear" onClick={() => setTagSearch('')}>✕</button>
          )}
        </div>

        {error && (
          <div className="tp-error">
            <span>{error}</span>
            <button onClick={() => setError(null)}>✕</button>
          </div>
        )}

        {/* Tag list */}
        <ul className="tp-tag-list">
          {filteredTags.length === 0 && (
            <li className="tp-tag-empty">No hay tags que coincidan</li>
          )}
          {filteredTags.map(tag => {
            const txCount = transactions.filter(tx => tx.tagIds?.includes(tag.id)).length;
            const isActive  = selectedTagId === tag.id;
            const isEditing = editingId === tag.id;

            return (
              <li
                key={tag.id}
                className={`tp-tag-item${isActive ? ' tp-tag-item--active' : ''}`}
                onClick={() => {
                  if (!isEditing && selectedTagId !== tag.id) {
                    setSelectedTagId(tag.id);
                    setSelectedCategoryId(undefined);
                    setTransactionSearch('');
                  }
                }}
              >
                {isEditing ? (
                  <div className="tp-tag-edit-row" onClick={e => e.stopPropagation()}>
                    <input
                      type="text"
                      className="tp-tag-edit-input"
                      value={editDraft}
                      autoFocus
                      onChange={e => setEditDraft(e.target.value)}
                      onKeyDown={e => {
                        if (e.key === 'Enter')  handleSaveEdit(tag.id);
                        if (e.key === 'Escape') cancelEdit();
                      }}
                      maxLength={64}
                    />
                    <button
                      className="tp-action-btn tp-action-btn--save"
                      onClick={() => handleSaveEdit(tag.id)}
                      disabled={savingId === tag.id}
                      title="Guardar"
                    >
                      {savingId === tag.id ? '…' : '✓'}
                    </button>
                    <button className="tp-action-btn tp-action-btn--cancel" onClick={cancelEdit} title="Cancelar">
                      ✕
                    </button>
                  </div>
                ) : (
                  <>
                    <div className="tp-tag-info">
                      <span className="tp-tag-pill"># {tag.name}</span>
                      <span className="tp-tag-txcount">{txCount} tx</span>
                    </div>
                    <div className="tp-tag-actions" onClick={e => e.stopPropagation()}>
                      <button
                        className="tp-action-btn tp-action-btn--edit"
                        onClick={() => startEdit(tag)}
                        title="Renombrar"
                      >
                        ✏️
                      </button>
                      <button
                        className="tp-action-btn tp-action-btn--delete"
                        onClick={() => handleDelete(tag)}
                        disabled={deletingId === tag.id}
                        title="Borrar"
                      >
                        {deletingId === tag.id ? '…' : '🗑️'}
                      </button>
                    </div>
                  </>
                )}
              </li>
            );
          })}
        </ul>
      </aside>

      {/* ── Main content: tag detail + charts ────────────────────────────── */}
      <main className="tp-main">
        {!selectedTag ? (
          <div className="tp-empty-state">
            <span className="tp-empty-state__icon">🏷️</span>
            <h3>Selecciona una tag</h3>
            <p>Elige una tag de la lista para ver sus análisis, balance y desglose por categoría.</p>
          </div>
        ) : (
          <>
            {/* Hero */}
            <header className="tp-hero">
              <div className="tp-hero__title-wrap">
                <span className="tp-hero__icon">🏷️</span>
                <div>
                  <h2 className="tp-hero__name"># {selectedTag.name}</h2>
                  <p className="tp-hero__sub">{tagTx.length} transacciones registradas</p>

                </div>
              </div>
            </header>

            {tagTxFiltered.length === 0 ? (
              <div className="tp-no-tx">
                <span>{tagTx.length ? 'No hay ingresos ni gastos para el análisis.' : 'Esta tag no tiene transacciones aún.'}</span>
              </div>
            ) : (
              <>
                {/* Stat cards */}
                <div className="tp-stats-grid">
                  <div className="tp-stat-card tp-stat-card--balance">
                    <span className="tp-stat-card__label">Balance neto</span>
                    <span className={`tp-stat-card__value ${stats.total >= 0 ? 'pos' : 'neg'}`}>
                      {fmtFull(stats.total)}
                    </span>
                  </div>
                  <div className="tp-stat-card tp-stat-card--spent">
                    <span className="tp-stat-card__label">Total gastado</span>
                    <span className="tp-stat-card__value neg">{fmtFull(Math.abs(stats.spent))}</span>
                  </div>
                  <div className="tp-stat-card tp-stat-card--income">
                    <span className="tp-stat-card__label">Total ingresos</span>
                    <span className="tp-stat-card__value pos">{fmtFull(stats.income)}</span>
                  </div>
                  <div className="tp-stat-card">
                    <span className="tp-stat-card__label">Media mensual gastos</span>
                    <span className="tp-stat-card__value neg">{fmt(Math.abs(stats.avg))}</span>
                  </div>
                  <div className="tp-stat-card">
                    <span className="tp-stat-card__label">Transacciones</span>
                    <span className="tp-stat-card__value neutral">{stats.count}</span>
                  </div>
                </div>

                {/* Charts row */}
                <div className="tp-charts-row">
                  {/* Monthly bar chart */}
                  <div className="tp-chart-card">
                    <h3 className="tp-chart-card__title">Evolución mensual</h3>
                    <p className="tp-chart-card__sub">Gastos e ingresos por mes</p>
                    <div className="tp-chart-area">
                      <ResponsiveContainer width="100%" height={220}>
                        <BarChart data={monthlyData} barGap={2}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                          <XAxis dataKey="month" tick={{ fontSize: 11, fill: '#9ca3af' }} axisLine={false} tickLine={false} />
                          <YAxis tickFormatter={v => fmt(v)} tick={{ fontSize: 10, fill: '#9ca3af' }} axisLine={false} tickLine={false} width={72} />
                          <Tooltip content={<BarTooltip />} />
                          <Legend wrapperStyle={{ fontSize: 12 }} />
                          <Bar dataKey="gastos"   name="Gastos"   fill="#ef4444" radius={[4, 4, 0, 0]} />
                          <Bar dataKey="ingresos" name="Ingresos" fill="#10b981" radius={[4, 4, 0, 0]} />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>

                  {/* Category pie chart */}
                  {categoryPieData.length > 0 && (
                    <div className="tp-chart-card">
                      <div className="tp-chart-card__header">
                        <div>
                          <h3 className="tp-chart-card__title">Gasto por categoría</h3>
                          <p className="tp-chart-card__sub">
                            {pieMode === 'parent' ? 'Categorías padre' : 'Subcategorías'}
                          </p>
                        </div>
                        <div className="tp-pie-toggle">
                          <button
                            className={`tp-pie-toggle__btn${pieMode === 'parent' ? ' tp-pie-toggle__btn--active' : ''}`}
                            onClick={() => { setPieMode('parent'); setSelectedCategoryId(undefined); }}
                          >
                            Padre
                          </button>
                          <button
                            className={`tp-pie-toggle__btn${pieMode === 'child' ? ' tp-pie-toggle__btn--active' : ''}`}
                            onClick={() => { setPieMode('child'); setSelectedCategoryId(undefined); }}
                          >
                            Hijos
                          </button>
                        </div>
                      </div>
                      <div className="tp-chart-area">
                        <ResponsiveContainer width="100%" height={220}>
                          <PieChart>
                            <Pie
                              data={categoryPieData}
                              cx="50%"
                              cy="50%"
                              innerRadius={55}
                              outerRadius={90}
                              paddingAngle={3}
                              dataKey="value"
                            >
                              {categoryPieData.map((_, i) => (
                                <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                              ))}
                            </Pie>
                            <Tooltip content={<PieTooltip />} />
                            <Legend
                              formatter={(value: string) => <span style={{ fontSize: 11 }}>{value}</span>}
                            />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  )}
                </div>

                {/* Category breakdown table */}
                {categoryBreakdown.length > 0 && (
                  <div className="tp-breakdown-card">
                    <h3 className="tp-breakdown-card__title">Desglose por categoría</h3>
                    <div className="tp-breakdown-table-wrap">
                    <table className="tp-breakdown-table">
                      <thead>
                        <tr>
                          <th>Categoría</th>
                          <th>Gastos</th>
                          <th>Importe gastado</th>
                          <th>% del total</th>
                        </tr>
                      </thead>
                      <tbody>
                        {categoryBreakdown.map((row, i) => {
                          const visual = getCategoryVisual(row.parentCode);
                          const pct = stats.spent !== 0
                            ? (row.amount / Math.abs(stats.spent)) * 100
                            : 0;
                          return (
                            <tr key={row.id ?? 'uncategorized'} className={`tp-breakdown-row${selectedCategoryId === row.id ? ' tp-breakdown-row--selected' : ''}`}>
                              <td>
                                <div className="tp-breakdown-cat">
                                  <button
                                    type="button"
                                    className="tp-breakdown-cat__badge tp-breakdown-cat__button"
                                    style={{ background: `${visual.color}22`, color: visual.color }}
                                    aria-pressed={selectedCategoryId === row.id}
                                    onClick={() => setSelectedCategoryId(current => current === row.id ? undefined : row.id)}
                                  >
                                    <span aria-hidden="true">{visual.emoji}</span>
                                    <span>{row.parentName}</span>
                                  </button>
                                </div>
                              </td>
                              <td className="tp-breakdown-num">{row.count}</td>
                              <td className="tp-breakdown-amt">{fmtFull(row.amount)}</td>
                              <td>
                                <div className="tp-breakdown-pct-wrap">
                                  <div
                                    className="tp-breakdown-pct-bar"
                                    style={{ width: `${Math.min(pct, 100).toFixed(1)}%`, background: PIE_COLORS[i % PIE_COLORS.length] }}
                                  />
                                  <span className="tp-breakdown-pct-label">{pct.toFixed(1)}%</span>
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                    </div>
                  </div>
                )}
              </>
            )}

            {tagTx.length > 0 && (
              <section className="tp-transactions" aria-labelledby="tp-transactions-title">
                <div className="tp-transactions-header">
                  <div>
                    <h3 id="tp-transactions-title">Transacciones</h3>
                    <span>{visibleTransactions.length} de {tagTx.length}</span>
                  </div>
                  <div className="tp-transactions-controls">
                    <input type="search" value={transactionSearch} onChange={event => setTransactionSearch(event.target.value)} placeholder="Buscar transacciones..." aria-label="Buscar transacciones de la tag" />
                    <details className="tp-category-dropdown" ref={categoryDropdownRef} open={categoryDropdownOpen} onKeyDown={handleCategoryDropdownKey}>
                      <summary role="button" aria-expanded={categoryDropdownOpen} aria-label={`Filtrar transacciones por categoría: ${selectedCategory?.name ?? 'Todas las categorías'}`} title={selectedCategory?.name ?? 'Todas las categorías'} onClick={event => { event.preventDefault(); setCategoryDropdownOpen(current => !current); }}>
                        {selectedCategory && (() => {
                          const visual = categoryVisual(selectedCategory.id);
                          return <span className="tp-category-icon" style={{ color: visual.color, background: `${visual.color}22` }} aria-hidden="true">{visual.emoji}</span>;
                        })()}
                        <span className="tp-category-selected" style={selectedCategory ? { color: categoryVisual(selectedCategory.id).color } : undefined}>{selectedCategory?.name ?? 'Todas las categorías'}</span>
                        <span className="tp-category-arrow" aria-hidden="true">▾</span>
                      </summary>
                      {categoryDropdownOpen && <div className="tp-category-menu" role="group" aria-label="Categorías de transacciones">
                        <button type="button" className="tp-category-option" aria-pressed={selectedCategoryId === undefined} onClick={() => selectTransactionCategory(undefined)}>Todas las categorías</button>
                        {transactionCategoryOptions.map(option => {
                          const visual = categoryVisual(option.id);
                          return (
                            <button key={option.id ?? 'none'} type="button" className="tp-category-option" aria-pressed={selectedCategoryId === option.id} onClick={() => selectTransactionCategory(option.id)}>
                              <span className="tp-category-icon" style={{ color: visual.color, background: `${visual.color}22` }} aria-hidden="true">{visual.emoji}</span>
                              <span style={{ color: visual.color }}>{option.name}</span>
                            </button>
                          );
                        })}
                      </div>}
                    </details>
                    {selectedCategoryId !== undefined && <button type="button" className="tp-clear-category" onClick={() => setSelectedCategoryId(undefined)}>Todas las categorías</button>}
                  </div>
                </div>
                {selectedCategoryId !== undefined && <p className="tp-transactions-filter">Categoría: {transactionCategoryOptions.find(option => option.id === selectedCategoryId)?.name ?? 'Sin categoría'}</p>}
                <div className="tp-transactions-table-wrap">
                  <table className="tp-transactions-table">
                    <thead><tr><th>Fecha</th><th>Descripción / comercio</th><th>Cuenta</th><th>Categoría</th><th>Importe</th><th><span className="tp-visually-hidden">Acciones</span></th></tr></thead>
                    <tbody>
                      {visibleTransactions.length === 0 && <tr><td colSpan={6} className="tp-transactions-empty">No hay transacciones para estos filtros.</td></tr>}
                      {pagedTransactions.map((transaction, index) => {
                        const account = transaction.sourceAccountId != null ? accountById[transaction.sourceAccountId] : undefined;
                        const destination = transaction.destinationAccountId != null ? accountById[transaction.destinationAccountId] : undefined;
                        const category = transaction.categoryId != null ? catById[transaction.categoryId] : undefined;
                        return (
                          <tr key={transaction.id ?? `${transaction.externalId ?? 'transaction'}-${index}`}>
                            <td className="tp-transaction-date">{fmtTransactionDate(transaction.bookingDate)}</td>
                            <td><div className="tp-transaction-description"><strong>{transaction.description || transaction.merchantName || '—'}</strong>{transaction.description && transaction.merchantName && <span>{transaction.merchantName}</span>}</div></td>
                            <td>{account?.name ?? (transaction.sourceAccountId != null ? `#${transaction.sourceAccountId}` : '—')}{destination && <span className="tp-transaction-destination"> → {destination.name}</span>}</td>
                            <td>{category?.name ?? 'Sin categoría'}</td>
                            <td className={`tp-transaction-amount ${(transaction.amount ?? 0) < 0 ? 'negative' : 'positive'}`}>{fmtTransactionAmount(transaction.amount, transaction.currency)}</td>
                            <td>{transaction.id != null && <button type="button" className="tp-transaction-link" title="Ver transacción" aria-label={`Ver transacción ${transaction.description || transaction.id}`} onClick={() => dispatchNavigateToTransaction(transaction.id!)}>↗</button>}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                {totalPages > 1 && (
                  <div className="tp-pagination">
                    <span>{currentPage * pageSize + 1}–{Math.min((currentPage + 1) * pageSize, visibleTransactions.length)} de {visibleTransactions.length}</span>
                    <button type="button" aria-label="Página anterior" title="Página anterior" disabled={currentPage === 0} onClick={() => setTransactionPage(currentPage - 1)}>‹</button>
                    <button type="button" aria-label="Página siguiente" title="Página siguiente" disabled={currentPage >= totalPages - 1} onClick={() => setTransactionPage(currentPage + 1)}>›</button>
                  </div>
                )}
              </section>
            )}
          </>
        )}
      </main>
    </div>
  );
};
