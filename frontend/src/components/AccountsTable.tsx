import React from 'react';
import { useAccounts } from '../hooks/useAccounts';
import type { Account } from '../types/account';
import './accounts-table.css';

interface AccountsTableProps {
  token: string;
}

const currencySymbol = (currency: string) => {
  switch (currency) {
    case 'EUR': return '€';
    case 'USD': return '$';
    case 'GBP': return '£';
    default: return currency;
  }
};

const bankIcon = (bankName?: string) => {
  if (!bankName) return <span role="img" aria-label="bank">🏦</span>;
  const map: Record<string, string> = {
    'Santander': '/bank-logos/santanderbank-com-logo.png',
    'BBVA': '/bank-logos/bbva-es-logo.png',
    'ING': '/bank-logos/ing-com-logo.png',
    'MyInvestor': '/bank-logos/myinvestor-es-logo.png',
    'Binance': '/bank-logos/binance-com-logo.png',
    'Revolut': '/bank-logos/revolut-com-logo.png',
    'Interactive Brokers': '/bank-logos/interactivebrokers-com-logo.png',
    'Imagin': '/bank-logos/imagin-com-logo.png',
  };
  const url = map[bankName] || undefined;
  return url
    ? <img src={url} alt={bankName} style={{ width: 32, height: 32, objectFit: 'contain', borderRadius: 4, background: '#f5f6fa', border: '1px solid #eee', display: 'block' }} onError={e => { (e.target as HTMLImageElement).style.display = 'none'; }} />
    : <span role="img" aria-label="bank">🏦</span>;
};

export const AccountsTable: React.FC<AccountsTableProps> = ({ token }) => {
  const { accounts, loading, error, createAccount } = useAccounts(token);
  const [showForm, setShowForm] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [formError, setFormError] = React.useState<string | null>(null);

  const [form, setForm] = React.useState({
    name: '',
    iban: '',
    institutionId: '',
    accountTypeId: '',
    currency: 'EUR',
    lastBalanceReal: ''
  });

  const onChange = (k: string, v: string) => setForm((s) => ({ ...s, [k]: v }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      const payload: any = {
        name: form.name,
        iban: form.iban || null,
        institutionId: form.institutionId ? Number(form.institutionId) : null,
        accountTypeId: form.accountTypeId ? Number(form.accountTypeId) : null,
        currency: form.currency,
        lastBalanceReal: form.lastBalanceReal ? Number(form.lastBalanceReal) : undefined
      };
      await createAccount(payload);
      setShowForm(false);
      setForm({ name: '', iban: '', institutionId: '', accountTypeId: '', currency: 'EUR', lastBalanceReal: '' });
    } catch (err: any) {
      setFormError(err?.message || 'Error creando cuenta');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <div className="accounts-loading">Cargando cuentas...</div>;
  if (error) return <div className="accounts-error">Error: {error}</div>;
  // render even if empty so button is visible

  return (
    <div className="accounts-table-wrapper">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <h3 style={{ margin: 0 }}>Cuentas</h3>
        <div>
          <button className="btn primary" onClick={() => setShowForm((s) => !s)} type="button">{showForm ? 'Cancelar' : 'Añadir cuenta'}</button>
        </div>
      </div>

      {showForm && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal">
            <div className="modal-header">
              <h4>Añadir cuenta</h4>
              <button className="modal-close" onClick={() => setShowForm(false)} type="button">✕</button>
            </div>
            <form onSubmit={submit} className="modal-body">
              <div className="modal-row">
                <label>Nombre</label>
                <input required placeholder="Nombre" value={form.name} onChange={(e) => onChange('name', e.target.value)} />
              </div>
              <div className="modal-row">
                <label>IBAN</label>
                <input placeholder="IBAN" value={form.iban} onChange={(e) => onChange('iban', e.target.value)} />
              </div>
              <div className="modal-row">
                <label>InstitutionId</label>
                <input placeholder="InstitutionId (num)" value={form.institutionId} onChange={(e) => onChange('institutionId', e.target.value)} />
              </div>
              <div className="modal-row">
                <label>AccountTypeId</label>
                <input placeholder="AccountTypeId (num)" value={form.accountTypeId} onChange={(e) => onChange('accountTypeId', e.target.value)} />
              </div>
              <div className="modal-row">
                <label>Currency</label>
                <input placeholder="Currency (EUR)" value={form.currency} onChange={(e) => onChange('currency', e.target.value)} />
              </div>
              <div className="modal-row">
                <label>Balance inicial</label>
                <input placeholder="Balance inicial" value={form.lastBalanceReal} onChange={(e) => onChange('lastBalanceReal', e.target.value)} />
              </div>
              {formError && <div className="modal-error">{formError}</div>}
              <div className="modal-actions">
                <button className="btn primary" type="submit" disabled={submitting}>{submitting ? 'Guardando...' : 'Crear'}</button>
                <button className="btn" type="button" onClick={() => setShowForm(false)}>Cancelar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      <table className="accounts-table">
        <thead>
          <tr>
            <th>Banco</th>
            <th>Nombre</th>
            <th>IBAN</th>
            <th>Tipo</th>
            <th>Balance real</th>
            <th>Balance disponible</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((acc: Account) => (
            <tr key={acc.id}>
              <td className="bank-cell">
                {bankIcon(acc.institutionName)}
                <span>{acc.institutionName || '-'}</span>
              </td>
              <td>{acc.name}</td>
              <td style={{ fontFamily: 'monospace', fontSize: 13 }}>{acc.iban || '-'}</td>
              <td>{acc.accountTypeName || '-'}</td>
              <td className="balance-real">
                {typeof acc.lastBalanceReal === 'number'
                  ? `${acc.lastBalanceReal.toLocaleString('es-ES', { minimumFractionDigits: 2 })} ${currencySymbol(acc.currency)}`
                  : '-'}
              </td>
              <td className="balance-available">
                {typeof acc.lastBalanceAvailable === 'number'
                  ? `${acc.lastBalanceAvailable.toLocaleString('es-ES', { minimumFractionDigits: 2 })} ${currencySymbol(acc.currency)}`
                  : '-'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
