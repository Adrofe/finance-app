import axios from 'axios';
import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, PieChart, Pie, Cell,
} from 'recharts';

import type { Transaction } from '../types/banking';
import type { DashboardSummary, SpendingByCategory, TimeSeriesPoint, DashboardPreset } from '../types/dashboard';
import { fetchDashboardSummary, fetchSpendingByCategory, fetchTimeSeries } from '../services/dashboardService';
import { getCategoryVisual } from '../constants/visualConfig';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import './Dashboard.css';

// ─── Formatters ───────────────────────────────────────────────────────────────

const fmt = (n: number) =>
  new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(n);

const fmtPct = (n: number | null) => (n != null ? `${n.toFixed(1)}%` : '—');

function toISO(d: Date): string {
  return d.toISOString().slice(0, 10);
}

// ─── Preset helpers ───────────────────────────────────────────────────────────

const PRESET_LABELS: Record<DashboardPreset, string> = {
  'this-month': 'Este mes',
  'last-month': 'Mes anterior',
  '3-months':   'Últimos 3m',
  '6-months':   'Últimos 6m',
  'this-year':  'Este año',
  'custom':     'Personalizado',
};

const PRESETS: DashboardPreset[] = [
  'this-month', 'last-month', '3-months', '6-months', 'this-year', 'custom',
];

function getPresetRange(preset: DashboardPreset): { start: string; end: string } {
  const t = new Date();
  const y = t.getFullYear();
  const m = t.getMonth();
  switch (preset) {
    case 'this-month': return { start: toISO(new Date(y, m, 1)),     end: toISO(t) };
    case 'last-month': return { start: toISO(new Date(y, m - 1, 1)), end: toISO(new Date(y, m, 0)) };
    case '3-months':   return { start: toISO(new Date(y, m - 2, 1)), end: toISO(t) };
    case '6-months':   return { start: toISO(new Date(y, m - 5, 1)), end: toISO(t) };
    case 'this-year':  return { start: toISO(new Date(y, 0, 1)),     end: toISO(t) };
    default:           return { start: '', end: '' };
  }
}

// ─── KPI card ─────────────────────────────────────────────────────────────────

interface KpiCardProps {
  icon: string;
  label: string;
  value: string;
  sub?: string;
  variant: 'income' | 'expenses' | 'net' | 'savings' | 'count';
  onClick?: () => void;
  active?: boolean;
}

function KpiCard({ icon, label, value, sub, variant, onClick, active = false }: KpiCardProps) {
  const className = `db-kpi-card db-kpi--${variant}${onClick ? ' db-kpi-clickable' : ''}${active ? ' is-active' : ''}`;

  if (onClick) {
    return (
      <button type="button" className={className} onClick={onClick}>
        <span className="db-kpi-icon">{icon}</span>
        <span className="db-kpi-label">{label}</span>
        <strong className="db-kpi-value">{value}</strong>
        {sub && <span className="db-kpi-sub">{sub}</span>}
      </button>
    );
  }

  return (
    <article className={className}>
      <span className="db-kpi-icon">{icon}</span>
      <span className="db-kpi-label">{label}</span>
      <strong className="db-kpi-value">{value}</strong>
      {sub && <span className="db-kpi-sub">{sub}</span>}
    </article>
  );
}

// ─── Recent transaction row ───────────────────────────────────────────────────

interface RecentRowProps {
  tx: Transaction;
  categoryCodeMap: Record<number, string>;
}

function RecentRow({ tx, categoryCodeMap }: RecentRowProps) {
  const catCode = tx.categoryId ? (categoryCodeMap[tx.categoryId] ?? '') : '';
  const { emoji, color } = getCategoryVisual(catCode);
  const isIncome = (tx.amount ?? 0) > 0;

  const dateLabel = tx.bookingDate
    ? new Date(tx.bookingDate).toLocaleDateString('es-ES', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      })
    : '—';

  return (
    <li className="db-recent-item">
      <span
        className="db-recent-cat-badge"
        style={{ background: `${color}22`, color }}
        title={catCode}
      >
        {emoji}
      </span>
      <div className="db-recent-info">
        <span className="db-recent-merchant">
          {tx.merchantName || tx.description || '—'}
        </span>
        <span className="db-recent-date">{dateLabel}</span>
      </div>
      <strong className={`db-recent-amount ${isIncome ? 'positive' : 'negative'}`}>
        {fmt(tx.amount ?? 0)}
      </strong>
    </li>
  );
}

