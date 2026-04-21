
import { useState } from 'react';
import type { Transaction } from '../types/banking';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import { CreateTransactionModal } from './CreateTransactionModal';
import { getCategoryVisual, getInstitutionLogo, getMerchantLogo } from '../constants/visualConfig';
import './TransactionsTable.css';

type TransactionsTableProps = {
  items: Transaction[];
  accessToken: string;
  onRefresh?: () => void;
};

const STATUS_STYLES: Record<string, { background: string; color: string }> = {
  BOOKED:    { background: '#dcfce7', color: '#166534' },
  PENDING:   { background: '#fef9c3', color: '#854d0e' },
  CANCELLED: { background: '#f3f4f6', color: '#6b7280' },
  REJECTED:  { background: '#fee2e2', color: '#991b1b' },
};

function BankLogo({ name }: { name?: string }) {
  const src = getInstitutionLogo(name || '');
  if (src.startsWith('/')) {
    return (
      <img
        src={src}
        alt={name || ''}
        className="tt-bank-logo"
        onError={e => { (e.target as HTMLImageElement).style.display = 'none'; }}
      />
    );
  }
  return <span className="tt-bank-emoji">{src}</span>;
}

function formatDate(dateStr?: string): { day: string; rest: string } {
  if (!dateStr) return { day: '—', rest: '' };
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return { day: '—', rest: '' };
  return {
    day: d.getDate().toString().padStart(2, '0'),
    rest: d.toLocaleDateString('es-ES', { month: 'short', year: 'numeric' }),
  };
}

