
import { useState } from 'react';
import type { Transaction } from '../types/banking';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import { CreateTransactionModal } from './CreateTransactionModal';
import { getCategoryVisual, getInstitutionLogo, getMerchantLogo } from '../constants/visualConfig';
import { deleteTransaction } from '../services/transactionsService';
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

/** Pending confirmation modal state */
type ConfirmState =
  | { mode: 'single'; tx: Transaction }
  | { mode: 'bulk';   ids: number[] };

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

  const [showCreateModal, setShowCreateModal]   = useState(false);
  const [selectMode,      setSelectMode]        = useState(false);
  const [selected,        setSelected]          = useState<Set<number>>(new Set());
  const [confirm,         setConfirm]           = useState<ConfirmState | null>(null);
  const [deleting,        setDeleting]          = useState(false);

  const handleSuccess = () => { if (onRefresh) onRefresh(); };

  // Toggle individual row selection
  const toggleSelect = (id: number) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  // Select-all / deselect-all
  const allIds      = items.map(t => t.id).filter((id): id is number => id != null);
  const allSelected = allIds.length > 0 && allIds.every(id => selected.has(id));

  const toggleSelectAll = () => {
    setSelected(allSelected ? new Set() : new Set(allIds));
  };

  // Exit select mode and clear
  const exitSelectMode = () => {
    setSelectMode(false);
    setSelected(new Set());
  };

  // Confirm and execute deletion
  const handleConfirmDelete = async () => {
    if (!confirm) return;
    setDeleting(true);
    try {
      if (confirm.mode === 'single') {
        await deleteTransaction(accessToken, confirm.tx.id!);
      } else {
        await Promise.all(confirm.ids.map(id => deleteTransaction(accessToken, id)));
      }
      setConfirm(null);
      exitSelectMode();
      if (onRefresh) onRefresh();
    } finally {
      setDeleting(false);
    }
  };

  const totalIncome   = items.filter(t => (t.amount ?? 0) > 0).reduce((s, t) => s + (t.amount ?? 0), 0);
  const totalExpenses = items.filter(t => (t.amount ?? 0) < 0).reduce((s, t) => s + (t.amount ?? 0), 0);
  const net           = totalIncome + totalExpenses;

  const fmt = (n: number) => n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });

  const totalCols = selectMode ? 11 : 10;

  return (
    <div className="tt-wrapper">

      {/* ── Create modal ── */}
      {showCreateModal && (
        <CreateTransactionModal
          accessToken={accessToken}
          onClose={() => setShowCreateModal(false)}
          onSuccess={handleSuccess}
        />
      )}

      {/* ── Confirm delete dialog ── */}
      {confirm && (
        <div className="tt-confirm-overlay" onClick={() => !deleting && setConfirm(null)}>
          <div className="tt-confirm-dialog" onClick={e => e.stopPropagation()}>
            <div className="tt-confirm-header">
              <div className="tt-confirm-icon">🗑️</div>
              <p className="tt-confirm-title">
                {confirm.mode === 'single'
                  ? 'Eliminar transacción'
                  : `Eliminar ${confirm.ids.length} transacciones`}
              </p>
            </div>
            <p className="tt-confirm-body">
              {confirm.mode === 'single' ? (
                <>Esta acción eliminará permanentemente la transacción
                  {confirm.tx.description ? <> <strong>"{confirm.tx.description}"</strong></> : ''}.
                  Esta operación <strong>no se puede deshacer</strong>.</>
              ) : (
                <>Esta acción eliminará permanentemente <strong>{confirm.ids.length} transacciones</strong> seleccionadas.
                  Esta operación <strong>no se puede deshacer</strong>.</>
              )}
            </p>
            <div className="tt-confirm-actions">
              <button className="tt-confirm-cancel" onClick={() => setConfirm(null)} disabled={deleting}>
                Cancelar
              </button>
              <button className="tt-confirm-delete" onClick={handleConfirmDelete} disabled={deleting}>
                {deleting ? 'Eliminando…' : '🗑️ Eliminar'}
              </button>
            </div>
          </div>
        </div>
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
        <div className="tt-toolbar-right">
          {selectMode && selected.size > 0 && (
            <button
              className="btn-delete-selected"
              onClick={() => setConfirm({ mode: 'bulk', ids: Array.from(selected) })}
            >
              🗑️ Eliminar {selected.size} seleccionadas
            </button>
          )}
          <button
            className={`btn-select-mode ${selectMode ? 'active' : ''}`}
            onClick={() => selectMode ? exitSelectMode() : setSelectMode(true)}
          >
            {selectMode ? '✕ Cancelar selección' : '☑ Seleccionar'}
          </button>
          <button className="btn primary" onClick={() => setShowCreateModal(true)}>
            + Nueva Transacción
          </button>
        </div>
      </div>

      {/* ── Table ── */}
      <div className="tt-container">
        <table className="tt-table">
          <thead>
            <tr>
              {selectMode && (
                <th className="tt-th tt-th-check">
                  <input
                    type="checkbox"
                    className="tt-checkbox"
                    checked={allSelected}
                    onChange={toggleSelectAll}
                    title="Seleccionar todas"
                  />
                </th>
              )}
              <th className="tt-th tt-th-date">Fecha</th>
              <th className="tt-th tt-th-desc">Descripción</th>
              <th className="tt-th tt-th-amount">Importe</th>
              <th className="tt-th tt-th-cat">Categoría</th>
              <th className="tt-th tt-th-acc">Cuenta Origen</th>
              <th className="tt-th tt-th-acc">Cuenta Destino</th>
              <th className="tt-th tt-th-status">Estado</th>
              <th className="tt-th tt-th-type">Tipo</th>
              <th className="tt-th tt-th-tags">Tags</th>
              <th className="tt-th tt-th-actions"></th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 ? (
              <tr className="tt-empty-row">
                <td colSpan={totalCols}>No hay transacciones</td>
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

              const txId      = tx.id;
              const isSelected = txId != null && selected.has(txId);

              return (
                <tr
                  key={tx.id ?? tx.externalId ?? index}
                  className={`tt-row${isSelected ? ' selected' : ''}`}
                >
                  {/* Checkbox (select mode only) */}
                  {selectMode && (
                    <td className="tt-td tt-td-check">
                      {txId != null && (
                        <input
                          type="checkbox"
                          className="tt-checkbox"
                          checked={isSelected}
                          onChange={() => toggleSelect(txId)}
                        />
                      )}
                    </td>
                  )}

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
                          background:  `${catVisual.color}22`,
                          color:        catVisual.color,
                          borderColor: `${catVisual.color}55`,
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

                  {/* Delete action */}
                  <td className="tt-td tt-td-actions">
                    {txId != null && (
                      <button
                        className="tt-btn-delete"
                        title="Eliminar transacción"
                        onClick={() => setConfirm({ mode: 'single', tx })}
                      >
                        🗑️
                      </button>
                    )}
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
