import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Account } from '../types/account';
import { BankFormat, CsvImportResult, ImportError } from '../types/banking';
import { fetchAccounts } from '../services/accountsService';
import { dispatchFinanceEvent, FINANCE_EVENTS } from '../events/financeEvents';

interface BankImportPanelProps {
  token: string;
  onUnauthorized?: (message: string) => void;
}

const BANK_OPTIONS: { value: BankFormat; label: string }[] = [
  { value: 'INTERNAL', label: 'Formato interno' },
  { value: 'SANTANDER', label: 'Santander' },
  { value: 'BBVA', label: 'BBVA' },
  { value: 'ING', label: 'ING' },
  { value: 'IMAGIN', label: 'IMAGIN' },
];

export const BankImportPanel: React.FC<BankImportPanelProps> = ({ token, onUnauthorized }) => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountsLoading, setAccountsLoading] = useState(true);

  const [bankFormat, setBankFormat] = useState<BankFormat>('INTERNAL');
  const [accountId, setAccountId] = useState<string>('');
  const [file, setFile] = useState<File | null>(null);
  const [skipDuplicates, setSkipDuplicates] = useState(true);

  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<CsvImportResult | null>(null);
  const [globalError, setGlobalError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!token) return;
    fetchAccounts(token)
      .then((accs) => {
        setAccounts(accs);
        if (accs.length > 0) setAccountId(String(accs[0].id));
      })
      .catch((err) => {
        if (err?.response?.status === 401) onUnauthorized?.('Sesión expirada');
        else setGlobalError('Error cargando cuentas');
      })
      .finally(() => setAccountsLoading(false));
  }, [token, onUnauthorized]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0] ?? null;
    setFile(f);
    setResult(null);
    setGlobalError(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) { setGlobalError('Selecciona un archivo CSV'); return; }
    if (!accountId) { setGlobalError('Selecciona una cuenta destino'); return; }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('accountId', accountId);
    formData.append('skipDuplicates', String(skipDuplicates));
    formData.append('bankFormat', bankFormat);

    setSubmitting(true);
    setResult(null);
    setGlobalError(null);

    try {
      const res = await axios.post<{ data: CsvImportResult }>(
        '/v1/api/transactions/import',
        formData,
        { headers: { Authorization: `Bearer ${token}` } },
      );
      setResult(res.data.data ?? (res.data as unknown as CsvImportResult));
      dispatchFinanceEvent(FINANCE_EVENTS.TRANSACTIONS_UPDATED);
      // reset file input
      setFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err: any) {
      if (err?.response?.status === 401) { onUnauthorized?.('Sesión expirada'); return; }
      const msg = err?.response?.data?.message ?? err?.message ?? 'Error al importar';
      setGlobalError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const allSuccess = result && result.failedCount === 0;

  return (
    <div className="import-panel">
      <h3 className="import-panel__title">Importar movimientos bancarios</h3>

      <form className="import-form" onSubmit={handleSubmit}>
        {/* Bank selector */}
        <div className="import-form__field">
          <label className="import-form__label" htmlFor="bankFormat">Banco / formato</label>
          <select
            id="bankFormat"
            className="import-form__select"
            value={bankFormat}
            onChange={(e) => setBankFormat(e.target.value as BankFormat)}
          >
            {BANK_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </div>

        {/* Account selector */}
        <div className="import-form__field">
          <label className="import-form__label" htmlFor="accountId">Cuenta destino</label>
          {accountsLoading ? (
            <span className="import-form__loading">Cargando cuentas…</span>
          ) : (
            <select
              id="accountId"
              className="import-form__select"
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              required
            >
              <option value="">— selecciona cuenta —</option>
              {accounts.map((a) => (
                <option key={a.id} value={String(a.id)}>
                  {a.name}{a.iban ? ` (${a.iban})` : ''} — {a.currency}
                </option>
              ))}
            </select>
          )}
        </div>

        {/* File input */}
        <div className="import-form__field">
          <label className="import-form__label" htmlFor="csvFile">Archivo CSV</label>
          <input
            ref={fileInputRef}
            id="csvFile"
            type="file"
            accept=".csv,text/csv"
            className="import-form__file"
            onChange={handleFileChange}
            required
          />
          {file && <span className="import-form__filename">{file.name}</span>}
        </div>

        {/* Skip duplicates */}
        <div className="import-form__field import-form__field--inline">
          <input
            id="skipDuplicates"
            type="checkbox"
            className="import-form__checkbox"
            checked={skipDuplicates}
            onChange={(e) => setSkipDuplicates(e.target.checked)}
          />
          <label className="import-form__label import-form__label--inline" htmlFor="skipDuplicates">
            Omitir duplicados automáticamente
          </label>
        </div>

        {globalError && (
          <p className="import-form__error">{globalError}</p>
        )}

        <button
          type="submit"
          className="import-form__submit"
          disabled={submitting || accountsLoading}
        >
          {submitting ? 'Importando…' : 'Importar CSV'}
        </button>
      </form>

      {/* Result */}
      {result && (
        <div className={`import-result ${allSuccess ? 'import-result--ok' : 'import-result--warn'}`}>
          <div className="import-result__summary">
            <div className="import-result__stat">
              <span className="import-result__stat-label">Total filas</span>
              <span className="import-result__stat-value">{result.totalRows}</span>
            </div>
            <div className="import-result__stat import-result__stat--ok">
              <span className="import-result__stat-label">Importadas</span>
              <span className="import-result__stat-value">{result.successCount}</span>
            </div>
            <div className="import-result__stat import-result__stat--skip">
              <span className="import-result__stat-label">Omitidas</span>
              <span className="import-result__stat-value">{result.skippedCount}</span>
            </div>
            <div className="import-result__stat import-result__stat--err">
              <span className="import-result__stat-label">Errores</span>
              <span className="import-result__stat-value">{result.failedCount}</span>
            </div>
          </div>

          {result.errors && result.errors.length > 0 && (
            <div className="import-result__errors">
              <h4 className="import-result__errors-title">Detalle de errores</h4>
              <div className="import-result__table-wrap">
                <table className="import-result__table">
                  <thead>
                    <tr>
                      <th>Fila</th>
                      <th>Campo</th>
                      <th>Mensaje</th>
                      <th>Valor</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.errors.map((err: ImportError, i: number) => (
                      <tr key={i}>
                        <td>{err.rowNumber}</td>
                        <td><code>{err.field}</code></td>
                        <td>{err.message}</td>
                        <td><code>{err.rawValue}</code></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