export function TransactionsTable({ items, accessToken, onRefresh }: TransactionsTableProps) {
  const {
    statusMap,
    typeMap,
    categoryMap,
    categoryCodeMap,
    accountDetailMap,
    tagMap,
  } = useTransactionCatalogs(accessToken);

  const [showCreateModal, setShowCreateModal] = useState(false);

  const handleSuccess = () => {
    if (onRefresh) onRefresh();
  };

  const totalIncome   = items.filter(t => (t.amount ?? 0) > 0).reduce((s, t) => s + (t.amount ?? 0), 0);
  const totalExpenses = items.filter(t => (t.amount ?? 0) < 0).reduce((s, t) => s + (t.amount ?? 0), 0);
  const net           = totalIncome + totalExpenses;

  const fmt = (n: number) => n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });

  return (
    <div className="tt-wrapper">
      {showCreateModal && (
        <CreateTransactionModal
          accessToken={accessToken}
          onClose={() => setShowCreateModal(false)}
          onSuccess={handleSuccess}
        />
      )}

      {/* ── Summary bar ── */}
      <div className="tt-summary-bar">
        <div className="tt-summary-stats">
          <div className="tt-stat">
            <span className="tt-stat-label">Transacciones</span>
            <span className="tt-stat-value">{items.length}</span>
          </div>
          <div className="tt-stat tt-stat--income">
            <span className="tt-stat-label">Ingresos</span>
            <span className="tt-stat-value">{fmt(totalIncome)}</span>
          </div>
          <div className="tt-stat tt-stat--expense">
            <span className="tt-stat-label">Gastos</span>
            <span className="tt-stat-value">{fmt(totalExpenses)}</span>
          </div>
          <div className={`tt-stat tt-stat--net ${net >= 0 ? 'positive' : 'negative'}`}>
            <span className="tt-stat-label">Neto</span>
            <span className="tt-stat-value">{fmt(net)}</span>
          </div>
        </div>
        <button className="btn primary" onClick={() => setShowCreateModal(true)}>
          + Nueva Transacción
        </button>
      </div>

      {/* ── Table ── */}
      <div className="tt-container">
        <table className="tt-table">
          <thead>
            <tr>
              <th className="tt-th tt-th-date">Fecha</th>
              <th className="tt-th tt-th-desc">Descripción</th>
              <th className="tt-th tt-th-amount">Importe</th>
              <th className="tt-th tt-th-cat">Categoría</th>
              <th className="tt-th tt-th-acc">Cuenta Origen</th>
              <th className="tt-th tt-th-acc">Cuenta Destino</th>
              <th className="tt-th tt-th-status">Estado</th>
              <th className="tt-th tt-th-type">Tipo</th>
              <th className="tt-th tt-th-tags">Tags</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 ? (
              <tr className="tt-empty-row">
                <td colSpan={9}>No hay transacciones</td>
              </tr>
            ) : items.map((tx, index) => {
              const { day, rest } = formatDate(tx.bookingDate);
              const amount = tx.amount ?? 0;
              const isNeg  = amount < 0;

              const catCode   = tx.categoryId != null ? (categoryCodeMap[tx.categoryId] ?? '') : '';
              const catName   = tx.categoryId != null ? (categoryMap[tx.categoryId]     ?? '') : '';
              const catVisual = getCategoryVisual(catCode || 'OTH');

              const srcAcc = tx.sourceAccountId      != null ? accountDetailMap[tx.sourceAccountId]      : undefined;
              const dstAcc = tx.destinationAccountId != null ? accountDetailMap[tx.destinationAccountId] : undefined;

              const merchantEmoji = tx.merchantName ? getMerchantLogo(tx.merchantName) : '';

              const statusCode  = tx.statusId != null ? statusMap[tx.statusId] : undefined;
              const statusStyle = statusCode ? (STATUS_STYLES[statusCode] ?? STATUS_STYLES.BOOKED) : undefined;

              const typeName = tx.typeId != null ? typeMap[tx.typeId] : undefined;
              const typeClass = typeName ? `tt-type-badge tt-type-${typeName.toUpperCase()}` : 'tt-type-badge';

              return (
                <tr key={tx.id ?? tx.externalId ?? index} className="tt-row">

                  {/* Date */}
                  <td className="tt-td tt-td-date">
                    <span className="tt-date-day">{day}</span>
                    <span className="tt-date-rest">{rest}</span>
                  </td>

                  {/* Description + merchant */}
                  <td className="tt-td tt-td-desc">
                    <div className="tt-desc-wrap">
                      {tx.description && (
                        <span className="tt-desc-primary">{tx.description}</span>
                      )}
                      {tx.merchantName && (
                        <span className="tt-desc-merchant">
                          {merchantEmoji} {tx.merchantName}
                        </span>
                      )}
                      {!tx.description && !tx.merchantName && (
                        <span className="tt-desc-empty">—</span>
                      )}
                    </div>
                  </td>

                  {/* Amount */}
                  <td className="tt-td tt-td-amount">
                    <span className={`tt-amount ${isNeg ? 'tt-amount--neg' : 'tt-amount--pos'}`}>
                      {typeof tx.amount === 'number' ? fmt(tx.amount) : '—'}
                    </span>
                  </td>

                  {/* Category */}
                  <td className="tt-td tt-td-cat">
                    {catCode ? (
                      <span
                        className="tt-category-badge"
                        style={{
                          background:   `${catVisual.color}22`,
                          color:         catVisual.color,
                          borderColor:  `${catVisual.color}55`,
                        }}
                      >
                        <span className="tt-cat-emoji">{catVisual.emoji}</span>
                        <span className="tt-cat-name">{catName}</span>
                      </span>
                    ) : (
                      <span className="tt-empty-cell">—</span>
                    )}
                  </td>

                  {/* Source account */}
                  <td className="tt-td tt-td-acc">
                    {srcAcc ? (
                      <div className="tt-account-cell">
                        <BankLogo name={srcAcc.institutionName} />
                        <div className="tt-account-info">
                          <span className="tt-account-name">{srcAcc.name}</span>
                          {srcAcc.institutionName && (
                            <span className="tt-account-bank">{srcAcc.institutionName}</span>
                          )}
                        </div>
                      </div>
                    ) : <span className="tt-empty-cell">—</span>}
                  </td>

                  {/* Destination account */}
                  <td className="tt-td tt-td-acc">
                    {dstAcc ? (
                      <div className="tt-account-cell">
                        <BankLogo name={dstAcc.institutionName} />
                        <div className="tt-account-info">
                          <span className="tt-account-name">{dstAcc.name}</span>
                          {dstAcc.institutionName && (
                            <span className="tt-account-bank">{dstAcc.institutionName}</span>
                          )}
                        </div>
                      </div>
                    ) : <span className="tt-empty-cell">—</span>}
                  </td>

                  {/* Status */}
                  <td className="tt-td tt-td-status">
                    {statusCode && statusStyle ? (
                      <span className="tt-status-badge" style={statusStyle}>
                        {statusCode}
                      </span>
                    ) : <span className="tt-empty-cell">—</span>}
                  </td>

                  {/* Type */}
                  <td className="tt-td tt-td-type">
                    {typeName ? (
                      <span className={typeClass}>{typeName}</span>
                    ) : <span className="tt-empty-cell">—</span>}
                  </td>

                  {/* Tags */}
                  <td className="tt-td tt-td-tags">
                    {tx.tagIds && tx.tagIds.length > 0 ? (
                      <div className="tt-tags-wrap">
                        {tx.tagIds.map(id => (
                          <span key={id} className="tt-tag">
                            {tagMap[id] ?? `#${id}`}
                          </span>
                        ))}
                      </div>
                    ) : <span className="tt-empty-cell">—</span>}
                  </td>

                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

