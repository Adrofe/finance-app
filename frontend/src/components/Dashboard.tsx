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
}

function KpiCard({ icon, label, value, sub, variant }: KpiCardProps) {
  return (
    <article className={`db-kpi-card db-kpi--${variant}`}>
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
  recentTransactions: Transaction[];
  onUnauthorized: (msg: string) => void;
};

export function Dashboard({ token, recentTransactions, onUnauthorized }: DashboardProps) {
  const [preset, setPreset]           = useState<DashboardPreset>('this-month');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd]     = useState('');

  const { start, end } = useMemo(() => {
    if (preset === 'custom') return { start: customStart, end: customEnd };
    return getPresetRange(preset);
  }, [preset, customStart, customEnd]);

  const [summary,    setSummary]    = useState<DashboardSummary | null>(null);
  const [categories, setCategories] = useState<SpendingByCategory[]>([]);
  const [timeSeries, setTimeSeries] = useState<TimeSeriesPoint[]>([]);
  const [loading,    setLoading]    = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const { categoryCodeMap } = useTransactionCatalogs(token);

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

  // ─── Chart data ─────────────────────────────────────────────────────────────

  const barData = timeSeries.map(p => ({
    period: p.period,
    Ingresos: p.income,
    Gastos: Math.abs(p.expenses),
  }));

  const pieData = categories.slice(0, 10).map(c => ({
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
            />
            <KpiCard
              icon="💸"
              label="Gastos"
              variant="expenses"
              value={fmt(Math.abs(summary.totalExpenses))}
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
                      <li key={i} className="db-pie-legend-item">
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
              <h3 className="db-chart-title">Últimas transacciones</h3>
              <span className="db-recent-count">{recentTransactions.length} mostradas</span>
            </div>
            {recentTransactions.length === 0 ? (
              <p className="db-empty">Sin transacciones recientes.</p>
            ) : (
              <ul className="db-recent-list">
                {recentTransactions.map((tx, i) => (
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
