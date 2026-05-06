import React, { useMemo } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useInvestmentsDashboard } from '../hooks/useInvestmentsDashboard';
import './investments-dashboard.css';

type Props = {
  token: string;
  onUnauthorized?: (message: string) => void;
};

const TYPE_COLORS = ['#0f6bb4', '#16a34a', '#f59e0b', '#8b5cf6', '#ef4444', '#14b8a6', '#64748b'];

const safeNumber = (value: number | null | undefined) => (typeof value === 'number' && Number.isFinite(value) ? value : 0);

const fmtMoney = (value: number | null | undefined, currency = 'EUR') =>
  safeNumber(value).toLocaleString('es-ES', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 });

const fmtPct = (value: number | null | undefined) => {
  const amount = safeNumber(value);
  return `${amount >= 0 ? '+' : ''}${amount.toFixed(2)}%`;
};

const pnlClass = (value: number | null | undefined) => (safeNumber(value) >= 0 ? 'idb-positive' : 'idb-negative');

export const InvestmentsDashboard: React.FC<Props> = ({ token, onUnauthorized }) => {
  const {
    summary,
    taxSummary,
    selectedYear,
    setSelectedYear,
    availableYears,
    instrumentComposition,
    typeComposition,
    realizedSeries,
    realizedAllTime,
    unrealizedCurrent,
    bestInstrument,
    loading,
    error,
    clearError,
  } = useInvestmentsDashboard(token, onUnauthorized);

  const topInstrumentBars = useMemo(() => {
    return instrumentComposition
      .slice(0, 8)
      .map((item) => ({
        name: item.symbol,
        value: item.currentValue,
        pnl: item.pnl,
      }));
  }, [instrumentComposition]);

  if (loading) {
    return <p className="state">Cargando dashboard de inversiones...</p>;
  }

  if (error) {
    return (
      <div className="idb-state">
        <p className="state error">{error}</p>
        <button className="btn secondary" type="button" onClick={clearError}>Cerrar</button>
      </div>
    );
  }

  if (!summary) {
    return <p className="state">No hay datos de inversiones para mostrar todavía.</p>;
  }

  return (
    <div className="idb-wrapper">
      <div className="idb-metrics-grid">
        <article className="metric-card">
          <h3>Valor Cartera</h3>
          <strong>{fmtMoney(summary.totalCurrentValue)}</strong>
          <span>{summary.positions} posiciones activas</span>
        </article>

        <article className="metric-card">
          <h3>Capital Invertido</h3>
          <strong>{fmtMoney(summary.totalInvested)}</strong>
          <span>Coste acumulado de compra</span>
        </article>

        <article className="metric-card">
          <h3>P&amp;L No Realizado</h3>
          <strong className={pnlClass(unrealizedCurrent)}>{fmtMoney(unrealizedCurrent)}</strong>
          <span className={pnlClass(summary.totalPnlPct)}>{fmtPct(summary.totalPnlPct)}</span>
        </article>

        <article className="metric-card">
          <h3>P&amp;L Realizado (Total)</h3>
          <strong className={pnlClass(realizedAllTime)}>{fmtMoney(realizedAllTime)}</strong>
          <span>Ganancias/pérdidas cerradas por ventas</span>
        </article>

        <article className="metric-card idb-metric-highlight">
          <h3>Top Instrumento</h3>
          <strong>{bestInstrument ? bestInstrument.symbol : '—'}</strong>
          <span className={bestInstrument ? pnlClass(bestInstrument.pnl) : ''}>
            {bestInstrument ? fmtMoney(bestInstrument.currentValue) : 'Sin datos'}
          </span>
        </article>
      </div>

      <div className="idb-grid">
        <article className="idb-card">
          <div className="sheet-header">
            <h3>Beneficio Realizado en el Tiempo</h3>
            <span>Cumulado por ventas (FIFO)</span>
          </div>
          {realizedSeries.length === 0 ? (
            <p className="idb-empty">No hay ventas registradas para construir la serie temporal.</p>
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <LineChart data={realizedSeries} margin={{ top: 8, right: 16, left: 0, bottom: 6 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e6edf5" vertical={false} />
                <XAxis dataKey="period" tick={{ fontSize: 11, fill: '#60758a' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: '#60758a' }} axisLine={false} tickLine={false} />
                <Tooltip
                  formatter={((value: unknown, name: unknown) => [fmtMoney(Number(value)), name === 'realized' ? 'Realizado' : 'Realizado acumulado']) as never}
                  contentStyle={{ borderRadius: 10, border: '1px solid #d6e0eb', fontSize: '0.86rem' }}
                />
                <Line type="monotone" dataKey="realized" stroke="#0f6bb4" strokeWidth={2} dot={{ r: 2 }} name="realized" />
                <Line type="monotone" dataKey="cumulativeRealized" stroke="#16a34a" strokeWidth={3} dot={{ r: 2 }} name="cumulative" />
              </LineChart>
            </ResponsiveContainer>
          )}
        </article>

        <article className="idb-card">
          <div className="sheet-header">
            <h3>Composición por Tipo</h3>
            <span>Distribución por valor actual</span>
          </div>
          {typeComposition.length === 0 ? (
            <p className="idb-empty">No hay tipos de inversión para representar.</p>
          ) : (
            <div className="idb-pie-layout">
              <ResponsiveContainer width="100%" height={240}>
                <PieChart>
                  <Pie data={typeComposition} dataKey="currentValue" nameKey="label" innerRadius={58} outerRadius={95} paddingAngle={2}>
                    {typeComposition.map((_, index) => (
                      <Cell key={index} fill={TYPE_COLORS[index % TYPE_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={((value: unknown) => fmtMoney(Number(value))) as never}
                    contentStyle={{ borderRadius: 10, border: '1px solid #d6e0eb', fontSize: '0.86rem' }}
                  />
                </PieChart>
              </ResponsiveContainer>
              <ul className="idb-legend">
                {typeComposition.map((item, index) => (
                  <li key={item.label}>
                    <span className="idb-dot" style={{ background: TYPE_COLORS[index % TYPE_COLORS.length] }} />
                    <span className="idb-legend-name">{item.label}</span>
                    <strong>{item.sharePct.toFixed(1)}%</strong>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </article>
      </div>

      <div className="idb-grid idb-grid-2">
        <article className="idb-card">
          <div className="sheet-header">
            <h3>Composición por Instrumento</h3>
            <span>Top 8 por valor actual</span>
          </div>
          {topInstrumentBars.length === 0 ? (
            <p className="idb-empty">No hay instrumentos para representar.</p>
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={topInstrumentBars} margin={{ top: 8, right: 16, left: 0, bottom: 6 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e6edf5" vertical={false} />
                <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#60758a' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: '#60758a' }} axisLine={false} tickLine={false} />
                <Tooltip
                  formatter={((value: unknown, name: unknown) => [fmtMoney(Number(value)), name === 'value' ? 'Valor actual' : 'P&L']) as never}
                  contentStyle={{ borderRadius: 10, border: '1px solid #d6e0eb', fontSize: '0.86rem' }}
                />
                <Bar dataKey="value" fill="#0f6bb4" radius={[4, 4, 0, 0]} name="value" />
              </BarChart>
            </ResponsiveContainer>
          )}
        </article>

        <article className="idb-card">
          <div className="idb-tax-header">
            <div className="sheet-header">
              <h3>Resumen Fiscal</h3>
              <span>Ganancia/pérdida realizada</span>
            </div>
            <label className="idb-year-select">
              Año
              <select value={selectedYear} onChange={(event) => setSelectedYear(Number(event.target.value))}>
                {availableYears.map((year) => (
                  <option key={year} value={year}>{year}</option>
                ))}
              </select>
            </label>
          </div>

          <div className="idb-tax-total">
            <span>Total {selectedYear}</span>
            <strong className={pnlClass(taxSummary?.totalGainLossEur ?? 0)}>{fmtMoney(taxSummary?.totalGainLossEur ?? 0)}</strong>
          </div>

          <div className="idb-tax-currency-wrap">
            {(taxSummary?.byCurrency ?? []).map((item) => (
              <span key={item.currency} className={`idb-tax-currency ${pnlClass(item.gainLossEur)}`}>
                {item.currency}: {fmtMoney(item.gainLossEur)}
              </span>
            ))}
          </div>

          <div className="idb-tax-table-wrap">
            <table className="table idb-tax-table">
              <thead>
                <tr>
                  <th>Instrumento</th>
                  <th className="idb-right">Ganancia/Pérdida</th>
                </tr>
              </thead>
              <tbody>
                {(taxSummary?.byInstrument ?? []).length === 0 && (
                  <tr>
                    <td colSpan={2} className="idb-empty">No hay ventas fiscalmente realizadas en este año.</td>
                  </tr>
                )}
                {(taxSummary?.byInstrument ?? []).map((item) => (
                  <tr key={`${item.instrumentId}-${item.code || item.symbol || item.name}`}>
                    <td>
                      <div className="idb-instrument-cell">
                        <strong>{item.symbol || item.code || `#${item.instrumentId}`}</strong>
                        <span>{item.name || 'Instrumento sin nombre'}</span>
                      </div>
                    </td>
                    <td className={`idb-right ${pnlClass(item.gainLossEur)}`}>{fmtMoney(item.gainLossEur)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </article>
      </div>
    </div>
  );
};
