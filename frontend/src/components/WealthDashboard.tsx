import { useMemo, useState } from 'react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { WealthItemType, WealthSnapshotDTO } from '../types/wealth';

type Props = {
  snapshots: WealthSnapshotDTO[];
  onRefresh: () => void;
  refreshing: boolean;
};

type TypeConfig = {
  type: WealthItemType;
  label: string;
  icon: string;
  valueKey: keyof WealthSnapshotDTO;
  color: string;
};

const TYPE_CONFIGS: TypeConfig[] = [
  { type: 'CASH',        label: 'Cash',      icon: '💵', valueKey: 'cashValue',       color: '#16a34a' },
  { type: 'FUND',        label: 'Fondos',    icon: '📦', valueKey: 'fundsValue',      color: '#0f6bb4' },
  { type: 'ETF',         label: 'ETF',       icon: '📈', valueKey: 'etfsValue',       color: '#f59e0b' },
  { type: 'CRYPTO',      label: 'Crypto',    icon: '🪙', valueKey: 'cryptoValue',     color: '#f97316' },
  { type: 'STOCK',       label: 'Acciones',  icon: '📊', valueKey: 'stocksValue',     color: '#8b5cf6' },
  { type: 'BOND',        label: 'Bonos',     icon: '🏦', valueKey: 'bondsValue',      color: '#14b8a6' },
  { type: 'REAL_ESTATE', label: 'Inmuebles', icon: '🏠', valueKey: 'realEstateValue', color: '#6366f1' },
  { type: 'OTHER',       label: 'Otros',     icon: '📁', valueKey: 'otherValue',      color: '#94a3b8' },
];

const fmtMoney = (value: number, currency = 'EUR') =>
  value.toLocaleString('es-ES', { style: 'currency', currency, minimumFractionDigits: 0, maximumFractionDigits: 0 });

const fmtDate = (dateStr: string) =>
  new Date(dateStr + 'T00:00:00').toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });

const fmtShortDate = (dateStr: string) =>
  new Date(dateStr + 'T00:00:00').toLocaleDateString('es-ES', { day: '2-digit', month: 'short' });

const toDateMillis = (dateStr: string) =>
  new Date(dateStr + 'T00:00:00').getTime();

