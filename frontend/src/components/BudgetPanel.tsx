import axios from 'axios';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  createBudgetPlan,
  deleteBudgetPlan,
  fetchBudgetPlans,
  fetchLatestSnapshot,
  refreshBudgetSnapshot,
} from '../services/budgetService';
import { CatalogService } from '../services/catalogService';
import type { TransactionCategory } from '../services/catalogService';
import { getCategoryVisual } from '../constants/visualConfig';
import type {
  BudgetLineType,
  BudgetPeriod,
  BudgetPlanDTO,
  BudgetPlanLineRequest,
  BudgetPlanRequest,
  BudgetSnapshotDTO,
  BudgetSnapshotLineDTO,
} from '../types/budget';

/* ── helpers ─────────────────────────────────────────────────────────────── */
const fmt = (n: number, currency = 'EUR') =>
  new Intl.NumberFormat('es-ES', { style: 'currency', currency }).format(n);

const pct = (spent: number, budget: number) =>
  budget > 0 ? Math.min((spent / budget) * 100, 100) : 0;

const PERIOD_LABELS: Record<BudgetPeriod, string> = {
  MONTHLY: 'Mensual',
  QUARTERLY: 'Trimestral',
  ANNUAL: 'Anual',
  CUSTOM: 'Personalizado',
};

const CATEGORY_EMOJIS: Record<string, string> = {
  FOOD: '🍽️',
  TRANS: '🚗',
  HOUSE: '🏠',
  HEALTH: '💊',
  LEISURE: '🎬',
  CLOTHES: '👗',
  EDUCATION: '📚',
  TECH: '💻',
  TRAVEL: '✈️',
  SAVINGS: '💰',
  OTHER: '📦',
};

const getCategoryEmoji = (code: string): string => {
  const prefix = code.split('.')[0].toUpperCase();
  return CATEGORY_EMOJIS[prefix] ?? '📂';
};

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function firstDayOfMonth(): string {
  const d = new Date();
  d.setDate(1);
  return d.toISOString().slice(0, 10);
}

/* ── Plan creation modal ─────────────────────────────────────────────────── */
type CreatePlanModalProps = {
  accessToken: string;
  onClose: () => void;
  onSave: (req: BudgetPlanRequest) => Promise<void>;
  saving: boolean;
};

const emptyLine = (lineType: BudgetLineType = 'EXPENSE'): BudgetPlanLineRequest => ({
  categoryCode: '',
  categoryName: '',
  budgetAmount: 0,
  lineType,
});

