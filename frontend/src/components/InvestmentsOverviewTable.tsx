import React from 'react';
import { useInvestmentsOverview } from '../hooks/useInvestmentsOverview';
import './investments-overview.css';

type Props = {
  token: string;
  onUnauthorized?: (message: string) => void;
};

const fmtMoney = (value: number, currency = 'EUR') =>
  value.toLocaleString('es-ES', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 });

const fmtPct = (value: number) => `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`;

const fmtQty = (value: number) =>
  value.toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 8 });

const pnlClass = (value: number) => value >= 0 ? 'io-positive' : 'io-negative';

export const InvestmentsOverviewTable: React.FC<Props> = ({ token, onUnauthorized }) => {
  const { summary, positions, byInstrument, loading, error, clearError } = useInvestmentsOverview(token, onUnauthorized);

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
          <span>{byInstrument.length} instrumentos · {positions.length} posiciones</span>
        </div>

        <div className="io-table-wrap">
          <table className="table io-table">
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