export function WealthDashboard({ snapshots, onRefresh, refreshing }: Props) {
  const [selectedType, setSelectedType] = useState<WealthItemType | null>(null);
  const [evolutionMode, setEvolutionMode] = useState<'total' | 'categorized'>('total');

  const timelineSnapshots = useMemo(
    () => [...snapshots].sort((a, b) => a.snapshotDate.localeCompare(b.snapshotDate)),
    [snapshots],
  );

  const latest  = timelineSnapshots.length > 0 ? timelineSnapshots[timelineSnapshots.length - 1] : null;
  const previous = timelineSnapshots.length > 1 ? timelineSnapshots[timelineSnapshots.length - 2] : null;

  const delta = useMemo(() => {
    if (!latest || !previous) return null;
    const diff = latest.totalValue - previous.totalValue;
    const pct  = previous.totalValue !== 0 ? (diff / previous.totalValue) * 100 : 0;
    return { diff, pct };
  }, [latest, previous]);

  const lineData = useMemo(() => {
    const config = selectedType ? TYPE_CONFIGS.find((c) => c.type === selectedType) : null;
    return timelineSnapshots.map((s) => ({
      timestamp: toDateMillis(s.snapshotDate),
      dateLabel: fmtShortDate(s.snapshotDate),
      value: config ? ((s[config.valueKey] as number) ?? 0) : s.totalValue,
    }));
  }, [timelineSnapshots, selectedType]);

  const categorizedData = useMemo(() => {
    return timelineSnapshots.map((s) => {
      const row: Record<string, number | string> = {
        timestamp: toDateMillis(s.snapshotDate),
        dateLabel: fmtShortDate(s.snapshotDate),
      };
      TYPE_CONFIGS.forEach((c) => {
        row[c.type] = (s[c.valueKey] as number) ?? 0;
      });
      return row;
    });
  }, [timelineSnapshots]);

  const axisDateFormatter = (value: number) =>
    new Date(value).toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: '2-digit' });

  const pieData = useMemo(() => {
    if (!latest) return [];
    return TYPE_CONFIGS
      .map((c) => ({
        name:  c.label,
        icon:  c.icon,
        type:  c.type,
        value: (latest[c.valueKey] as number) ?? 0,
        color: c.color,
      }))
      .filter((d) => d.value > 0);
  }, [latest]);

  const selectedTypeItems = useMemo(() => {
    if (!selectedType || !latest?.items) return [];
    return latest.items.filter((item) => item.type === selectedType);
  }, [selectedType, latest]);

  const lineColor = selectedType
    ? (TYPE_CONFIGS.find((c) => c.type === selectedType)?.color ?? '#0f6bb4')
    : '#0f6bb4';

  const toggleType = (type: WealthItemType) =>
    setSelectedType((prev) => (prev === type ? null : type));

  if (snapshots.length === 0) {
    return (
      <div className="w-empty">
        <div className="w-empty-icon">💎</div>
        <h3>Sin datos de patrimonio</h3>
        <p>Pulsa "Actualizar" para importar automáticamente tus datos desde Banca e Inversiones.</p>
        <button className="btn" type="button" onClick={onRefresh} disabled={refreshing}>
          {refreshing ? 'Actualizando...' : '🔄 Actualizar patrimonio'}
        </button>
      </div>
    );
  }

  return (
    <div className="w-dashboard">

      {/* ── Hero row ── */}
      <div className="w-hero-row">
        <div className="w-hero-card">
          <div className="w-hero-label">Patrimonio neto total</div>
          <div className="w-hero-amount">{fmtMoney(latest!.totalValue, latest!.currency)}</div>
          {delta && (
            <div className={`w-hero-delta ${delta.diff >= 0 ? 'pos' : 'neg'}`}>
              {delta.diff >= 0 ? '↑' : '↓'}{' '}
              {fmtMoney(Math.abs(delta.diff), latest!.currency)}{' '}
              ({delta.pct >= 0 ? '+' : ''}{delta.pct.toFixed(1)}%) desde el snapshot anterior
            </div>
          )}
          <div className="w-hero-date">Último snapshot: {fmtDate(latest!.snapshotDate)}</div>
        </div>
        <div className="w-hero-actions">
          <button className="btn" type="button" onClick={onRefresh} disabled={refreshing}>
            {refreshing
              ? <><span className="w-spinner" />Actualizando...</>
              : '🔄 Actualizar desde banca'}
          </button>
          <span className="w-hero-hint">{snapshots.length} snapshots en historial</span>
        </div>
      </div>

      {/* ── Type mini-cards ── */}
      <div className="w-type-cards">
        {TYPE_CONFIGS.map((c) => {
          const value = (latest![c.valueKey] as number) ?? 0;
          if (value <= 0) return null;
          const pct = latest!.totalValue > 0 ? (value / latest!.totalValue) * 100 : 0;
          return (
            <button
              key={c.type}
              type="button"
              className={`w-type-card${selectedType === c.type ? ' active' : ''}`}
              style={{ '--type-color': c.color } as React.CSSProperties}
              onClick={() => toggleType(c.type)}
            >
              <span className="w-type-card-icon">{c.icon}</span>
              <span className="w-type-card-label">{c.label}</span>
              <span className="w-type-card-amount">{fmtMoney(value, latest!.currency)}</span>
              <span className="w-type-card-pct">{pct.toFixed(1)}%</span>
            </button>
          );
        })}
      </div>

      {/* ── Charts ── */}
      <div className="w-charts-row">
        <div className="w-chart-card">
          <div className="w-chart-head">
            <div className="w-chart-title">
              {evolutionMode === 'categorized'
                ? '🧩 Evolución por categorías'
                : selectedType
                  ? `${TYPE_CONFIGS.find((c) => c.type === selectedType)?.icon} Evolución — ${TYPE_CONFIGS.find((c) => c.type === selectedType)?.label}`
                  : '📈 Evolución del patrimonio'}
            </div>
            <div className="w-chart-mode-toggle" role="group" aria-label="Modo de evolución">
              <button
                type="button"
                className={`w-chart-mode-btn${evolutionMode === 'total' ? ' active' : ''}`}
                onClick={() => setEvolutionMode('total')}
              >
                Total
              </button>
              <button
                type="button"
                className={`w-chart-mode-btn${evolutionMode === 'categorized' ? ' active' : ''}`}
                onClick={() => setEvolutionMode('categorized')}
              >
                Categorizado
              </button>
            </div>
          </div>
          <ResponsiveContainer width="100%" height={240}>
            <AreaChart data={evolutionMode === 'categorized' ? categorizedData : lineData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="wGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%"  stopColor={lineColor} stopOpacity={0.18} />
                  <stop offset="95%" stopColor={lineColor} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#e8eef5" />
              <XAxis
                dataKey="timestamp"
                type="number"
                scale="time"
                domain={['dataMin', 'dataMax']}
                tick={{ fontSize: 11 }}
                tickFormatter={(value) => axisDateFormatter(Number(value))}
              />
              <YAxis
                tick={{ fontSize: 11 }}
                tickFormatter={(v) => `${(Number(v) / 1000).toFixed(0)}k`}
                width={52}
              />
              <Tooltip
                labelFormatter={(label) => fmtDate(new Date(Number(label)).toISOString().slice(0, 10))}
                formatter={(v, name) => {
                  const conf = TYPE_CONFIGS.find((c) => c.type === name);
                  return [fmtMoney(Number(v), latest!.currency), conf ? `${conf.icon} ${conf.label}` : 'Valor'];
                }}
              />
              {evolutionMode === 'categorized' ? (
                TYPE_CONFIGS.map((c) => (
                  <Area
                    key={c.type}
                    type="monotone"
                    stackId="composition"
                    dataKey={c.type}
                    name={c.type}
                    stroke={c.color}
                    strokeWidth={1.5}
                    fill={c.color}
                    fillOpacity={0.72}
                    isAnimationActive
                  />
                ))
              ) : (
                <Area
                  type="monotone"
                  dataKey="value"
                  stroke={lineColor}
                  strokeWidth={2.5}
                  fill="url(#wGrad)"
                  dot={{ r: 3.5, fill: lineColor, strokeWidth: 0 }}
                  activeDot={{ r: 5 }}
                />
              )}
            </AreaChart>
          </ResponsiveContainer>
        </div>

        <div className="w-chart-card">
          <div className="w-chart-title">🥧 Distribución actual</div>
          {pieData.length > 0 ? (
            <>
              <ResponsiveContainer width="100%" height={190}>
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={52}
                    outerRadius={82}
                    dataKey="value"
                    onClick={(d) => d && toggleType((d as unknown as { type: WealthItemType }).type)}
                    style={{ cursor: 'pointer' }}
                  >
                    {pieData.map((entry) => (
                      <Cell
                        key={entry.name}
                        fill={entry.color}
                        opacity={selectedType && selectedType !== entry.type ? 0.3 : 1}
                        stroke={selectedType === entry.type ? '#1a1a1a' : 'none'}
                        strokeWidth={selectedType === entry.type ? 2 : 0}
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(v, _name, props) =>
                      [fmtMoney(Number(v), latest!.currency), (props.payload as { name?: string })?.name ?? '']
                    }
                  />
                </PieChart>
              </ResponsiveContainer>
              <div className="w-legend">
                {pieData.map((d) => (
                  <button
                    key={d.name}
                    type="button"
                    className={`w-legend-item${selectedType === d.type ? ' active' : ''}`}
                    onClick={() => toggleType(d.type)}
                  >
                    <span className="w-legend-dot" style={{ background: d.color }} />
                    <span className="w-legend-name">{d.icon} {d.name}</span>
                    <span className="w-legend-pct">
                      {latest!.totalValue > 0
                        ? ((d.value / latest!.totalValue) * 100).toFixed(1)
                        : '0'}%
                    </span>
                  </button>
                ))}
              </div>
            </>
          ) : (
            <p className="w-no-data">Sin datos de distribución.</p>
          )}
        </div>
      </div>

      {/* ── Type detail ── */}
      {selectedType && (
        <div className="w-type-detail">
          <div className="w-type-detail-header">
            <h4>
              {TYPE_CONFIGS.find((c) => c.type === selectedType)?.icon}{' '}
              {TYPE_CONFIGS.find((c) => c.type === selectedType)?.label} — detalle (último snapshot)
            </h4>
            <button className="btn secondary" type="button" onClick={() => setSelectedType(null)}>
              ✕ Quitar filtro
            </button>
          </div>

          {selectedTypeItems.length > 0 ? (
            <div className="w-items-table-wrapper">
              <table className="w-items-table">
                <thead>
                  <tr>
                    <th>Activo</th>
                    <th>Subtipo</th>
                    <th>Fuente</th>
                    <th className="w-num">Cantidad</th>
                    <th className="w-num">P. Unitario</th>
                    <th className="w-num">Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedTypeItems.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <div className="w-item-label">{item.label}</div>
                        {item.sourceRef && <div className="w-item-ref">{item.sourceRef}</div>}
                      </td>
                      <td>{item.subtype ?? '—'}</td>
                      <td>
                        {item.source
                          ? <span className="w-source-badge" data-source={item.source}>{item.source}</span>
                          : '—'}
                      </td>
                      <td className="w-num">
                        {item.quantity != null
                          ? item.quantity.toLocaleString('es-ES', { maximumFractionDigits: 6 })
                          : '—'}
                      </td>
                      <td className="w-num">
                        {item.unitPrice != null ? fmtMoney(item.unitPrice, item.currency) : '—'}
                      </td>
                      <td className="w-num w-bold">{fmtMoney(item.value, item.currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="w-no-data">
              Sin elementos de este tipo en el último snapshot. Los activos de Banking e Inversiones
              se importan automáticamente al actualizar.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