function CreatePlanModal({ accessToken, onClose, onSave, saving }: CreatePlanModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [period, setPeriod] = useState<BudgetPeriod>('MONTHLY');
  const [currency, setCurrency] = useState('EUR');
  const [startDate, setStartDate] = useState(firstDayOfMonth());
  const [endDate, setEndDate] = useState(today());
  const [lines, setLines] = useState<BudgetPlanLineRequest[]>([emptyLine('EXPENSE')]);
  const [error, setError] = useState<string | null>(null);
  const [lineTab, setLineTab] = useState<BudgetLineType>('EXPENSE');

  // Category picker
  const [categories, setCategories] = useState<TransactionCategory[]>([]);
  const [activePickerIdx, setActivePickerIdx] = useState<number | null>(null);
  const [categorySearch, setCategorySearch] = useState('');
  const pickerDialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    CatalogService.fetchCategories(accessToken).then(setCategories).catch(() => {});
  }, [accessToken]);

  // Close picker on outside click
  useEffect(() => {
    if (activePickerIdx === null) return;
    const handle = (e: MouseEvent) => {
      if (pickerDialogRef.current && !pickerDialogRef.current.contains(e.target as Node)) {
        setActivePickerIdx(null);
        setCategorySearch('');
      }
    };
    document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, [activePickerIdx]);

  const parentCategories = categories.filter(c => !c.parentId);
  const childCategoriesMap = categories.reduce<Record<number, TransactionCategory[]>>((acc, cat) => {
    if (cat.parentId) {
      if (!acc[cat.parentId]) acc[cat.parentId] = [];
      acc[cat.parentId].push(cat);
    }
    return acc;
  }, {});

  const filteredParents = parentCategories.filter(p => {
    if (!categorySearch.trim()) return true;
    const q = categorySearch.toLowerCase();
    return (
      p.name.toLowerCase().includes(q) ||
      p.code?.toLowerCase().includes(q) ||
      (childCategoriesMap[p.id] || []).some(c => c.name.toLowerCase().includes(q) || c.code?.toLowerCase().includes(q))
    );
  });

  const getVisibleChildren = (parentId: number): TransactionCategory[] => {
    const children = childCategoriesMap[parentId] || [];
    if (!categorySearch.trim()) return children;
    const q = categorySearch.toLowerCase();
    return children.filter(c => c.name.toLowerCase().includes(q) || c.code?.toLowerCase().includes(q));
  };

  const pickCategory = (lineIdx: number, cat: TransactionCategory | null) => {
    setLines(prev => prev.map((l, i) =>
      i === lineIdx
        ? { ...l, categoryId: cat?.id, categoryCode: cat?.code ?? '', categoryName: cat?.name ?? '' }
        : l
    ));
    setActivePickerIdx(null);
    setCategorySearch('');
  };

  const updateLine = (i: number, field: keyof BudgetPlanLineRequest, value: string | number) => {
    setLines(prev => prev.map((l, idx) => idx === i ? { ...l, [field]: value } : l));
  };

  const addLine = () => setLines(prev => [...prev, emptyLine(lineTab)]);
  const removeLine = (i: number) => setLines(prev => prev.filter((_, idx) => idx !== i));
  const visibleLines = lines.map((l, i) => ({ ...l, _idx: i })).filter(l => l.lineType === lineTab);

  const handleSubmit = async () => {
    setError(null);
    if (!name.trim()) { setError('El nombre es obligatorio'); return; }
    if (lines.some(l => !l.categoryCode.trim() || l.budgetAmount <= 0)) {
      setError('Todas las líneas requieren código de categoría y un importe mayor que 0');
      return;
    }
    const req: BudgetPlanRequest = {
      name: name.trim(),
      description: description.trim() || undefined,
      period,
      currency,
      startDate: period === 'CUSTOM' ? startDate : undefined,
      endDate: period === 'CUSTOM' ? endDate : undefined,
      lines,
    };
    await onSave(req);
  };

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="modal" style={{ width: 640, maxHeight: '90vh', overflowY: 'auto' }}>
        <div className="modal-header">
          <h4>Nuevo plan de presupuesto</h4>
          <button type="button" className="btn icon-btn" onClick={onClose} aria-label="Cerrar">✕</button>
        </div>
        <div className="modal-body" style={{ display: 'block', padding: '20px 24px' }}>
          <div className="bdg-form-grid">
            <div className="bdg-form-field">
              <label>Nombre *</label>
              <input
                type="text"
                className="bdg-input"
                placeholder="Presupuesto mensual"
                value={name}
                onChange={e => setName(e.target.value)}
              />
            </div>
            <div className="bdg-form-field">
              <label>Periodicidad</label>
              <select className="bdg-input" value={period} onChange={e => setPeriod(e.target.value as BudgetPeriod)}>
                <option value="MONTHLY">Mensual</option>
                <option value="QUARTERLY">Trimestral</option>
                <option value="ANNUAL">Anual</option>
                <option value="CUSTOM">Personalizado</option>
              </select>
            </div>
            <div className="bdg-form-field" style={{ gridColumn: '1 / -1' }}>
              <label>Descripción</label>
              <input
                type="text"
                className="bdg-input"
                placeholder="Descripción opcional"
                value={description}
                onChange={e => setDescription(e.target.value)}
              />
            </div>
            {period === 'CUSTOM' && (
              <>
                <div className="bdg-form-field">
                  <label>Fecha inicio</label>
                  <input type="date" className="bdg-input" value={startDate} onChange={e => setStartDate(e.target.value)} />
                </div>
                <div className="bdg-form-field">
                  <label>Fecha fin</label>
                  <input type="date" className="bdg-input" value={endDate} onChange={e => setEndDate(e.target.value)} />
                </div>
              </>
            )}
            <div className="bdg-form-field">
              <label>Moneda</label>
              <select className="bdg-input" value={currency} onChange={e => setCurrency(e.target.value)}>
                <option value="EUR">EUR</option>
                <option value="USD">USD</option>
              </select>
            </div>
          </div>

          <div className="bdg-lines-header">
            <div className="bdg-line-tabs">
              <button
                type="button"
                className={`bdg-line-tab ${lineTab === 'EXPENSE' ? 'active' : ''}`}
                onClick={() => setLineTab('EXPENSE')}
              >💸 Gastos ({lines.filter(l => l.lineType === 'EXPENSE').length})</button>
              <button
                type="button"
                className={`bdg-line-tab ${lineTab === 'INCOME' ? 'active' : ''}`}
                onClick={() => setLineTab('INCOME')}
              >💰 Ingresos ({lines.filter(l => l.lineType === 'INCOME').length})</button>
            </div>
            <button type="button" className="btn small" onClick={addLine}>+ Añadir línea</button>
          </div>

          <div className="bdg-lines-table">
            <div className="bdg-lines-row bdg-lines-head">
              <span>Categoría</span>
              <span>{lineTab === 'INCOME' ? 'Ingreso esperado' : 'Importe'}</span>
              <span />
            </div>
            {visibleLines.length === 0 && (
              <div className="bdg-lines-empty">
                <span>Sin líneas de {lineTab === 'INCOME' ? 'ingresos' : 'gastos'}. Pulsa "+ Añadir línea".</span>
              </div>
            )}
            {visibleLines.map((line) => {
              const selectedCat = categories.find(c => c.id === line.categoryId);
              const isPickerOpen = activePickerIdx === line._idx;
              return (
                <div key={line._idx} className="bdg-lines-row">
                  {/* Category picker trigger */}
                  <button
                    type="button"
                    className={'category-trigger bdg-cat-trigger' + (isPickerOpen ? ' active' : '')}
                    onClick={() => {
                      setActivePickerIdx(isPickerOpen ? null : line._idx);
                      setCategorySearch('');
                    }}
                  >
                    {selectedCat ? (
                      <span className="category-trigger-value">
                        <span>{getCategoryVisual(selectedCat.code || selectedCat.name).emoji}</span>
                        <span>
                          {selectedCat.parentName
                            ? selectedCat.parentName + ' › ' + selectedCat.name
                            : selectedCat.name}
                        </span>
                      </span>
                    ) : (
                      <span className="category-trigger-placeholder">
                        {lineTab === 'INCOME' ? 'Ej: Salario' : 'Ej: Alimentación'}
                      </span>
                    )}
                    <span className="category-trigger-arrow">{isPickerOpen ? '▲' : '▼'}</span>
                  </button>

                  <input
                    type="number"
                    className="bdg-input small"
                    min={0}
                    step={10}
                    placeholder="0"
                    value={line.budgetAmount || ''}
                    onChange={e => updateLine(line._idx, 'budgetAmount', parseFloat(e.target.value) || 0)}
                  />
                  <button
                    type="button"
                    className="btn icon-btn danger"
                    onClick={() => removeLine(line._idx)}
                    aria-label="Eliminar línea"
                  >✕</button>
                </div>
              );
            })}
          </div>

          {/* Category picker floating dialog — rendered once, outside the lines table */}
          {activePickerIdx !== null && (
            <div
              className="category-picker-overlay"
              onClick={() => { setActivePickerIdx(null); setCategorySearch(''); }}
            >
              <div
                ref={pickerDialogRef}
                className="category-picker-dialog"
                onClick={e => e.stopPropagation()}
              >
                <div className="category-picker-header">
                  <span>Seleccionar categoría</span>
                  <button
                    type="button"
                    className="category-picker-close"
                    onClick={() => { setActivePickerIdx(null); setCategorySearch(''); }}
                  >×</button>
                </div>

                <div className="category-picker-search-bar">
                  <span className="category-search-icon">🔍</span>
                  <input
                    className="category-search-input"
                    placeholder="Filtrar categorías..."
                    value={categorySearch}
                    onChange={e => setCategorySearch(e.target.value)}
                    autoFocus
                  />
                  {categorySearch && (
                    <button type="button" className="category-search-clear" onClick={() => setCategorySearch('')}>×</button>
                  )}
                </div>

                <div className="category-picker-list">
                  <div
                    className={'category-none-item' + (!lines[activePickerIdx]?.categoryId ? ' selected' : '')}
                    onClick={() => pickCategory(activePickerIdx, null)}
                  >
                    Sin categoría
                  </div>

                  <div className="category-picker-grid">
                    {filteredParents.map(parent => {
                      const pv = getCategoryVisual(parent.code || parent.name);
                      const children = getVisibleChildren(parent.id);
                      const totalChildren = (childCategoriesMap[parent.id] || []).length;
                      return (
                        <div key={parent.id} className="category-group">
                          <div
                            className={'category-parent-row' + (lines[activePickerIdx]?.categoryId === parent.id ? ' selected' : '')}
                            onClick={() => pickCategory(activePickerIdx, parent)}
                          >
                            <span className="cat-emoji">{pv.emoji}</span>
                            <span className="cat-name">{parent.name}</span>
                            {totalChildren > 0 && (
                              <span className="cat-count">{totalChildren}</span>
                            )}
                          </div>
                          {children.map(child => {
                            const cv = getCategoryVisual(child.code || child.name);
                            return (
                              <div
                                key={child.id}
                                className={'category-child-row' + (lines[activePickerIdx]?.categoryId === child.id ? ' selected' : '')}
                                onClick={() => pickCategory(activePickerIdx, child)}
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

                  {filteredParents.length === 0 && (
                    <div className="category-no-results">
                      Sin resultados para "{categorySearch}"
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {error && <p className="bdg-form-error">{error}</p>}

          <div className="bdg-modal-actions">
            <button type="button" className="btn" onClick={onClose}>Cancelar</button>
            <button type="button" className="btn primary" onClick={handleSubmit} disabled={saving}>
              {saving ? 'Guardando…' : 'Crear plan'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── Snapshot compliance line ────────────────────────────────────────────── */
function SnapshotLine({ line, currency }: { line: BudgetSnapshotLineDTO; currency: string }) {
  const isIncome = line.lineType === 'INCOME';
  const progress = pct(line.spentAmount, line.budgetAmount);
  const over = isIncome
    ? line.spentAmount > line.budgetAmount   // income over = surplus (good)
    : line.spentAmount > line.budgetAmount;  // expense over = bad

  return (
    <div className="bdg-snap-line">
      <div className="bdg-snap-line-header">
        <div className="bdg-snap-line-cat">
          <span className="bdg-cat-icon">{getCategoryEmoji(line.categoryCode)}</span>
          <span className="bdg-cat-name">{line.categoryName || line.categoryCode}</span>
          {isIncome && <span className="bdg-type-tag income">ingreso</span>}
        </div>
        <div className="bdg-snap-line-amounts">
          <span className={!isIncome && over ? 'bdg-over' : isIncome && over ? 'bdg-income-surplus' : ''}>
            {fmt(line.spentAmount, currency)}
          </span>
          <span className="bdg-sep">/</span>
          <span className="bdg-budget">{fmt(line.budgetAmount, currency)}</span>
          <span className={`bdg-badge ${line.compliant ? 'ok' : 'ko'}`}>{line.compliant ? '✓' : '✗'}</span>
        </div>
      </div>
      <div className="bdg-bar-track">
        <div
          className={`bdg-bar-fill ${isIncome ? 'income' : ''} ${over && !isIncome ? 'over' : ''}`}
          style={{ width: `${progress}%` }}
        />
      </div>
      <div className="bdg-snap-line-footer">
        <span className="bdg-pct">
          {isIncome ? `${progress.toFixed(0)}% recibido` : `${progress.toFixed(0)}% utilizado`}
        </span>
        <span className={`bdg-variance ${isIncome ? (line.variance >= 0 ? 'pos' : 'neg') : (line.variance >= 0 ? 'pos' : 'neg')}`}>
          {isIncome
            ? (line.variance >= 0 ? `+${fmt(line.variance, currency)} de más` : `${fmt(line.variance, currency)} pendiente`)
            : (line.variance >= 0 ? `+${fmt(line.variance, currency)} restante` : `${fmt(line.variance, currency)} excedido`)}
        </span>
      </div>
    </div>
  );
}

/* ── Plan detail view ────────────────────────────────────────────────────── */
type PlanDetailProps = {
  plan: BudgetPlanDTO;
  token: string;
  onBack: () => void;
  onUnauthorized?: (msg: string) => void;
};

function PlanDetail({ plan, token, onBack, onUnauthorized }: PlanDetailProps) {
  const [snapshot, setSnapshot] = useState<BudgetSnapshotDTO | null>(null);
  const [loadingSnap, setLoadingSnap] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [startDate, setStartDate] = useState(firstDayOfMonth());
  const [endDate, setEndDate] = useState(today());

  const loadSnapshot = useCallback(async () => {
    try {
      setLoadingSnap(true);
      const snap = await fetchLatestSnapshot(token, plan.id);
      setSnapshot(snap);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      }
    } finally {
      setLoadingSnap(false);
    }
  }, [token, plan.id, onUnauthorized]);

  useEffect(() => { loadSnapshot(); }, [loadSnapshot]);

  const handleRefresh = async () => {
    setError(null);
    if (!startDate || !endDate) { setError('Selecciona un rango de fechas'); return; }
    try {
      setRefreshing(true);
      const snap = await refreshBudgetSnapshot(token, plan.id, startDate, endDate);
      setSnapshot(snap);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      } else {
        setError('Error calculando el presupuesto');
      }
    } finally {
      setRefreshing(false);
    }
  };

  const totalProgress = snapshot ? pct(snapshot.totalSpent, snapshot.totalBudget) : 0;

  return (
    <div className="bdg-detail">
      <div className="bdg-detail-topbar">
        <button type="button" className="btn small" onClick={onBack}>← Volver</button>
        <div className="bdg-detail-title">
          <h3>{plan.name}</h3>
          {plan.description && <p>{plan.description}</p>}
        </div>
        <span className="bdg-period-pill">{PERIOD_LABELS[plan.period]}</span>
      </div>

      {/* Date range + refresh */}
      <div className="bdg-refresh-bar">
        <div className="bdg-date-group">
          <label>Desde</label>
          <input type="date" className="bdg-input small" value={startDate} onChange={e => setStartDate(e.target.value)} />
        </div>
        <div className="bdg-date-group">
          <label>Hasta</label>
          <input type="date" className="bdg-input small" value={endDate} onChange={e => setEndDate(e.target.value)} />
        </div>
        <button
          type="button"
          className="btn primary"
          onClick={handleRefresh}
          disabled={refreshing}
        >
          {refreshing ? '⟳ Calculando…' : '⟳ Calcular'}
        </button>
      </div>
      {error && <p className="bdg-error">{error}</p>}

      {/* Snapshot summary */}
      {loadingSnap && <p className="state">Cargando análisis…</p>}
      {!loadingSnap && !snapshot && (
        <div className="bdg-empty-snap">
          <span className="bdg-empty-icon">📊</span>
          <p>Aún no hay análisis. Selecciona un rango y pulsa <strong>Calcular</strong>.</p>
        </div>
      )}
      {snapshot && (
        <>
          <div className="bdg-snap-summary">
            <div className="bdg-snap-kpi">
              <span className="bdg-kpi-label">Presupuesto total</span>
              <span className="bdg-kpi-value">{fmt(snapshot.totalBudget, plan.currency)}</span>
            </div>
            <div className="bdg-snap-kpi">
              <span className="bdg-kpi-label">Gastado</span>
              <span className={`bdg-kpi-value ${snapshot.totalSpent > snapshot.totalBudget ? 'bdg-over' : ''}`}>
                {fmt(snapshot.totalSpent, plan.currency)}
              </span>
            </div>
            <div className="bdg-snap-kpi">
              <span className="bdg-kpi-label">Restante</span>
              <span className={`bdg-kpi-value ${snapshot.totalVariance < 0 ? 'bdg-over' : 'bdg-ok'}`}>
                {fmt(snapshot.totalVariance, plan.currency)}
              </span>
            </div>
            <div className="bdg-snap-kpi">
              <span className="bdg-kpi-label">Estado global</span>
              <span className={`bdg-badge large ${snapshot.compliant ? 'ok' : 'ko'}`}>
                {snapshot.compliant ? '✓ Dentro del presupuesto' : '✗ Presupuesto superado'}
              </span>
            </div>
          </div>

          {/* Income summary KPIs (shown only if there are income lines) */}
          {snapshot.totalExpectedIncome != null && snapshot.totalExpectedIncome > 0 && (
            <div className="bdg-snap-summary income-row">
              <div className="bdg-snap-kpi">
                <span className="bdg-kpi-label">Ingreso esperado</span>
                <span className="bdg-kpi-value income">{fmt(snapshot.totalExpectedIncome, plan.currency)}</span>
              </div>
              <div className="bdg-snap-kpi">
                <span className="bdg-kpi-label">Ingreso real</span>
                <span className={`bdg-kpi-value ${(snapshot.totalIncome ?? 0) >= snapshot.totalExpectedIncome ? 'bdg-ok' : 'bdg-over'}`}>
                  {fmt(snapshot.totalIncome ?? 0, plan.currency)}
                </span>
              </div>
              <div className="bdg-snap-kpi">
                <span className="bdg-kpi-label">Balance neto</span>
                <span className={`bdg-kpi-value ${(snapshot.netBalance ?? 0) >= 0 ? 'bdg-ok' : 'bdg-over'}`}>
                  {(snapshot.netBalance ?? 0) >= 0 ? '+' : ''}{fmt(snapshot.netBalance ?? 0, plan.currency)}
                </span>
              </div>
            </div>
          )}

          {/* Global progress bar */}
          <div className="bdg-global-bar-wrap">
            <div className="bdg-bar-track global">
              <div
                className={`bdg-bar-fill ${snapshot.totalSpent > snapshot.totalBudget ? 'over' : ''}`}
                style={{ width: `${totalProgress}%` }}
              />
            </div>
            <span className="bdg-pct">{totalProgress.toFixed(0)}%</span>
          </div>

          <div className="bdg-snap-period">
            Período analizado: <strong>{snapshot.startDate}</strong> — <strong>{snapshot.endDate}</strong>
            &nbsp;·&nbsp; Calculado el {new Date(snapshot.computedAt).toLocaleString('es-ES')}
          </div>

          {/* Per-category lines split by type */}
          {(() => {
            const expenseLines = snapshot.lines.filter(l => l.lineType !== 'INCOME');
            const incomeLines = snapshot.lines.filter(l => l.lineType === 'INCOME');
            return (
              <>
                {incomeLines.length > 0 && (
                  <>
                    <div className="bdg-section-divider income">
                      <span>💰 Ingresos</span>
                      <div className="bdg-section-kpis">
                        <span className="bdg-kpi-inline">Esperado: <strong>{fmt(snapshot.totalExpectedIncome ?? 0, plan.currency)}</strong></span>
                        <span className="bdg-kpi-inline">Recibido: <strong>{fmt(snapshot.totalIncome ?? 0, plan.currency)}</strong></span>
                        <span className={`bdg-kpi-inline ${(snapshot.incomeVariance ?? 0) >= 0 ? 'pos' : 'neg'}`}>
                          Balance ingresos: <strong>{(snapshot.incomeVariance ?? 0) >= 0 ? '+' : ''}{fmt(snapshot.incomeVariance ?? 0, plan.currency)}</strong>
                        </span>
                      </div>
                    </div>
                    <div className="bdg-snap-lines">
                      {incomeLines.map(line => (
                        <SnapshotLine key={line.id} line={line} currency={plan.currency} />
                      ))}
                    </div>
                  </>
                )}

                {expenseLines.length > 0 && (
                  <>
                    <div className="bdg-section-divider expense">
                      <span>💸 Gastos</span>
                    </div>
                    <div className="bdg-snap-lines">
                      {expenseLines.map(line => (
                        <SnapshotLine key={line.id} line={line} currency={plan.currency} />
                      ))}
                    </div>
                  </>
                )}
              </>
            );
          })()}
        </>
      )}
    </div>
  );
}

/* ── Plans grid ──────────────────────────────────────────────────────────── */
type PlansGridProps = {
  plans: BudgetPlanDTO[];
  onSelect: (plan: BudgetPlanDTO) => void;
  onDelete: (planId: number) => Promise<void>;
  onNew: () => void;
};

function PlansGrid({ plans, onSelect, onDelete, onNew }: PlansGridProps) {
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const handleDelete = async (e: React.MouseEvent, planId: number) => {
    e.stopPropagation();
    if (!window.confirm('¿Eliminar este plan de presupuesto?')) return;
    setDeletingId(planId);
    try { await onDelete(planId); } finally { setDeletingId(null); }
  };

  return (
    <div className="bdg-plans-section">
      <div className="bdg-plans-header">
        <div>
          <h3>Planes de presupuesto</h3>
          <p className="bdg-plans-sub">Define tus límites por categoría y analiza el cumplimiento</p>
        </div>
        <button type="button" className="btn primary" onClick={onNew}>+ Nuevo plan</button>
      </div>

      {plans.length === 0 ? (
        <div className="bdg-empty">
          <span className="bdg-empty-icon">💰</span>
          <h4>Sin planes de presupuesto</h4>
          <p>Crea un plan para empezar a controlar tus gastos por categoría.</p>
          <button type="button" className="btn primary" onClick={onNew}>Crear primer plan</button>
        </div>
      ) : (
        <div className="bdg-grid">
          {plans.map(plan => (
            <div
              key={plan.id}
              className="bdg-card"
              role="button"
              tabIndex={0}
              onClick={() => onSelect(plan)}
              onKeyDown={e => e.key === 'Enter' && onSelect(plan)}
            >
              <div className="bdg-card-top">
                <span className="bdg-card-icon">📋</span>
                <span className={`bdg-badge ${plan.isActive ? 'ok' : ''}`}>
                  {plan.isActive ? 'Activo' : 'Inactivo'}
                </span>
              </div>
              <div className="bdg-card-name">{plan.name}</div>
              {plan.description && <div className="bdg-card-desc">{plan.description}</div>}
              <div className="bdg-card-meta">
                <span className="bdg-period-pill small">{PERIOD_LABELS[plan.period]}</span>
                <span className="bdg-card-currency">{plan.currency}</span>
              </div>
              <div className="bdg-card-stats">
                <span>💸 {plan.lines.filter(l => l.lineType !== 'INCOME').length} gasto{plan.lines.filter(l => l.lineType !== 'INCOME').length !== 1 ? 's' : ''}
                  {' · '}{fmt(plan.lines.filter(l => l.lineType !== 'INCOME').reduce((s, l) => s + l.budgetAmount, 0), plan.currency)}
                </span>
                {plan.lines.some(l => l.lineType === 'INCOME') && (
                  <span>💰 {plan.lines.filter(l => l.lineType === 'INCOME').length} ingreso{plan.lines.filter(l => l.lineType === 'INCOME').length !== 1 ? 's' : ''}
                    {' · '}{fmt(plan.lines.filter(l => l.lineType === 'INCOME').reduce((s, l) => s + l.budgetAmount, 0), plan.currency)}
                  </span>
                )}
              </div>
              <div className="bdg-card-footer">
                <span className="bdg-card-hint">Clic para analizar →</span>
                <button
                  type="button"
                  className="btn icon-btn danger small"
                  onClick={e => handleDelete(e, plan.id)}
                  disabled={deletingId === plan.id}
                  aria-label="Eliminar plan"
                >🗑</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/* ── BudgetPanel (main export) ───────────────────────────────────────────── */
type Props = {
  token: string;
  onUnauthorized?: (msg: string) => void;
};

export function BudgetPanel({ token, onUnauthorized }: Props) {
  const [plans, setPlans] = useState<BudgetPlanDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<BudgetPlanDTO | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [saving, setSaving] = useState(false);

  const loadPlans = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await fetchBudgetPlans(token);
      setPlans(data);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      } else {
        setError('Error cargando planes de presupuesto');
      }
    } finally {
      setLoading(false);
    }
  }, [token, onUnauthorized]);

  useEffect(() => { loadPlans(); }, [loadPlans]);

  const handleCreate = async (req: BudgetPlanRequest) => {
    setSaving(true);
    try {
      await createBudgetPlan(token, req);
      setShowCreate(false);
      await loadPlans();
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (planId: number) => {
    try {
      await deleteBudgetPlan(token, planId);
      setPlans(prev => prev.filter(p => p.id !== planId));
      if (selectedPlan?.id === planId) setSelectedPlan(null);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      }
    }
  };

  return (
    <article className="sheet bdg-panel">
      <div className="sheet-header">
        <h3>Presupuesto</h3>
        <span>Controla tus gastos por categoría</span>
      </div>

      {loading && <p className="state">Cargando…</p>}
      {!loading && error && <p className="state error">{error}</p>}

      {!loading && !error && (
        selectedPlan ? (
          <PlanDetail
            plan={selectedPlan}
            token={token}
            onBack={() => setSelectedPlan(null)}
            onUnauthorized={onUnauthorized}
          />
        ) : (
          <PlansGrid
            plans={plans}
            onSelect={setSelectedPlan}
            onDelete={handleDelete}
            onNew={() => setShowCreate(true)}
          />
        )
      )}

      {showCreate && (
        <CreatePlanModal
          accessToken={token}
          onClose={() => setShowCreate(false)}
          onSave={handleCreate}
          saving={saving}
        />
      )}
    </article>
  );
}