// ─── Custom tooltip for bar chart ─────────────────────────────────────────────

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function BarTooltip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: '#fff',
      border: '1px solid #d6e0eb',
      borderRadius: 10,
      padding: '10px 14px',
      fontSize: '0.86rem',
      boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
    }}>
      <p style={{ margin: '0 0 6px', fontWeight: 700 }}>{label}</p>
      {payload.map((entry: { name: string; value: number; color: string }, i: number) => (
        <p key={i} style={{ margin: '2px 0', color: entry.color }}>
          {entry.name}: <strong>{fmt(entry.value)}</strong>
        </p>
      ))}
    </div>
  );
}

// ─── Main Dashboard component ─────────────────────────────────────────────────

type DashboardProps = {
  token: string;
  transactions: Transaction[];
  onUnauthorized: (msg: string) => void;
};

type DetailsFilter =
  | { mode: 'all' }
  | { mode: 'income' }
  | { mode: 'expenses' }
  | { mode: 'category'; categoryId: number; name: string };

export function Dashboard({ token, transactions, onUnauthorized }: DashboardProps) {
  const [preset, setPreset]           = useState<DashboardPreset>('this-month');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd]     = useState('');
  const [detailsFilter, setDetailsFilter] = useState<DetailsFilter>({ mode: 'all' });

  const { start, end } = useMemo(() => {
    if (preset === 'custom') return { start: customStart, end: customEnd };
    return getPresetRange(preset);
  }, [preset, customStart, customEnd]);

  const [summary,    setSummary]    = useState<DashboardSummary | null>(null);
  const [categories, setCategories] = useState<SpendingByCategory[]>([]);
  const [timeSeries, setTimeSeries] = useState<TimeSeriesPoint[]>([]);
  const [loading,    setLoading]    = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const { categoryCodeMap, typeMap } = useTransactionCatalogs(token);

  const isInternalTransfer = useCallback((tx: Transaction): boolean => {
    const typeName = tx.typeId != null ? (typeMap[tx.typeId] || '').toUpperCase() : '';
    if (typeName === 'TRANSFER') return true;
    if (tx.linkedTransactionId != null) return true;
    if (tx.sourceAccountId != null && tx.destinationAccountId != null) return true;
    return false;
  }, [typeMap]);

  const loadData = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setFetchError(null);
    try {
      const startParam = start || undefined;
      const endParam   = end   || undefined;
      const [s, c, ts] = await Promise.all([
        fetchDashboardSummary(token, startParam, endParam),
        fetchSpendingByCategory(token, startParam, endParam),
        fetchTimeSeries(token, startParam, endParam, 'MONTH'),
      ]);
      setSummary(s);
      setCategories(c);
      setTimeSeries(ts);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized('Sesión expirada. Por favor vuelve a iniciar sesión.');
        return;
      }
      setFetchError('Error al cargar el dashboard. Comprueba que el backend está en ejecución.');
    } finally {
      setLoading(false);
    }
  }, [token, start, end, onUnauthorized]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const toTimestamp = useCallback((input?: string): number => {
    if (!input) return 0;
    const date = new Date(input);
    return Number.isNaN(date.getTime()) ? 0 : date.getTime();
  }, []);

  const isInSelectedRange = useCallback((input?: string): boolean => {
    if (!input) return false;
    const ts = toTimestamp(input);
    if (ts <= 0) return false;

    const startTs = start ? toTimestamp(start) : 0;
    const endTs = end ? toTimestamp(end) : 0;

    if (startTs && ts < startTs) return false;
    if (endTs && ts > endTs + 86400000 - 1) return false;
    return true;
  }, [end, start, toTimestamp]);

  const periodTransactions = useMemo(() => {
    return transactions.filter((tx) => {
      if (!isInSelectedRange(tx.bookingDate)) return false;
      return !isInternalTransfer(tx);
    });
  }, [isInSelectedRange, isInternalTransfer, transactions]);

  const visibleTransactions = useMemo(() => {
    const filtered = periodTransactions.filter((tx) => {
      const amount = tx.amount ?? 0;
      if (detailsFilter.mode === 'income') return amount > 0;
      if (detailsFilter.mode === 'expenses') return amount < 0;
      if (detailsFilter.mode === 'category') {
        if (amount >= 0 || tx.categoryId == null) return false;
        return tx.categoryId === detailsFilter.categoryId;
      }
      return true;
    });

    return [...filtered]
      .sort((a, b) => toTimestamp(b.bookingDate) - toTimestamp(a.bookingDate))
      .slice(0, 15);
  }, [detailsFilter, periodTransactions, toTimestamp]);

  const detailsTitle = useMemo(() => {
    if (detailsFilter.mode === 'income') return 'Transacciones que componen los ingresos';
    if (detailsFilter.mode === 'expenses') return 'Transacciones que componen los gastos';
    if (detailsFilter.mode === 'category') return `Transacciones de ${detailsFilter.name}`;
    return 'Últimas transacciones del periodo';
  }, [detailsFilter]);

  const detailsSubtitle = useMemo(() => {
    if (detailsFilter.mode === 'income') return 'Mostrando solo ingresos del periodo activo';
    if (detailsFilter.mode === 'expenses') return 'Mostrando solo gastos del periodo activo';
    if (detailsFilter.mode === 'category') return `Mostrando gastos de la categoría ${detailsFilter.name}`;
    return 'Mostrando transacciones más recientes en el periodo activo';
  }, [detailsFilter]);

  // ─── Chart data ─────────────────────────────────────────────────────────────

  const barData = timeSeries.map(p => ({
    period: p.period,
    Ingresos: p.income,
    Gastos: Math.abs(p.expenses),
  }));

  const pieData = categories.slice(0, 10).map(c => ({
    categoryId: c.categoryId,
    name: c.categoryName,
    code: c.categoryCode,
    value: Math.abs(c.total),
    pct: c.percentage ?? 0,
    count: c.transactionCount,
    color: getCategoryVisual(c.categoryCode).color,
    emoji: getCategoryVisual(c.categoryCode).emoji,
  }));

  // ─── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="db-wrap">

      {/* ── Date filter bar ─────────────────────────────────────────────── */}
      <div className="db-filter-bar">
        <div className="db-presets">
          {PRESETS.map(p => (
            <button
              key={p}
              type="button"
              className={`db-preset-btn${preset === p ? ' active' : ''}`}
              onClick={() => setPreset(p)}
            >
              {PRESET_LABELS[p]}
            </button>
          ))}
        </div>

        {preset === 'custom' && (
          <div className="db-custom-dates">
            <label>
              Desde
              <input
                type="date"
                value={customStart}
                onChange={e => setCustomStart(e.target.value)}
              />
            </label>
            <label>
              Hasta
              <input
                type="date"
                value={customEnd}
                onChange={e => setCustomEnd(e.target.value)}
              />
            </label>
          </div>
        )}

        <button
          type="button"
          className="db-refresh-btn"
          onClick={loadData}
          disabled={loading}
          title="Actualizar"
        >
          {loading ? '⏳' : '↻'} Actualizar
        </button>
      </div>

      {/* ── Loading / error ──────────────────────────────────────────────── */}
      {loading && <p className="state">Cargando dashboard...</p>}
      {!loading && fetchError && <p className="state error">{fetchError}</p>}

      {!loading && !fetchError && summary && (
        <>
          {/* ── KPI cards ──────────────────────────────────────────────── */}
          <div className="db-kpi-grid">
            <KpiCard
              icon="💵"
              label="Ingresos"
              variant="income"
              value={fmt(summary.totalIncome)}
              sub={`${summary.transactionCount} transacciones en el periodo`}
              onClick={() => setDetailsFilter(prev => prev.mode === 'income' ? { mode: 'all' } : { mode: 'income' })}
              active={detailsFilter.mode === 'income'}
            />
            <KpiCard
              icon="💸"
              label="Gastos"
              variant="expenses"
              value={fmt(Math.abs(summary.totalExpenses))}
              onClick={() => setDetailsFilter(prev => prev.mode === 'expenses' ? { mode: 'all' } : { mode: 'expenses' })}
              active={detailsFilter.mode === 'expenses'}
            />
            <KpiCard
              icon="📊"
              label="Neto"
              variant="net"
              value={fmt(summary.net)}
              sub={summary.net >= 0 ? 'Balance positivo ✓' : 'Balance negativo ✗'}
            />
            <KpiCard
              icon="📈"
              label="Tasa de ahorro"
              variant="savings"
              value={fmtPct(summary.savingsRate)}
              sub="del total de ingresos"
            />
          </div>

          {/* ── Charts ─────────────────────────────────────────────────── */}
          <div className="db-charts-grid">

            {/* Bar chart: income vs expenses time series */}
            <article className="db-chart-card">
              <h3 className="db-chart-title">Ingresos vs Gastos por periodo</h3>
              {barData.length === 0 ? (
                <p className="db-empty">Sin datos para el periodo seleccionado.</p>
              ) : (
                <ResponsiveContainer width="100%" height={280}>
                  <BarChart data={barData} margin={{ top: 4, right: 12, left: 0, bottom: 4 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e5edf6" vertical={false} />
                    <XAxis
                      dataKey="period"
                      tick={{ fontSize: 11, fill: '#60758a' }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis
                      tickFormatter={v => `${(v / 1000).toFixed(0)}k`}
                      tick={{ fontSize: 11, fill: '#60758a' }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip content={<BarTooltip />} />
                    <Legend
                      wrapperStyle={{ fontSize: '0.85rem', paddingTop: 8 }}
                    />
                    <Bar dataKey="Ingresos" fill="#22c55e" radius={[4, 4, 0, 0]} maxBarSize={48} />
                    <Bar dataKey="Gastos"   fill="#ef4444" radius={[4, 4, 0, 0]} maxBarSize={48} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </article>

            {/* Donut chart: spending by category */}
            <article className="db-chart-card">
              <h3 className="db-chart-title">Gastos por categoría</h3>
              {pieData.length === 0 ? (
                <p className="db-empty">Sin datos para el periodo seleccionado.</p>
              ) : (
                <div className="db-pie-wrap">
                  <ResponsiveContainer width="100%" height={200}>
                    <PieChart>
                      <Pie
                        data={pieData}
                        cx="50%"
                        cy="50%"
                        innerRadius={55}
                        outerRadius={90}
                        paddingAngle={2}
                        dataKey="value"
                        labelLine={false}
                        onClick={(entry: { categoryId?: number; name?: string }) => {
                          const categoryId = entry?.categoryId;
                          const name = entry?.name || 'Categoría';
                          if (categoryId == null) return;
                          setDetailsFilter(prev => (
                            prev.mode === 'category' && prev.categoryId === categoryId
                              ? { mode: 'all' }
                              : { mode: 'category', categoryId, name }
                          ));
                        }}
                      >
                        {pieData.map((entry, i) => (
                          <Cell key={i} fill={entry.color} stroke="none" />
                        ))}
                      </Pie>
                      <Tooltip
                        formatter={(val: unknown) => [fmt(val as number)]}
                        contentStyle={{
                          border: '1px solid #d6e0eb',
                          borderRadius: 10,
                          fontSize: '0.84rem',
                        }}
                      />
                    </PieChart>
                  </ResponsiveContainer>

                  <ul className="db-pie-legend">
                    {pieData.map((item, i) => (
                      <li
                        key={i}
                        className={`db-pie-legend-item${detailsFilter.mode === 'category' && detailsFilter.categoryId === item.categoryId ? ' is-active' : ''}`}
                        onClick={() => {
                          setDetailsFilter(prev => (
                            prev.mode === 'category' && prev.categoryId === item.categoryId
                              ? { mode: 'all' }
                              : { mode: 'category', categoryId: item.categoryId, name: item.name }
                          ));
                        }}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setDetailsFilter(prev => (
                              prev.mode === 'category' && prev.categoryId === item.categoryId
                                ? { mode: 'all' }
                                : { mode: 'category', categoryId: item.categoryId, name: item.name }
                            ));
                          }
                        }}
                      >
                        <span className="db-pie-dot" style={{ background: item.color }} />
                        <span className="db-pie-name">{item.emoji} {item.name}</span>
                        <span className="db-pie-pct">{item.pct.toFixed(1)}%</span>
                        <span className="db-pie-val">{fmt(item.value)}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </article>
          </div>

          {/* ── Recent transactions ─────────────────────────────────────── */}
          <article className="db-chart-card">
            <div className="db-recent-header">
              <div className="db-recent-headline">
                <h3 className="db-chart-title">{detailsTitle}</h3>
                <span className="db-recent-subtitle">{detailsSubtitle}</span>
              </div>
              <span className="db-recent-count">{visibleTransactions.length} mostradas</span>
              {detailsFilter.mode !== 'all' && (
                <button
                  type="button"
                  className="db-clear-filter"
                  onClick={() => setDetailsFilter({ mode: 'all' })}
                >
                  Limpiar filtro
                </button>
              )}
            </div>
            {visibleTransactions.length === 0 ? (
              <p className="db-empty">No hay transacciones para este filtro en el periodo seleccionado.</p>
            ) : (
              <ul className="db-recent-list">
                {visibleTransactions.map((tx, i) => (
                  <RecentRow
                    key={tx.id ?? tx.externalId ?? i}
                    tx={tx}
                    categoryCodeMap={categoryCodeMap}
                  />
                ))}
              </ul>
            )}
          </article>
        </>
      )}
    </div>
  );
}
