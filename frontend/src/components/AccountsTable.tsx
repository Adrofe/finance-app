import React from 'react';
import { useAccounts } from '../hooks/useAccounts';
import type { Account } from '../types/account';
import type { Institution } from '../types/institution';
import type { AccountType } from '../types/accountType';
import './accounts-table.css';

// --- Small custom dropdown component to display logos inside the option list ---
interface InstitutionDropdownProps {
  institutions: Institution[];
  value: string | number;
  onChange: (id: number | '') => void;
  renderIcon?: (name?: string) => React.ReactNode;
}

const InstitutionDropdown: React.FC<InstitutionDropdownProps> = ({ institutions, value, onChange, renderIcon }) => {
  const [open, setOpen] = React.useState(false);
  const ref = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (!ref.current) return;
      if (!ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, []);

  const selected = institutions.find(i => String(i.id) === String(value));

  return (
    <div className="inst-dropdown" ref={ref}>
      <button type="button" className="inst-toggle" onClick={() => setOpen(o => !o)} aria-haspopup="listbox">
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <span style={{ width: 28, height: 28 }}>{renderIcon?.(selected?.name)}</span>
          <span>{selected ? `${selected.name} (${selected.id})` : '-- Seleccionar institución --'}</span>
        </span>
        <span style={{ marginLeft: 8 }}>{open ? '▴' : '▾'}</span>
      </button>
      {open && (
        <ul className="inst-list" role="listbox">
          {institutions.map(inst => (
            <li key={inst.id} role="option" tabIndex={0} className="inst-item" onClick={() => { onChange(inst.id); setOpen(false); }}>
              <span style={{ width: 28, height: 28, display: 'inline-block', marginRight: 8 }}>{renderIcon?.(inst.name)}</span>
              <span>{inst.name}</span>
              <span style={{ marginLeft: 'auto', color: '#666', fontSize: 12 }}>{inst.id}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

// --- Account type dropdown: similar UI but no logo; show description on hover ---
interface AccountTypeDropdownProps {
  types: AccountType[];
  value: string | number;
  onChange: (id: number | '') => void;
}

const AccountTypeDropdown: React.FC<AccountTypeDropdownProps> = ({ types, value, onChange }) => {
  const [open, setOpen] = React.useState(false);
  const ref = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (!ref.current) return;
      if (!ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('click', handleClick);
    return () => document.removeEventListener('click', handleClick);
  }, []);

  const selected = types.find(t => String(t.id) === String(value));

  return (
    <div className="inst-dropdown" ref={ref}>
      <button type="button" className="inst-toggle" onClick={() => setOpen(o => !o)} aria-haspopup="listbox">
        <span>{selected ? `${selected.name} (${selected.id})` : '-- Seleccionar tipo --'}</span>
        <span style={{ marginLeft: 8 }}>{open ? '▴' : '▾'}</span>
      </button>
      {open && (
        <ul className="inst-list" role="listbox">
          {types.map(t => (
            <li key={t.id} role="option" tabIndex={0} className="inst-item" title={t.description || ''} onClick={() => { onChange(t.id); setOpen(false); }}>
              <span>{t.name}</span>
              <span style={{ marginLeft: 'auto', color: '#666', fontSize: 12 }}>{t.id}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

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
  const { accounts, institutions, accountTypes, loading, error, createAccount, updateAccount } = useAccounts(token);
  const [showForm, setShowForm] = React.useState(false);
  const [editingId, setEditingId] = React.useState<number | null>(null);
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
      if (editingId) {
        await updateAccount(editingId, payload);
      } else {
        await createAccount(payload);
      }
      setShowForm(false);
      setEditingId(null);
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
              <h4>{editingId ? 'Editar cuenta' : 'Añadir cuenta'}</h4>
              <button className="modal-close" onClick={() => { setShowForm(false); setEditingId(null); }} type="button">✕</button>
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
                <label>Institución</label>
                {institutions && institutions.length > 0 ? (
                  <InstitutionDropdown
                    institutions={institutions}
                    value={form.institutionId}
                    onChange={(val) => onChange('institutionId', String(val))}
                    renderIcon={(name) => bankIcon(name)}
                  />
                ) : (
                  <select disabled><option>Cargando instituciones...</option></select>
                )}
              </div>

              <div className="modal-row">
                <label>Tipo de cuenta</label>
                {accountTypes && accountTypes.length > 0 ? (
                  <AccountTypeDropdown types={accountTypes} value={form.accountTypeId} onChange={(val) => onChange('accountTypeId', String(val))} />
                ) : (
                  <select disabled><option>Cargando tipos...</option></select>
                )}
              </div>
              <div className="modal-row">
                <label>Currency</label>
                <input placeholder="Currency (EUR)" value={form.currency} onChange={(e) => onChange('currency', e.target.value)} />
              </div>
              {!editingId && (
                <div className="modal-row">
                  <label>Balance inicial</label>
                  <input placeholder="Balance inicial" value={form.lastBalanceReal} onChange={(e) => onChange('lastBalanceReal', e.target.value)} />
                </div>
              )}
              {formError && <div className="modal-error">{formError}</div>}
              <div className="modal-actions">
                <button className="btn primary" type="submit" disabled={submitting}>{submitting ? 'Guardando...' : (editingId ? 'Guardar' : 'Crear')}</button>
                <button className="btn" type="button" onClick={() => { setShowForm(false); setEditingId(null); }}>Cancelar</button>
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
            <th aria-label="Acciones"></th>
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
              <td>
                <button className="btn icon-btn" type="button" title="Editar" aria-label="Editar cuenta" onClick={() => {
                  // open modal in edit mode
                  setEditingId(acc.id ?? null);
                  setForm({
                    name: acc.name || '',
                    iban: acc.iban || '',
                    institutionId: acc.institutionId ? String(acc.institutionId) : '',
                    accountTypeId: acc.accountTypeId ? String(acc.accountTypeId) : '',
                    currency: acc.currency || 'EUR',
                    lastBalanceReal: acc.lastBalanceReal ? String(acc.lastBalanceReal) : ''
                  });
                  setShowForm(true);
                }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden>
                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="currentColor" />
                    <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="currentColor" />
                  </svg>
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};
