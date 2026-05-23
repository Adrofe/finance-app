import React, { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import type { ExchangeRateEntry } from '../types/investments';
import {
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
      <div className="fx-hero">
        <div>
          <h3>Histórico de Tipo de Cambio</h3>
          <p>Consulta y completa manualmente series FX para operaciones históricas.</p>
        </div>
        <div className="fx-chip">
          <span>Último</span>
          <strong>{latest ? `${fmtDateDdMmYyyy(latest.asOf)} · ${fmtRate(latest.rate)}` : 'Sin datos'}</strong>
        </div>
      </div>

      <div className="fx-grid">
        <section className="fx-card">
          <h4>Filtro histórico</h4>
          <div className="fx-form-grid">
            <label>
              From
              <input value={fromCurrency} maxLength={3} onChange={(e) => setFromCurrency(e.target.value.toUpperCase())} />
            </label>
            <label>
              To
              <input value={toCurrency} maxLength={3} onChange={(e) => setToCurrency(e.target.value.toUpperCase())} />
            </label>
            <label>
              Desde
              <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            </label>
            <label>
              Hasta
              <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            </label>
          </div>
          <button type="button" className="btn primary" onClick={loadRates} disabled={loading}>
            {loading ? 'Cargando...' : 'Recargar histórico'}
          </button>
        </section>

        <section className="fx-card">
          <h4>Alta manual / BCE por día</h4>
          <form className="fx-form-grid" onSubmit={submitManual}>
            <label>
              Fecha
              <input type="date" value={manualDate} onChange={(e) => setManualDate(e.target.value)} required />
            </label>
            <label>
              Tipo de cambio
              <input type="number" step="0.000001" min="0" value={manualRate} onChange={(e) => setManualRate(e.target.value)} required />
            </label>
            <label>
              Fuente
              <input value={manualSource} onChange={(e) => setManualSource(e.target.value)} />
            </label>
            <div className="fx-actions">
              <button type="submit" className="btn primary" disabled={saving}>{saving ? 'Guardando...' : 'Guardar manual'}</button>
              <button type="button" className="btn secondary" disabled={saving} onClick={refreshFromEcb}>Importar BCE (día)</button>
            </div>
          </form>
        </section>
      </div>

      {error && <p className="state error">{error}</p>}
      {success && <p className="state">{success}</p>}

      <div className="fx-table-wrap">
        <table className="fx-table">
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Par</th>
              <th>Rate</th>
              <th>Fuente</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={4} className="fx-empty">No hay registros para el filtro actual.</td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={`${row.fromCurrency}-${row.toCurrency}-${row.asOf}`}>
                  <td>{fmtDateDdMmYyyy(row.asOf)}</td>
                  <td>{row.fromCurrency}/{row.toCurrency}</td>
                  <td>{fmtRate(row.rate)}</td>
                  <td>{row.source || '—'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
