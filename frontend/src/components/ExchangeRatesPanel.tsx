import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import type { ExchangeRateEntry } from '../types/investments';
import {
  deleteExchangeRate,
  fetchExchangeRates,
  refreshExchangeRatesForDay,
  upsertExchangeRate,
} from '../services/exchangeRatesService';
import './exchange-rates.css';

type Props = {
  token: string;
  onUnauthorized?: (message: string) => void;
};

const todayIso = () => new Date().toISOString().slice(0, 10);
const ninetyDaysAgoIso = () => {
  const d = new Date();
  d.setDate(d.getDate() - 90);
  return d.toISOString().slice(0, 10);
};

const fmtRate = (rate: number | undefined) => (typeof rate === 'number' ? rate.toFixed(6) : '0.000000');

const fmtDateDdMmYyyy = (isoDate: string | undefined) => {
  if (!isoDate) return '—';
  const [year, month, day] = isoDate.split('-');
  if (!year || !month || !day) return isoDate;
  return `${day}-${month}-${year}`;
};

export function ExchangeRatesPanel({ token, onUnauthorized }: Props) {
  const [fromCurrency, setFromCurrency] = useState('EUR');
  const [toCurrency, setToCurrency] = useState('USD');
  const [fromDate, setFromDate] = useState(ninetyDaysAgoIso());
  const [toDate, setToDate] = useState(todayIso());

  const [rows, setRows] = useState<ExchangeRateEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [manualDate, setManualDate] = useState(todayIso());
  const [manualRate, setManualRate] = useState('1.000000');
  const [manualSource, setManualSource] = useState('MANUAL');
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const loadRates = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchExchangeRates(token, {
        fromCurrency,
        toCurrency,
        from: fromDate,
        to: toDate,
      });
      setRows(data);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
        return;
      }
      setError((err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
        || (err as { message?: string })?.message
        || 'Error loading exchange rates');
    } finally {
      setLoading(false);
    }
  }, [fromCurrency, toCurrency, fromDate, toDate, token, onUnauthorized]);

  useEffect(() => {
    loadRates();
  }, [loadRates]);

  const latest = useMemo(() => rows[0], [rows]);

  const submitManual = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await upsertExchangeRate(token, {
        fromCurrency,
        toCurrency,
        asOf: manualDate,
        rate: Number(manualRate),
        source: manualSource,
      });
      setSuccess(`Cambio ${fromCurrency}/${toCurrency} guardado para ${fmtDateDdMmYyyy(manualDate)}.`);
      await loadRates();
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
        return;
      }
      setError((err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
        || (err as { message?: string })?.message
        || 'Error saving exchange rate');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: ExchangeRateEntry) => {
    if (!window.confirm(`¿Borrar el registro ${row.fromCurrency}/${row.toCurrency} del ${fmtDateDdMmYyyy(row.asOf)}?`)) return;
    setDeletingId(row.id);
    setError(null);
    setSuccess(null);
    try {
      await deleteExchangeRate(token, row.id);
      setSuccess(`Registro ${row.fromCurrency}/${row.toCurrency} (${fmtDateDdMmYyyy(row.asOf)}) eliminado.`);
      setRows((prev) => prev.filter((r) => r.id !== row.id));
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
        return;
      }
      setError((err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
        || (err as { message?: string })?.message
        || 'Error deleting exchange rate');
    } finally {
      setDeletingId(null);
    }
  };

  const refreshFromEcb = async () => {
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      const saved = await refreshExchangeRatesForDay(token, manualDate);
      setSuccess(`BCE importado para ${fmtDateDdMmYyyy(manualDate)}. Registros insertados/actualizados: ${saved}.`);
      await loadRates();
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        onUnauthorized?.('Session expired or invalid token. Please login again.');
        return;
      }
      setError((err as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
        || (err as { message?: string })?.message
        || 'Error importing rates from ECB');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fx-panel">

      {/* ── KPI row ─────────────────────────────────────────────────────── */}
      <div className="fx-kpi-grid">
        <article className="fx-kpi fx-kpi--rate">
          <span className="fx-kpi-icon">💱</span>
          <span className="fx-kpi-label">Último tipo de cambio</span>
          <strong className="fx-kpi-value">{latest ? fmtRate(latest.rate) : '—'}</strong>
          <span className="fx-kpi-sub">{fromCurrency} → {toCurrency}</span>
        </article>

        <article className="fx-kpi fx-kpi--date">
          <span className="fx-kpi-icon">📅</span>
          <span className="fx-kpi-label">Fecha del último registro</span>
          <strong className="fx-kpi-value">{latest ? fmtDateDdMmYyyy(latest.asOf) : '—'}</strong>
          <span className="fx-kpi-sub">{latest?.source ?? 'Sin datos'}</span>
        </article>

        <article className="fx-kpi fx-kpi--count">
          <span className="fx-kpi-icon">📊</span>
          <span className="fx-kpi-label">Registros cargados</span>
          <strong className="fx-kpi-value">{rows.length}</strong>
          <span className="fx-kpi-sub">{fromDate ? `Desde ${fmtDateDdMmYyyy(fromDate)}` : ''}</span>
        </article>
      </div>

      {/* ── Control cards ───────────────────────────────────────────────── */}
      <div className="fx-controls">
        <section className="fx-card">
          <h4 className="fx-card-title">🔍 Filtro histórico</h4>
          <div className="fx-form-grid">
            <label className="fx-label">
              <span>From</span>
              <input
                className="fx-input fx-input--currency"
                value={fromCurrency}
                maxLength={3}
                onChange={(e) => setFromCurrency(e.target.value.toUpperCase())}
              />
            </label>
            <label className="fx-label">
              <span>To</span>
              <input
                className="fx-input fx-input--currency"
                value={toCurrency}
                maxLength={3}
                onChange={(e) => setToCurrency(e.target.value.toUpperCase())}
              />
            </label>
            <label className="fx-label">
              <span>Desde</span>
              <input className="fx-input" type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            </label>
            <label className="fx-label">
              <span>Hasta</span>
              <input className="fx-input" type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            </label>
          </div>
          <button type="button" className="fx-btn fx-btn--primary" onClick={loadRates} disabled={loading}>
            {loading ? '⏳ Cargando…' : '↻ Recargar histórico'}
          </button>
        </section>

        <section className="fx-card">
          <h4 className="fx-card-title">✏️ Alta manual / BCE por día</h4>
          <form className="fx-form-grid" onSubmit={submitManual}>
            <label className="fx-label">
              <span>Fecha</span>
              <input className="fx-input" type="date" value={manualDate} onChange={(e) => setManualDate(e.target.value)} required />
            </label>
            <label className="fx-label">
              <span>Tipo de cambio</span>
              <input className="fx-input" type="number" step="0.000001" min="0" value={manualRate} onChange={(e) => setManualRate(e.target.value)} required />
            </label>
            <label className="fx-label">
              <span>Fuente</span>
              <input className="fx-input" value={manualSource} onChange={(e) => setManualSource(e.target.value)} />
            </label>
            <div className="fx-actions">
              <button type="submit" className="fx-btn fx-btn--primary" disabled={saving}>
                {saving ? '⏳ Guardando…' : '💾 Guardar manual'}
              </button>
              <button type="button" className="fx-btn fx-btn--secondary" disabled={saving} onClick={refreshFromEcb}>
                🏦 Importar BCE
              </button>
            </div>
          </form>
        </section>
      </div>

      {/* ── Feedback ─────────────────────────────────────────────────────── */}
      {error   && <p className="fx-alert fx-alert--error">{error}</p>}
      {success && <p className="fx-alert fx-alert--success">{success}</p>}

      {/* ── Table ────────────────────────────────────────────────────────── */}
      <div className="fx-table-wrap">
        <table className="fx-table">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Par</th>
              <th className="fx-right">Rate</th>
              <th>Fuente</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={5} className="fx-empty">No hay registros para el filtro actual.</td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={`${row.fromCurrency}-${row.toCurrency}-${row.asOf}`} className="fx-row">
                  <td className="fx-mono">{fmtDateDdMmYyyy(row.asOf)}</td>
                  <td>
                    <span className="fx-pair">
                      <strong>{row.fromCurrency}</strong>
                      <span className="fx-arrow">→</span>
                      <strong>{row.toCurrency}</strong>
                    </span>
                  </td>
                  <td className="fx-right fx-mono fx-rate-cell">{fmtRate(row.rate)}</td>
                  <td>
                    <span className={`fx-source-badge ${row.source === 'MANUAL' ? 'fx-source-manual' : 'fx-source-ecb'}`}>
                      {row.source || '—'}
                    </span>
                  </td>
                  <td className="fx-action-cell">
                    {row.source === 'MANUAL' && (
                      <button
                        type="button"
                        className="fx-btn-delete"
                        title="Eliminar registro"
                        disabled={deletingId === row.id}
                        onClick={() => handleDelete(row)}
                      >
                        {deletingId === row.id ? '⏳' : '🗑'}
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
