import { useEffect, useRef, useState } from 'react';
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
import { fetchInstrumentHistory, updateInstrumentHistoryPrices } from '../services/investmentCatalogService';
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
  onSaved?: () => void;
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

export function InstrumentHistoryModal({ instrumentId, symbol, name, currency, token, onClose, onSaved }: Props) {
  const [range, setRange] = useState<RangeKey>('1Y');
  const [history, setHistory] = useState<InstrumentHistory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [editingPrices, setEditingPrices] = useState(false);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [bulkValue, setBulkValue] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [dateFilter, setDateFilter] = useState('');
  const [inputCurrency, setInputCurrency] = useState(currency);
  const loadedOnce = useRef(false);

  useEffect(() => {
    let active = true;
    if (!loadedOnce.current) setLoading(true);
    setError('');
    fetchInstrumentHistory(token, instrumentId, getRangeStart(range), localDate(new Date()))
      .then((data) => { if (active) { setHistory(data); loadedOnce.current = true; } })
      .catch(() => { if (active) setError('No se pudo cargar el histórico.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [token, instrumentId, range, reloadKey]);

  const points = history?.points ?? [];
  const hasPosition = points.some((point) => point.valueEur != null);
  const editablePoints = [...points].reverse().filter((point) => !dateFilter || point.date === dateFilter);

  const draftOf = (point: InstrumentHistoryPoint) => drafts[point.date] ?? String(point.price);
  const changedPrices = points
    .filter((point) => drafts[point.date] !== undefined && Number(drafts[point.date]) !== point.price)
    .map((point) => ({ date: point.date, price: Number(drafts[point.date]) }));
  const allVisibleSelected = editablePoints.length > 0 && editablePoints.every((point) => selected.has(point.date));

  const toggleSelected = (date: string) => setSelected((current) => {
    const next = new Set(current);
    if (next.has(date)) next.delete(date); else next.add(date);
    return next;
  });

  const toggleAllVisible = () => setSelected((current) => {
    const next = new Set(current);
    editablePoints.forEach((point) => { if (allVisibleSelected) next.delete(point.date); else next.add(point.date); });
    return next;
  });

  const applyBulkValue = () => {
    if (!(Number(bulkValue) > 0)) {
      setSaveError('Indica un valor mayor que cero para las filas seleccionadas.');
      return;
    }
    setSaveError('');
    setDrafts((current) => {
      const next = { ...current };
      selected.forEach((date) => { next[date] = bulkValue; });
      return next;
    });
  };

  const saveAll = async () => {
    if (changedPrices.some((item) => !Number.isFinite(item.price) || item.price <= 0)) {
      setSaveError('Todos los precios deben ser mayores que cero.');
      return;
    }
    setSaving(true);
    setSaveError('');
    try {
      await updateInstrumentHistoryPrices(token, { instrumentId, currency: inputCurrency, prices: changedPrices });
      setDrafts({});
      setSelected(new Set());
      setReloadKey((key) => key + 1);
      onSaved?.();
    } catch (requestError: unknown) {
      const errorMessage = requestError as { response?: { data?: { message?: string } }; message?: string };
      setSaveError(errorMessage.response?.data?.message || errorMessage.message || 'No se pudieron guardar los precios.');
    } finally {
      setSaving(false);
    }
  };

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
          <button
            className={`ih-edit-toggle${editingPrices ? ' active' : ''}`}
            type="button"
            onClick={() => {
              setEditingPrices((enabled) => !enabled);
              setDrafts({});
              setSelected(new Set());
              setSaveError('');
            }}
          >
            {editingPrices ? 'Cerrar edición' : 'Editar precios'}
          </button>
        </div>

        <div className="ih-body">
          {loading && !history ? <p className="ih-state">Cargando histórico…</p> : null}
          {!loading && error ? <p className="ih-state ih-error">{error}</p> : null}
          {!loading && !error && points.length === 0 ? <p className="ih-state">No hay precios históricos guardados para este instrumento.</p> : null}
          {!error && points.length > 0 && (
            <>
              {editingPrices && (
                <section className="ih-price-editor" aria-label="Editar precios históricos">
                  <div className="ih-editor-heading">
                    <div>
                      <h3>Precios guardados</h3>
                      <p>Corregir el último día también actualiza el precio actual y las posiciones.</p>
                    </div>
                    <label>
                      Fecha
                      <input type="date" value={dateFilter} onChange={(event) => setDateFilter(event.target.value)} />
                    </label>
                    {currency !== 'EUR' && (
                      <label>
                        Introducir en
                        <select value={inputCurrency} onChange={(event) => setInputCurrency(event.target.value)}>
                          <option value={currency}>{currency} (divisa del activo)</option>
                          <option value="EUR">EUR (cambio del día)</option>
                        </select>
                      </label>
                    )}
                  </div>
                  {saveError && <p className="ih-editor-error" role="alert">{saveError}</p>}
                  <div className="ih-bulk-bar">
                    <span>{selected.size} seleccionadas</span>
                    <input
                      aria-label="Valor para las filas seleccionadas"
                      type="number"
                      min="0.00000001"
                      step="any"
                      placeholder={`Precio (${inputCurrency})`}
                      value={bulkValue}
                      onChange={(event) => setBulkValue(event.target.value)}
                    />
                    <button type="button" disabled={selected.size === 0} onClick={applyBulkValue}>Rellenar seleccionadas</button>
                    <button type="button" className="ih-save-all" disabled={saving || changedPrices.length === 0} onClick={() => void saveAll()}>
                      {saving ? 'Guardando…' : `Guardar ${changedPrices.length} cambios`}
                    </button>
                  </div>
                  <div className="ih-editor-table-wrap">
                    <table className="ih-editor-table">
                      <thead>
                        <tr>
                          <th><input type="checkbox" aria-label="Seleccionar todas" checked={allVisibleSelected} onChange={toggleAllVisible} /></th>
                          <th>Fecha</th>
                          <th>Precio ({inputCurrency})</th>
                        </tr>
                      </thead>
                      <tbody>
                        {editablePoints.map((point) => {
                          const changed = drafts[point.date] !== undefined && Number(drafts[point.date]) !== point.price;
                          return (
                            <tr key={point.date} className={changed ? 'ih-row-changed' : undefined}>
                              <td><input type="checkbox" aria-label={`Seleccionar ${formatDate(point.date)}`} checked={selected.has(point.date)} onChange={() => toggleSelected(point.date)} /></td>
                              <td>{formatDate(point.date)}</td>
                              <td>
                                <input
                                  aria-label={`Precio para ${formatDate(point.date)}`}
                                  type="number"
                                  min="0.00000001"
                                  step="any"
                                  value={draftOf(point)}
                                  onChange={(event) => setDrafts((current) => ({ ...current, [point.date]: event.target.value }))}
                                />
                              </td>
                            </tr>
                          );
                        })}
                        {editablePoints.length === 0 && <tr><td colSpan={3} className="ih-editor-empty">No hay precios para esa fecha en el rango seleccionado.</td></tr>}
                      </tbody>
                    </table>
                  </div>
                </section>
              )}
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