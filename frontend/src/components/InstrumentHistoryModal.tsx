import { useEffect, useState } from 'react';
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { fetchInstrumentHistory } from '../services/investmentCatalogService';
import type { InstrumentHistory, InstrumentHistoryPoint } from '../types/investments';
import './instrument-history-modal.css';

type RangeKey = '3M' | '1Y' | '3Y' | 'ALL';

type Props = {
  instrumentId: number;
  symbol: string;
  name: string;
  currency: string;
  token: string;
  onClose: () => void;
};

const RANGES: { key: RangeKey; label: string }[] = [
  { key: '3M', label: '3M' },
  { key: '1Y', label: '1A' },
  { key: '3Y', label: '3A' },
  { key: 'ALL', label: 'Todo' },
];

function localDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getRangeStart(range: RangeKey): string | undefined {
  if (range === 'ALL') return undefined;
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  start.setMonth(start.getMonth() - (range === '3M' ? 3 : range === '1Y' ? 12 : 36));
  return localDate(start);
}

function formatDate(date: string): string {
  return new Date(`${date}T00:00:00`).toLocaleDateString('es-ES', {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

function formatMoney(value: number, currency: string): string {
  return value.toLocaleString('es-ES', { style: 'currency', currency, maximumFractionDigits: 2 });
}

function HistoryTooltip({
  active,
  payload,
  label,
  currency,
  kind,
}: {
  active?: boolean;
  payload?: { dataKey?: string; value?: number | null; payload?: InstrumentHistoryPoint }[];
  label?: string;
  currency: string;
  kind: 'price' | 'position';
}) {
  if (!active || !payload?.length || !label) return null;
  const values = payload.filter((entry) => typeof entry.value === 'number');
  const point = payload[0]?.payload;
  return (
    <div className="ih-tooltip">
      <strong>{formatDate(label)}</strong>
      {values.map((entry) => {
        const value = entry.value as number;
        const labelText = entry.dataKey === 'price'
          ? 'Precio'
          : entry.dataKey === 'valueEur'
            ? 'Valor de posición'
            : 'Invertido';
        const amount = entry.dataKey === 'price' ? formatMoney(value, currency) : formatMoney(value, 'EUR');
        return <span key={entry.dataKey}>{labelText}: <b>{amount}</b></span>;
      })}
      {kind === 'position' && typeof point?.quantity === 'number' && (
        <span>Cantidad: <b>{point.quantity.toLocaleString('es-ES', { maximumFractionDigits: 8 })}</b></span>
      )}
    </div>
  );
}

export function InstrumentHistoryModal({ instrumentId, symbol, name, currency, token, onClose }: Props) {
  const [range, setRange] = useState<RangeKey>('1Y');
  const [history, setHistory] = useState<InstrumentHistory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    fetchInstrumentHistory(token, instrumentId, getRangeStart(range), localDate(new Date()))
      .then((data) => { if (active) setHistory(data); })
      .catch(() => { if (active) setError('No se pudo cargar el histórico.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [token, instrumentId, range]);

  const points = history?.points ?? [];
  const hasPosition = points.some((point) => point.valueEur != null);

  return (
    <div className="ih-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section className="ih-modal" role="dialog" aria-modal="true" aria-labelledby="ih-title">
        <header className="ih-header">
          <div className="ih-heading">
            <span className="ih-eyebrow">Histórico del instrumento</span>
            <h2 id="ih-title">{symbol}</h2>
            <p>{name}</p>
          </div>
          <button className="ih-close" type="button" onClick={onClose} aria-label="Cerrar histórico" title="Cerrar">×</button>
        </header>

        <div className="ih-toolbar">
          <div className="ih-ranges" role="group" aria-label="Rango temporal">
            {RANGES.map(({ key, label }) => (
              <button
                key={key}
                type="button"
                className={range === key ? 'active' : ''}
                aria-pressed={range === key}
                onClick={() => setRange(key)}
              >
                {label}
              </button>
            ))}
          </div>
          <span className="ih-point-count">{points.length} puntos diarios</span>
        </div>

        <div className="ih-body">
          {loading ? <p className="ih-state">Cargando histórico…</p> : null}
          {!loading && error ? <p className="ih-state ih-error">{error}</p> : null}
          {!loading && !error && points.length === 0 ? <p className="ih-state">No hay precios históricos guardados para este instrumento.</p> : null}
          {!loading && !error && points.length > 0 && (
            <>
              <section className="ih-chart-section">
                <div className="ih-chart-heading">
                  <h3>Precio</h3>
                  <span>{currency}</span>
                </div>
                <div className="ih-chart">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart data={points} margin={{ top: 12, right: 20, left: 4, bottom: 4 }}>
                      <CartesianGrid stroke="#e4e9ed" vertical={false} />
                      <XAxis dataKey="date" tickFormatter={formatDate} minTickGap={32} />
                      <YAxis width={72} tickFormatter={(value: number) => value.toLocaleString('es-ES')} />
                      <Tooltip content={<HistoryTooltip currency={currency} kind="price" />} />
                      <Line type="monotone" dataKey="price" name="Precio" stroke="#087e75" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              </section>

              <section className="ih-chart-section">
                <div className="ih-chart-heading">
                  <h3>Tu posición</h3>
                  <span>EUR</span>
                </div>
                {hasPosition ? (
                  <div className="ih-chart">
                    <ResponsiveContainer width="100%" height="100%">
                      <ComposedChart data={points} margin={{ top: 12, right: 20, left: 4, bottom: 4 }}>
                        <CartesianGrid stroke="#e4e9ed" vertical={false} />
                        <XAxis dataKey="date" tickFormatter={formatDate} minTickGap={32} />
                        <YAxis width={82} tickFormatter={(value: number) => value.toLocaleString('es-ES')} />
                        <Tooltip content={<HistoryTooltip currency={currency} kind="position" />} />
                        <Area type="monotone" dataKey="valueEur" name="Valor de posición" stroke="#087e75" fill="#8dd4c6" fillOpacity={0.28} connectNulls={false} />
                        <Line type="monotone" dataKey="investedEur" name="Invertido" stroke="#c86b24" strokeWidth={2} dot={false} connectNulls={false} />
                      </ComposedChart>
                    </ResponsiveContainer>
                  </div>
                ) : (
                  <p className="ih-no-position">No hay operaciones BUY/SELL registradas para este instrumento.</p>
                )}
                {hasPosition && <p className="ih-footnote">El capital invertido se reconstruye con coste medio a partir de tus operaciones.</p>}
              </section>
            </>
          )}
        </div>
      </section>
    </div>
  );
}