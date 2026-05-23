import React, { useState } from 'react';
import { useInvestmentsOverview } from '../hooks/useInvestmentsOverview';
import { recalculateAllPositions } from '../services/investmentOperationsService';
import './investments-overview.css';

type Props = {
  token: string;
  onUnauthorized?: (message: string) => void;
};

const safeNumber = (value: number | null | undefined) => (typeof value === 'number' && Number.isFinite(value) ? value : 0);

const fmtMoney = (value: number | null | undefined, currency = 'EUR') =>
  safeNumber(value).toLocaleString('es-ES', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 });

const fmtPct = (value: number | null | undefined) => {
  const amount = safeNumber(value);
  return `${amount >= 0 ? '+' : ''}${amount.toFixed(2)}%`;
};

const fmtQty = (value: number | null | undefined) =>
  safeNumber(value).toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 8 });

const pnlClass = (value: number | null | undefined) => safeNumber(value) >= 0 ? 'io-positive' : 'io-negative';

export const InvestmentsOverviewTable: React.FC<Props> = ({ token, onUnauthorized }) => {
  const { summary, positions, byInstrument, loading, error, clearError, reload } = useInvestmentsOverview(token, onUnauthorized);
  const [recalculating, setRecalculating] = useState(false);

  const handleRecalculate = () => {
    setRecalculating(true);
    recalculateAllPositions(token)
      .then(() => reload())
      .catch((err) => console.error('Recalculate failed', err))
      .finally(() => setRecalculating(false));
  };

  if (loading) {
    return <p className="state">Cargando resumen de inversiones...</p>;
  }

  if (error) {
    return (
      <div className="io-state">
        <p className="state error">{error}</p>
        <button className="btn secondary" type="button" onClick={clearError}>Cerrar</button>
      </div>
    );
  }

  if (!summary) {
    return <p className="state">No hay resumen disponible todavía.</p>;
  }

  return (
    <div className="io-wrapper">
      <div className="io-metrics-grid">
        <article className="metric-card">
          <h3>Total Invertido</h3>
          <strong>{fmtMoney(summary.totalInvested)}</strong>
          <span>Capital desplegado en cartera</span>
        </article>

        <article className="metric-card">
          <h3>Valor Actual</h3>
          <strong>{fmtMoney(summary.totalCurrentValue)}</strong>
          <span>Manual o calculado por precio</span>
        </article>

        <article className="metric-card">
          <h3>P&amp;L Total</h3>
          <strong className={pnlClass(summary.totalPnl)}>{fmtMoney(summary.totalPnl)}</strong>
          <span className={pnlClass(summary.totalPnlPct)}>{fmtPct(summary.totalPnlPct)}</span>
        </article>

        <article className="metric-card">
          <h3>Posiciones</h3>
          <strong>{summary.positions}</strong>
          <span>Distribuidas en {byInstrument.length} instrumentos</span>
        </article>
      </div>

      <div className="io-panel">
        <div className="sheet-header">
          <h3>Resumen por Instrumento</h3>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span>{byInstrument.length} instrumentos · {positions.length} posiciones</span>
            <button
              type="button"
              className="btn secondary"
              onClick={handleRecalculate}
              disabled={recalculating}
            >
              {recalculating ? 'Recalculando...' : 'Recalcular posiciones'}
            </button>
          </div>
        </div>
        <div className="io-table-wrap">
          <table className="io-table">
            <thead>
              <tr>
                <th>Instrumento</th>
                <th>Plataformas</th>
                <th>Posiciones</th>
                <th className="io-right">Cantidad</th>
                <th className="io-right">Invertido</th>
                <th className="io-right">Valor actual</th>
                <th className="io-right">P&amp;L</th>
                <th className="io-right">Rentabilidad</th>
              </tr>
            </thead>
            <tbody>
              {byInstrument.length === 0 && (
                <tr>
                  <td colSpan={8} className="io-empty">Todavía no hay inversiones para mostrar.</td>
                </tr>
              )}

              {byInstrument.map((group) => (
                <tr key={group.instrumentId}>
                  <td>
                    <div className="io-instrument">
                      <strong>{group.instrumentSymbol}</strong>
                      <span>{group.instrumentName}</span>
                    </div>
                  </td>
                  <td>{group.platforms.join(', ')}</td>
                  <td>{group.positions}</td>
                  <td className="io-right">{fmtQty(group.quantity)}</td>
                  <td className="io-right">{fmtMoney(group.investedAmount, group.currency)}</td>
                  <td className="io-right">{fmtMoney(group.currentValue, group.currency)}</td>
                  <td className={`io-right ${pnlClass(group.pnl)}`}>{fmtMoney(group.pnl, group.currency)}</td>
                  <td className={`io-right ${pnlClass(group.pnlPct)}`}>{fmtPct(group.pnlPct)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
