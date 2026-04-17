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
  const { accounts, loading, error } = useAccounts(token);

  if (loading) return <div className="accounts-loading">Cargando cuentas...</div>;
  if (error) return <div className="accounts-error">Error: {error}</div>;
  if (!accounts.length) return <div className="accounts-empty">No hay cuentas registradas.</div>;

  return (
    <div className="accounts-table-wrapper">
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
