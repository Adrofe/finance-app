import { useState, useEffect, useMemo } from 'react';
import { CatalogService } from '../services/catalogService';
import type { TransactionTax } from '../types/banking';
import './TaxWithholdingReport.css';

type Props = {
  token: string;
  onUnauthorized: () => void;
};

export function TaxWithholdingReport({ token, onUnauthorized }: Props) {
  const [records, setRecords] = useState<TransactionTax[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedYear, setSelectedYear] = useState<number | 'all'>('all');
  const [selectedType, setSelectedType] = useState<string>('all');

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    setError(null);
    CatalogService.fetchTaxReport(token)
      .then(setRecords)
      .catch(err => {
        if (err?.response?.status === 401) {
          onUnauthorized();
        } else {
          setError('Error al cargar el informe de retenciones');
        }
      })
      .finally(() => setLoading(false));
  }, [token, onUnauthorized]);

  // Available years from the data
  const availableYears = useMemo(() => {
    const years = new Set<number>();
    records.forEach(r => {
      if (r.bookingDate) {
        years.add(new Date(r.bookingDate).getFullYear());
      }
    });
    return Array.from(years).sort((a, b) => b - a);
  }, [records]);

  // Available tax types from the data
  const availableTypes = useMemo(() => {
    const types = new Map<string, string>();
    records.forEach(r => types.set(r.taxType.code, r.taxType.name));
    return Array.from(types.entries()).map(([code, name]) => ({ code, name }));
  }, [records]);

  // Filter records
  const filtered = useMemo(() => {
    return records.filter(r => {
      const yearMatch = selectedYear === 'all'
        || (r.bookingDate && new Date(r.bookingDate).getFullYear() === selectedYear);
      const typeMatch = selectedType === 'all' || r.taxType.code === selectedType;
      return yearMatch && typeMatch;
    });
  }, [records, selectedYear, selectedType]);

  // Totals
  const totals = useMemo(() => ({
    gross: filtered.reduce((s, r) => s + r.grossAmount, 0),
    tax:   filtered.reduce((s, r) => s + r.taxAmount, 0),
    net:   filtered.reduce((s, r) => s + (r.grossAmount - r.taxAmount), 0),
  }), [filtered]);

  // Summary by tax type
  const byType = useMemo(() => {
    const map = new Map<string, { name: string; gross: number; tax: number; count: number }>();
    filtered.forEach(r => {
      const key = r.taxType.code;
      const existing = map.get(key) || { name: r.taxType.name, gross: 0, tax: 0, count: 0 };
      map.set(key, {
        ...existing,
        gross: existing.gross + r.grossAmount,
        tax:   existing.tax + r.taxAmount,
        count: existing.count + 1,
      });
    });
    return Array.from(map.values()).sort((a, b) => b.tax - a.tax);
  }, [filtered]);

  const fmt = (n: number, currency?: string) =>
    new Intl.NumberFormat('es-ES', { style: 'currency', currency: currency || 'EUR' }).format(n);

  const fmtDate = (d?: string) =>
    d ? new Date(d).toLocaleDateString('es-ES') : '—';

  if (loading) return <p className="twr-state">Cargando retenciones...</p>;
  if (error)   return <p className="twr-state twr-state--error">{error}</p>;

  return (
    <div className="twr">
      <div className="twr-header">
        <h2 className="twr-title">Informe de retenciones fiscales</h2>
        <p className="twr-subtitle">
          Resumen de impuestos retenidos en origen sobre dividendos, intereses y ganancias.
        </p>
      </div>

      {/* Filters */}
      <div className="twr-filters">
        <div className="twr-filter-group">
          <label className="twr-filter-label">Año</label>
          <select
            className="twr-filter-select"
            value={selectedYear}
            onChange={e => setSelectedYear(e.target.value === 'all' ? 'all' : Number(e.target.value))}
          >
            <option value="all">Todos los años</option>
            {availableYears.map(y => (
              <option key={y} value={y}>{y}</option>
            ))}
          </select>
        </div>

        <div className="twr-filter-group">
          <label className="twr-filter-label">Tipo de retención</label>
          <select
            className="twr-filter-select"
            value={selectedType}
            onChange={e => setSelectedType(e.target.value)}
          >
            <option value="all">Todos los tipos</option>
            {availableTypes.map(t => (
              <option key={t.code} value={t.code}>{t.name}</option>
            ))}
          </select>
        </div>
      </div>

      {records.length === 0 ? (
        <div className="twr-empty">
          <span className="twr-empty-icon">🧾</span>
          <p>No hay registros de retenciones aún.</p>
          <small>Crea una transacción y activa "Tiene retención fiscal" para registrarla aquí.</small>
        </div>
      ) : (
        <>
          {/* Summary cards */}
          <div className="twr-cards">
            <div className="twr-card twr-card--gross">
              <span className="twr-card-label">Importe bruto total</span>
              <span className="twr-card-value">{fmt(totals.gross)}</span>
            </div>
            <div className="twr-card twr-card--tax">
              <span className="twr-card-label">Retención total</span>
              <span className="twr-card-value">{fmt(totals.tax)}</span>
            </div>
            <div className="twr-card twr-card--net">
              <span className="twr-card-label">Neto recibido</span>
              <span className="twr-card-value">{fmt(totals.net)}</span>
            </div>
            <div className="twr-card twr-card--pct">
              <span className="twr-card-label">% de retención medio</span>
              <span className="twr-card-value">
                {totals.gross > 0
                  ? ((totals.tax / totals.gross) * 100).toFixed(2) + '%'
                  : '—'}
              </span>
            </div>
          </div>

          {/* By-type breakdown */}
          {byType.length > 1 && (
            <div className="twr-section">
              <h3 className="twr-section-title">Por tipo de retención</h3>
              <div className="twr-type-grid">
                {byType.map(t => (
                  <div key={t.name} className="twr-type-card">
                    <span className="twr-type-name">{t.name}</span>
                    <span className="twr-type-count">{t.count} registros</span>
                    <span className="twr-type-tax">{fmt(t.tax)}</span>
                    <span className="twr-type-gross">sobre {fmt(t.gross)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Detail table */}
          <div className="twr-section">
            <h3 className="twr-section-title">Detalle ({filtered.length})</h3>
            <div className="twr-table-wrap">
              <table className="twr-table">
                <thead>
                  <tr>
                    <th>Fecha</th>
                    <th>Descripción</th>
                    <th>Tipo</th>
                    <th className="twr-num">Bruto</th>
                    <th className="twr-num">Retención</th>
                    <th className="twr-num">Neto</th>
                    <th className="twr-num">%</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const net = r.grossAmount - r.taxAmount;
                    const pct = r.grossAmount > 0
                      ? ((r.taxAmount / r.grossAmount) * 100).toFixed(1) + '%'
                      : '—';
                    return (
                      <tr key={r.id}>
                        <td className="twr-date">{fmtDate(r.bookingDate)}</td>
                        <td className="twr-desc">{r.transactionDescription || '—'}</td>
                        <td>
                          <span className="twr-type-badge">{r.taxType.name}</span>
                        </td>
                        <td className="twr-num">{fmt(r.grossAmount, r.currency)}</td>
                        <td className="twr-num twr-tax">{fmt(r.taxAmount, r.currency)}</td>
                        <td className="twr-num">{fmt(net, r.currency)}</td>
                        <td className="twr-num twr-pct">{pct}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
