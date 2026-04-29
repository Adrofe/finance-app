import React from 'react';
import { useAccounts } from '../hooks/useAccounts';
import type { Account } from '../types/account';
import type { Institution } from '../types/institution';
import type { AccountType } from '../types/accountType';
import { getInstitutionLogo } from '../constants/visualConfig';
import './accounts-table.css';

function BankLogo({ name }: { name?: string }) {
  const src = getInstitutionLogo(name || '');
  if (src.startsWith('/')) {
    return (
      <img
        src={src}
        alt={name || ''}
        className="at-bank-logo"
        onError={e => { (e.target as HTMLImageElement).style.display = 'none'; }}
      />
    );
  }
  return <span className="at-bank-emoji">{src}</span>;
}

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

const fmtBalance = (n: number, currency = 'EUR') =>
  n.toLocaleString('es-ES', { style: 'currency', currency, minimumFractionDigits: 2 });

export const AccountsTable: React.FC<AccountsTableProps> = ({ token }) => {
  const { accounts, institutions, accountTypes, loading, error, createAccount, updateAccount, deleteAccount, clearError } = useAccounts(token);
  const [showForm, setShowForm] = React.useState(false);
  const [editingId, setEditingId] = React.useState<number | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [formError, setFormError] = React.useState<string | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = React.useState<number | null>(null);
  const [confirmDeleteName, setConfirmDeleteName] = React.useState<string | null>(null);
  const [deleting, setDeleting] = React.useState(false);

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

  const totalReal  = accounts.reduce((s, a) => s + (typeof a.lastBalanceReal      === 'number' ? a.lastBalanceReal      : 0), 0);
  const totalAvail  = accounts.reduce((s, a) => s + (typeof a.lastBalanceAvailable === 'number' ? a.lastBalanceAvailable : 0), 0);

  if (loading) return <div className="at-loading">Cargando cuentas…</div>;

  return (
    <div className="at-wrapper">

      {/* ── Summary bar ── */}
      <div className="at-summary-bar">
        <div className="at-summary-stats">
          <div className="at-stat">
            <span className="at-stat-label">Cuentas</span>
            <span className="at-stat-value">{accounts.length}</span>
          </div>
          <div className="at-stat at-stat--balance">
            <span className="at-stat-label">Balance real</span>
            <span className="at-stat-value">{fmtBalance(totalReal)}</span>
          </div>
          <div className="at-stat at-stat--avail">
            <span className="at-stat-label">Disponible</span>
            <span className="at-stat-value">{fmtBalance(totalAvail)}</span>
          </div>
        </div>
        <button
          className={`at-btn-add${showForm ? ' at-btn-add--cancel' : ''}`}
          onClick={() => { setShowForm(s => !s); if (showForm) setEditingId(null); }}
          type="button"
        >
          {showForm ? '✕ Cancelar' : '+ Nueva cuenta'}
        </button>
      </div>

      {error && (
        <div className="at-toast-error" role="alert">
          <span>⚠ {error}</span>
          <button onClick={() => clearError()}>Cerrar</button>
        </div>
      )}

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
                    renderIcon={(name) => <BankLogo name={name} />}
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
                <button className="at-btn-primary" type="submit" disabled={submitting}>{submitting ? 'Guardando…' : (editingId ? 'Guardar' : 'Crear')}</button>
                <button className="at-btn-secondary" type="button" onClick={() => { setShowForm(false); setEditingId(null); }}>Cancelar</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Accounts table ── */}
      <div className="at-container">
        <table className="at-table">
          <thead>
            <tr>
              <th className="at-th">Institución</th>
              <th className="at-th">Cuenta</th>
              <th className="at-th">IBAN</th>
              <th className="at-th">Tipo</th>
              <th className="at-th at-th--right">Balance real</th>
              <th className="at-th at-th--right">Disponible</th>
              <th className="at-th at-th--actions" aria-label="Acciones"></th>
            </tr>
          </thead>
          <tbody>
            {accounts.map((acc: Account) => (
              <tr key={acc.id} className="at-row">

                {/* Institution */}
                <td className="at-td">
                  <div className="at-bank-cell">
                    <BankLogo name={acc.institutionName} />
                    <div className="at-bank-info">
                      <span className="at-bank-name">{acc.institutionName || '—'}</span>
                      {acc.currency && <span className="at-bank-sub">{acc.currency}</span>}
                    </div>
                  </div>
                </td>

                {/* Account name */}
                <td className="at-td">
                  <span className="at-acc-name">{acc.name}</span>
                </td>

                {/* IBAN */}
                <td className="at-td">
                  <span className="at-iban">{acc.iban || '—'}</span>
                </td>

                {/* Type */}
                <td className="at-td">
                  {acc.accountTypeName
                    ? <span className="at-type-badge">{acc.accountTypeName}</span>
                    : <span className="at-empty">—</span>}
                </td>

                {/* Balance real */}
                <td className="at-td at-td--right">
                  {typeof acc.lastBalanceReal === 'number'
                    ? <span className={`at-balance ${acc.lastBalanceReal >= 0 ? 'at-balance--pos' : 'at-balance--neg'}`}>
                        {fmtBalance(acc.lastBalanceReal, acc.currency)}
                      </span>
                    : <span className="at-empty">—</span>}
                </td>

                {/* Balance disponible */}
                <td className="at-td at-td--right">
                  {typeof acc.lastBalanceAvailable === 'number'
                    ? <span className={`at-balance ${acc.lastBalanceAvailable >= 0 ? 'at-balance--avail' : 'at-balance--neg'}`}>
                        {fmtBalance(acc.lastBalanceAvailable, acc.currency)}
                      </span>
                    : <span className="at-empty">—</span>}
                </td>

                {/* Actions */}
                <td className="at-td">
                  <div className="at-actions-cell">
                    <button
                      className="at-icon-btn"
                      type="button"
                      title="Editar"
                      aria-label="Editar cuenta"
                      onClick={() => {
                        setEditingId(acc.id ?? null);
                        setForm({
                          name: acc.name || '',
                          iban: acc.iban || '',
                          institutionId: acc.institutionId ? String(acc.institutionId) : '',
                          accountTypeId: acc.accountTypeId ? String(acc.accountTypeId) : '',
                          currency: acc.currency || 'EUR',
                          lastBalanceReal: acc.lastBalanceReal ? String(acc.lastBalanceReal) : '',
                        });
                        setShowForm(true);
                      }}
                    >
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden>
                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="currentColor" />
                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="currentColor" />
                      </svg>
                    </button>
                    <button
                      className="at-icon-btn at-icon-btn--danger"
                      type="button"
                      title="Eliminar"
                      aria-label="Eliminar cuenta"
                      onClick={() => { setConfirmDeleteId(acc.id ?? null); setConfirmDeleteName(acc.name || null); }}
                    >
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden>
                        <path d="M3 6h18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                        <path d="M8 6V4c0-1.1.9-2 2-2h4c1.1 0 2 .9 2 2v2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                        <path d="M19 6l-1 14c0 .6-.4 1-1 1H7c-.6 0-1-.4-1-1L5 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                        <path d="M10 11v6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                        <path d="M14 11v6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    </button>
                  </div>
                </td>

              </tr>
            ))}
            {accounts.length === 0 && (
              <tr className="at-empty-row">
                <td colSpan={7}>No hay cuentas registradas</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* ── Confirm delete ── */}
      {confirmDeleteId !== null && (
        <div className="at-confirm-overlay" onClick={() => !deleting && (setConfirmDeleteId(null), setConfirmDeleteName(null))}>
          <div className="at-confirm-dialog" onClick={e => e.stopPropagation()}>
            <div className="at-confirm-header">
              <div className="at-confirm-icon">🗑️</div>
              <p className="at-confirm-title">Eliminar cuenta</p>
            </div>
            <p className="at-confirm-body">
              Vas a borrar la cuenta <strong>{confirmDeleteName || confirmDeleteId}</strong> y todas sus transacciones.
              Esta operación <strong>no se puede deshacer</strong>.
            </p>
            {formError && <div className="at-confirm-error">{formError}</div>}
            <div className="at-confirm-actions">
              <button
                className="at-confirm-cancel"
                type="button"
                disabled={deleting}
                onClick={() => { setConfirmDeleteId(null); setConfirmDeleteName(null); setFormError(null); }}
              >
                Cancelar
              </button>
              <button
                className="at-confirm-delete"
                type="button"
                disabled={deleting}
                onClick={async () => {
                  setFormError(null);
                  setDeleting(true);
                  try {
                    await deleteAccount(confirmDeleteId as number);
                    setConfirmDeleteId(null);
                    setConfirmDeleteName(null);
                  } catch (err: any) {
                    setFormError(err?.message || 'Error borrando cuenta');
                  } finally {
                    setDeleting(false);
                  }
                }}
              >
                {deleting ? 'Borrando…' : '🗑️ Borrar cuenta'}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
