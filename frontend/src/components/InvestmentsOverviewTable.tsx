import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useInvestmentsOverview } from '../hooks/useInvestmentsOverview';
import { recalculateAllPositions } from '../services/investmentOperationsService';
import { fetchExchangeRates } from '../services/exchangeRatesService';
import { getInvestmentTypeVisual } from '../constants/visualConfig';
import './investments-overview.css';

type SortKey = 'symbol' | 'investedAmount' | 'currentValue' | 'pnl' | 'pnlPct' | 'quantity';
type SortDir = 'asc' | 'desc';

function SortIcon({ active, dir }: { active: boolean; dir: SortDir }) {
  if (!active) return <span className="io-sort-icon io-sort-inactive">⇅</span>;
  return <span className="io-sort-icon">{dir === 'asc' ? '↑' : '↓'}</span>;
}

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
  safeNumber(value).toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 6 });

const pnlClass = (value: number | null | undefined) => safeNumber(value) >= 0 ? 'io-pos' : 'io-neg';

export const InvestmentsOverviewTable: React.FC<Props> = ({ token, onUnauthorized }) => {
  const { summary, positions, byInstrument, loading, error, clearError, reload } = useInvestmentsOverview(token, onUnauthorized);
  const [recalculating, setRecalculating] = useState(false);
  const [search, setSearch] = useState('');
  const [sortKey, setSortKey] = useState<SortKey>('currentValue');
  const [sortDir, setSortDir] = useState<SortDir>('desc');
  const [normalizeToEur, setNormalizeToEur] = useState(false);
  const [eurRates, setEurRates] = useState<Map<string, number>>(new Map());
  const [loadingRates, setLoadingRates] = useState(false);

  const foreignCurrenciesStr = useMemo(
    () => [...new Set(byInstrument.map((g) => g.currency).filter((c) => c && c !== 'EUR'))].sort().join(','),
    [byInstrument],
  );

  useEffect(() => {
    if (!foreignCurrenciesStr) return;
    const currencies = foreignCurrenciesStr.split(',');
    setLoadingRates(true);
    Promise.all(
      currencies.map((curr) =>
        fetchExchangeRates(token, { fromCurrency: 'EUR', toCurrency: curr })
          .then((rates) => ({ currency: curr, rate: rates.length > 0 ? rates[0].rate : null }))
          .catch(() => ({ currency: curr, rate: null as number | null })),
      ),
    )
      .then((results) => {
        const map = new Map<string, number>();
        for (const { currency, rate } of results) {
          if (rate !== null) map.set(currency, rate);
        }
        setEurRates(map);
      })
      .finally(() => setLoadingRates(false));
  }, [token, foreignCurrenciesStr]);

  const toEur = useCallback(
    (value: number, currency: string): number => {
      if (currency === 'EUR') return value;
      const rate = eurRates.get(currency);
      if (!rate) return value;
      return value / rate;
    },
    [eurRates],
  );

  const eurSummary = useMemo(() => {
    if (!normalizeToEur) return null;
    let invested = 0;
    let current = 0;
    for (const g of byInstrument) {
      invested += toEur(g.investedAmount, g.currency);
      current += toEur(g.currentValue, g.currency);
    }
    const pnl = current - invested;
    const pnlPct = invested > 0 ? (pnl / invested) * 100 : 0;
    return { totalInvested: invested, totalCurrentValue: current, totalPnl: pnl, totalPnlPct: pnlPct };
  }, [normalizeToEur, byInstrument, toEur]);

  const handleSort = (key: SortKey) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('desc');
    }
  };

  const handleRecalculate = () => {
    setRecalculating(true);
    recalculateAllPositions(token)
      .then(() => reload())
      .catch((err) => console.error('Recalculate failed', err))
      .finally(() => setRecalculating(false));
  };

  const displayRows = useMemo(() => {
    const q = search.toLowerCase().trim();
    const filtered = q
      ? byInstrument.filter(
          (g) =>
            g.instrumentSymbol.toLowerCase().includes(q) ||
            g.instrumentName.toLowerCase().includes(q) ||
            g.typeCode.toLowerCase().includes(q) ||
            g.platforms.some((p) => p.toLowerCase().includes(q)),
        )
      : byInstrument;

    return [...filtered].sort((a, b) => {
      let cmp = 0;
      switch (sortKey) {
        case 'symbol':         cmp = a.instrumentSymbol.localeCompare(b.instrumentSymbol); break;
        case 'investedAmount': cmp = (normalizeToEur ? toEur(a.investedAmount, a.currency) : a.investedAmount) - (normalizeToEur ? toEur(b.investedAmount, b.currency) : b.investedAmount); break;
        case 'currentValue':   cmp = (normalizeToEur ? toEur(a.currentValue, a.currency) : a.currentValue) - (normalizeToEur ? toEur(b.currentValue, b.currency) : b.currentValue); break;
        case 'pnl':            cmp = (normalizeToEur ? toEur(a.pnl, a.currency) : a.pnl) - (normalizeToEur ? toEur(b.pnl, b.currency) : b.pnl); break;
        case 'pnlPct':         cmp = a.pnlPct - b.pnlPct; break;
        case 'quantity':       cmp = a.quantity - b.quantity; break;
      }
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [byInstrument, search, sortKey, sortDir, normalizeToEur, toEur]);

  if (loading) {
    return <p className="state">Cargando resumen de inversiones…</p>;
  }

  if (error) {
    return (
      <div className="io-state-row">
        <p className="state error">{error}</p>
        <button className="btn secondary" type="button" onClick={clearError}>Cerrar</button>
      </div>
    );
  }

  if (!summary) {
    return <p className="state">No hay resumen disponible todavía.</p>;
  }

  const displaySummary = eurSummary ?? summary;
  const pnlPositive = safeNumber(displaySummary.totalPnl) >= 0;

  return (
    <div className="io-wrapper">

      {/* ── KPI cards ───────────────────────────────────────────────────── */}
      <div className="io-kpi-grid">
        <article className="io-kpi io-kpi--invested">
          <span className="io-kpi-icon">💰</span>
          <span className="io-kpi-label">Total invertido</span>
          <strong className="io-kpi-value">{fmtMoney(displaySummary.totalInvested)}</strong>
          <span className="io-kpi-sub">{normalizeToEur ? 'Normalizado a EUR' : 'Capital desplegado'}</span>
        </article>

        <article className="io-kpi io-kpi--current">
          <span className="io-kpi-icon">📈</span>
          <span className="io-kpi-label">Valor actual</span>
          <strong className="io-kpi-value">{fmtMoney(displaySummary.totalCurrentValue)}</strong>
          <span className="io-kpi-sub">{normalizeToEur ? 'Normalizado a EUR' : 'Manual o por precio'}</span>
        </article>

        <article className={`io-kpi ${pnlPositive ? 'io-kpi--gain' : 'io-kpi--loss'}`}>
          <span className="io-kpi-icon">{pnlPositive ? '🟢' : '🔴'}</span>
          <span className="io-kpi-label">P&amp;L total</span>
          <strong className={`io-kpi-value ${pnlClass(displaySummary.totalPnl)}`}>
            {fmtMoney(displaySummary.totalPnl)}
          </strong>
          <span className={`io-kpi-sub ${pnlClass(displaySummary.totalPnlPct)}`}>
            {fmtPct(displaySummary.totalPnlPct)}
          </span>
        </article>

        <article className="io-kpi io-kpi--positions">
          <span className="io-kpi-icon">🏦</span>
          <span className="io-kpi-label">Posiciones</span>
          <strong className="io-kpi-value">{summary.positions}</strong>
          <span className="io-kpi-sub">{byInstrument.length} instrumentos</span>
        </article>
      </div>

      {/* ── Toolbar ──────────────────────────────────────────────────────── */}
      <div className="io-toolbar">
        <div className="io-search-wrap">
          <span className="io-search-icon">🔍</span>
          <input
            type="text"
            className="io-search"
            placeholder="Filtrar por símbolo, nombre, tipo o plataforma…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          {search && (
            <button type="button" className="io-search-clear" onClick={() => setSearch('')}>✕</button>
          )}
        </div>
        <div className="io-toolbar-right">
          <span className="io-count">
            {displayRows.length} / {byInstrument.length} instrumentos · {positions.length} posiciones
          </span>
          <button
            type="button"
            className={`io-btn-eur-toggle${normalizeToEur ? ' io-btn-eur-toggle--active' : ''}`}
            onClick={() => setNormalizeToEur((v) => !v)}
            disabled={loadingRates}
            title={normalizeToEur ? 'Mostrando valores en EUR' : 'Normalizar todo a EUR'}
          >
            {loadingRates ? '⏳' : '€'} {normalizeToEur ? 'EUR' : 'Moneda'}
          </button>
          <button
            type="button"
            className="io-btn-recalc"
            onClick={handleRecalculate}
            disabled={recalculating}
          >
            {recalculating ? '⏳ Recalculando…' : '↻ Recalcular'}
          </button>
        </div>
      </div>

      {/* ── Table ────────────────────────────────────────────────────────── */}
      <div className="io-table-wrap">
        <table className="io-table">
          <thead>
            <tr>
              <th className="io-th-sortable" onClick={() => handleSort('symbol')}>
                Instrumento <SortIcon active={sortKey === 'symbol'} dir={sortDir} />
              </th>
              <th>Plataformas</th>
              <th className="io-right">Pos.</th>
              <th className="io-right io-th-sortable" onClick={() => handleSort('quantity')}>
                Cantidad <SortIcon active={sortKey === 'quantity'} dir={sortDir} />
              </th>
              <th className="io-right io-th-sortable" onClick={() => handleSort('investedAmount')}>
                Invertido <SortIcon active={sortKey === 'investedAmount'} dir={sortDir} />
              </th>
              <th className="io-right io-th-sortable" onClick={() => handleSort('currentValue')}>
                Valor actual <SortIcon active={sortKey === 'currentValue'} dir={sortDir} />
              </th>
              <th className="io-right io-th-sortable" onClick={() => handleSort('pnl')}>
                P&amp;L <SortIcon active={sortKey === 'pnl'} dir={sortDir} />
              </th>
              <th className="io-right io-th-sortable" onClick={() => handleSort('pnlPct')}>
                Rentab. <SortIcon active={sortKey === 'pnlPct'} dir={sortDir} />
              </th>
            </tr>
          </thead>
          <tbody>
            {displayRows.length === 0 && (
              <tr>
                <td colSpan={8} className="io-empty">
                  {search ? 'No hay instrumentos que coincidan con el filtro.' : 'Todavía no hay inversiones para mostrar.'}
                </td>
              </tr>
            )}

            {displayRows.map((group) => {
              const visual = getInvestmentTypeVisual(group.typeCode, group.typeName);
              const pnlPos = group.pnl >= 0;
              return (
                <tr key={group.instrumentId} className="io-row">
                  <td>
                    <div className="io-instrument-cell">
                      <span
                        className="io-type-badge"
                        style={{ background: visual.background, color: visual.color }}
                        title={group.typeName || group.typeCode}
                      >
                        {visual.emoji}
                      </span>
                      <div className="io-instrument-info">
                        <strong className="io-symbol">{group.instrumentSymbol}</strong>
                        <span className="io-name">{group.instrumentName}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div className="io-platforms">
                      {group.platforms.map((p) => (
                        <span key={p} className="io-platform-chip">{p}</span>
                      ))}
                    </div>
                  </td>
                  <td className="io-right io-dim">{group.positions}</td>
                  <td className="io-right io-mono">{fmtQty(group.quantity)}</td>
                  <td className="io-right io-mono">{fmtMoney(normalizeToEur ? toEur(group.investedAmount, group.currency) : group.investedAmount, normalizeToEur ? 'EUR' : group.currency)}</td>
                  <td className="io-right io-mono">{fmtMoney(normalizeToEur ? toEur(group.currentValue, group.currency) : group.currentValue, normalizeToEur ? 'EUR' : group.currency)}</td>
                  <td className="io-right">
                    <span className={`io-pnl-badge ${pnlPos ? 'io-pnl-pos' : 'io-pnl-neg'}`}>
                      {pnlPos ? '▲' : '▼'} {fmtMoney(Math.abs(normalizeToEur ? toEur(group.pnl, group.currency) : group.pnl), normalizeToEur ? 'EUR' : group.currency)}
                    </span>
                  </td>
                  <td className="io-right">
                    <span className={`io-pct-badge ${pnlPos ? 'io-pct-pos' : 'io-pct-neg'}`}>
                      {fmtPct(group.pnlPct)}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

    </div>
  );
};
