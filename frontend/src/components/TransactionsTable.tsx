
import { useEffect, useState } from 'react';
import type { Transaction } from '../types/banking';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import { CreateTransactionModal } from './CreateTransactionModal';
import { EditTransactionTaxModal } from './EditTransactionTaxModal';
import { BatchTransactionModal } from './BatchTransactionModal';
import { getCategoryVisual, getInstitutionLogo, getMerchantLogo } from '../constants/visualConfig';
import { deleteTransaction, searchTransactions, type TransactionsSearchRequest } from '../services/transactionsService';
import { TransactionEditableRow } from './TransactionEditableRow';
import {
  TransactionsFiltersPanel,
  type TransactionFilters,
  EMPTY_FILTERS,
  countActiveFilters,
} from './TransactionsFiltersPanel';
import './TransactionsTable.css';

type TransactionsTableProps = {
  items: Transaction[];
  accessToken: string;
  onRefresh?: () => void;
  highlightTransactionId?: number;
  onClearHighlight?: () => void;
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

function parseRange(filters: TransactionFilters): { startDate?: string; endDate?: string } {
  if (filters.dateMode === 'range') {
    return {
      startDate: filters.dateFrom || undefined,
      endDate: filters.dateTo || undefined,
    };
  }

  if (filters.dateMode === 'month' && filters.dateMonth) {
    const [yearRaw, monthRaw] = filters.dateMonth.split('-');
    const year = Number(yearRaw);
    const month = Number(monthRaw);
    if (!Number.isFinite(year) || !Number.isFinite(month) || month < 1 || month > 12) {
      return {};
    }

    const startDate = `${yearRaw}-${monthRaw}-01`;
    const lastDay = new Date(year, month, 0).getDate();
    const endDate = `${yearRaw}-${monthRaw}-${String(lastDay).padStart(2, '0')}`;
    return { startDate, endDate };
  }

  if (filters.dateMode === 'year' && filters.dateYear) {
    return {
      startDate: `${filters.dateYear}-01-01`,
      endDate: `${filters.dateYear}-12-31`,
    };
  }

  return {};
}

function parseNumber(value: string): number | undefined {
  if (value.trim() === '') {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function pickSingle(values: number[]): number | undefined {
  return values.length === 1 ? values[0] : undefined;
}

function buildSearchRequest(filters: TransactionFilters, page: number, size: number): TransactionsSearchRequest {
  const { startDate, endDate } = parseRange(filters);
  const description = filters.descriptionText.trim();

  return {
    accountId: pickSingle(filters.sourceAccountIds),
    categoryId: filters.parentCategoryIds.length === 0 ? pickSingle(filters.categoryIds) : undefined,
    tagIds: filters.tagIds.length > 0 ? filters.tagIds : undefined,
    statusId: pickSingle(filters.statusIds),
    typeId: pickSingle(filters.typeIds),
    startDate,
    endDate,
    minAmount: parseNumber(filters.amountMin),
    maxAmount: parseNumber(filters.amountMax),
    description: description.length > 0 ? description : undefined,
    page,
    size,
    sortBy: 'bookingDate',
    sortDirection: 'DESC',
  };
}

export function TransactionsTable({ items, accessToken, onRefresh, highlightTransactionId, onClearHighlight }: TransactionsTableProps) {
  const {
    statusMap,
    typeMap,
    categoryMap,
    categoryCodeMap,
    accountDetailMap,
    tagMap,
    categories,
    statuses,
    types,
    accounts,
    tags,
    merchants,
  } = useTransactionCatalogs(accessToken);

  const [showCreateModal, setShowCreateModal]   = useState(false);
  const [showBatchModal,  setShowBatchModal]    = useState(false);
  const [selectMode,      setSelectMode]        = useState(false);
  const [selected,        setSelected]          = useState<Set<number>>(new Set());
  const [confirm,         setConfirm]           = useState<ConfirmState | null>(null);
  const [deleting,        setDeleting]          = useState(false);
  const [showFilters,     setShowFilters]       = useState(false);
  const [filters,         setFilters]           = useState<TransactionFilters>(EMPTY_FILTERS);
  const [editingId,       setEditingId]         = useState<number | null>(null);
  const [editingTaxId,    setEditingTaxId]      = useState<number | null>(null);
  const [page,            setPage]              = useState(0);
  const [pageSize,        setPageSize]          = useState(25);
  const [serverItems,     setServerItems]       = useState<Transaction[]>([]);
  const [totalElements,   setTotalElements]     = useState(0);
  const [totalPages,      setTotalPages]        = useState(1);
  const [loadingPage,     setLoadingPage]       = useState(false);
  const [pageError,       setPageError]         = useState('');
  const [refreshTick,     setRefreshTick]       = useState(0);

  const activeFilters  = countActiveFilters(filters);
  const highlightedItems = highlightTransactionId != null
    ? items.filter(t => t.id === highlightTransactionId)
    : [];
  const displayedItems = highlightTransactionId != null ? highlightedItems : serverItems;
  const effectiveTotalElements = highlightTransactionId != null ? highlightedItems.length : totalElements;
  const effectiveTotalPages = highlightTransactionId != null ? 1 : Math.max(1, totalPages);
  const currentPage = highlightTransactionId != null ? 0 : Math.min(page, effectiveTotalPages - 1);
  const pageStart = effectiveTotalElements === 0 ? 0 : currentPage * pageSize;
  const pageEnd = highlightTransactionId != null ? effectiveTotalElements : Math.min(pageStart + pageSize, effectiveTotalElements);

  useEffect(() => {
    setPage(0);
  }, [filters, highlightTransactionId, pageSize]);

  useEffect(() => {
    if (highlightTransactionId != null) {
      return;
    }

    if (page > totalPages - 1) {
      setPage(Math.max(0, totalPages - 1));
    }
  }, [highlightTransactionId, page, totalPages]);

  useEffect(() => {
    if (highlightTransactionId != null) {
      setLoadingPage(false);
      setPageError('');
      return;
    }

    if (!accessToken) {
      setServerItems([]);
      setTotalElements(0);
      setTotalPages(1);
      setPageError('');
      setLoadingPage(false);
      return;
    }

    setLoadingPage(true);
    setPageError('');

    searchTransactions(accessToken, buildSearchRequest(filters, page, pageSize))
      .then((result) => {
        setServerItems(result.content || []);
        setTotalElements(result.totalElements ?? 0);
        setTotalPages(Math.max(1, result.totalPages ?? 1));
      })
      .catch(() => {
        setServerItems([]);
        setTotalElements(0);
        setTotalPages(1);
        setPageError('Could not load paginated transactions.');
      })
      .finally(() => {
        setLoadingPage(false);
      });
  }, [accessToken, filters, page, pageSize, highlightTransactionId, refreshTick]);

  const handleSuccess = () => {
    if (onRefresh) {
      onRefresh();
    }
    setRefreshTick(prev => prev + 1);
  };

  // Toggle individual row selection
  const toggleSelect = (id: number) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  // Select-all / deselect-all
  const allIds      = displayedItems.map(t => t.id).filter((id): id is number => id != null);
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
      if (onRefresh) {
        onRefresh();
      }
      setRefreshTick(prev => prev + 1);
    } finally {
      setDeleting(false);
    }
  };

  const isInternalTransfer = (t: Transaction): boolean => {
    const typeName = t.typeId != null ? (typeMap[t.typeId] || '').toUpperCase() : '';
    if (typeName === 'TRANSFER') return true;
    if (t.linkedTransactionId != null) return true;
    if (t.sourceAccountId != null && t.destinationAccountId != null) return true;
    return false;
  };

  const summaryItems = displayedItems.filter(t => !isInternalTransfer(t));
  const totalIncome   = summaryItems.filter(t => (t.amount ?? 0) > 0).reduce((s, t) => s + (t.amount ?? 0), 0);
  const totalExpenses = summaryItems.filter(t => (t.amount ?? 0) < 0).reduce((s, t) => s + (t.amount ?? 0), 0);
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

      {/* ── Batch assistant modal ── */}
      {showBatchModal && (
        <BatchTransactionModal
          accessToken={accessToken}
          onClose={() => setShowBatchModal(false)}
        />
      )}

      {/* Edit tax modal ── */}
      {editingTaxId != null && (
        <EditTransactionTaxModal
          transactionId={editingTaxId}
          transactionDesc={items.find(t => t.id === editingTaxId)?.description}
          token={accessToken}
          onClose={() => setEditingTaxId(null)}
          onSuccess={handleSuccess}
          onUnauthorized={() => {}}
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
            <span className="tt-stat-value">
              {effectiveTotalElements}
              {activeFilters > 0 && highlightTransactionId == null && (
                <span className="tt-stat-total"> filtradas</span>
              )}
            </span>
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
          <div className="tt-page-size-wrap">
            <label htmlFor="tt-page-size" className="tt-page-size-label">Por página</label>
            <select
              id="tt-page-size"
              className="tt-page-size-select"
              value={pageSize}
              onChange={(e) => setPageSize(Number(e.target.value))}
            >
              <option value={10}>10</option>
              <option value={25}>25</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </div>
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
          <button
            className={`btn-filter-toggle ${showFilters ? 'active' : ''}`}
            onClick={() => setShowFilters(v => !v)}
          >
            {showFilters ? '▲' : '▼'} Filtros
            {activeFilters > 0 && <span className="btn-filter-badge">{activeFilters}</span>}
          </button>
          {activeFilters > 0 && (
            <button className="btn-filter-clear" onClick={() => setFilters(EMPTY_FILTERS)}>
              ✕ Limpiar filtros
            </button>
          )}
          <button className="btn primary" onClick={() => setShowCreateModal(true)}>
            + Nueva Transacción
          </button>
          <button className="btn primary" onClick={() => setShowBatchModal(true)} title="Entrada en lote">
            📋 Lote
          </button>
        </div>
      </div>

      {/* ── Filter panel ── */}
      {showFilters && (
        <TransactionsFiltersPanel
          filters={filters}
          onChange={setFilters}
          categories={categories}
          accounts={accounts}
          statuses={statuses}
          types={types}
          tags={tags}
        />
      )}

      {/* ── Highlight banner ── */}
      {highlightTransactionId != null && (
        <div className="tt-highlight-banner">
          <span>🔗 Mostrando transacción vinculada a operación de inversión</span>
          <button className="tt-highlight-clear" type="button" onClick={onClearHighlight}>
            Quitar filtro ×
          </button>
        </div>
      )}

      {pageError && <p className="state error">{pageError}</p>}

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
            {loadingPage ? (
              <tr className="tt-empty-row">
                <td colSpan={totalCols}>Loading transactions...</td>
              </tr>
            ) : displayedItems.length === 0 ? (
              <tr className="tt-empty-row">
                <td colSpan={totalCols}>
                  {activeFilters > 0
                    ? 'No hay transacciones que coincidan con los filtros'
                    : 'No hay transacciones'}
                </td>
              </tr>
            ) : displayedItems.map((tx, index) => {
              const txId       = tx.id;
              const isSelected = txId != null && selected.has(txId);

              // ── Editable row ────────────────────────────────────────────
              if (txId != null && editingId === txId) {
                return (
                  <TransactionEditableRow
                    key={txId}
                    tx={tx}
                    accessToken={accessToken}
                    categories={categories}
                    statuses={statuses}
                    types={types}
                    accounts={accounts}
                    tags={tags}
                    merchants={merchants}
                    categoryCodeMap={categoryCodeMap}
                    categoryMap={categoryMap}
                    statusMap={statusMap}
                    typeMap={typeMap}
                    accountDetailMap={accountDetailMap}
                    tagMap={tagMap}
                    selectMode={selectMode}
                    isSelected={isSelected}
                    onToggleSelect={toggleSelect}
                    onDelete={() => setConfirm({ mode: 'single', tx })}
                    onRefresh={() => { if (onRefresh) onRefresh(); }}
                    onExitEdit={() => setEditingId(null)}
                  />
                );
              }

              // ── Normal row ───────────────────────────────────────────────
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
                    <div className="tt-type-wrap">
                      {typeName ? (
                        <span className={typeClass}>{typeName}</span>
                      ) : <span className="tt-empty-cell">—</span>}
                      {tx.linkedTransactionId != null && (
                        <span className="tt-linked-badge" title="Transferencia enlazada">⛓️</span>
                      )}
                    </div>
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

                  {/* Actions: edit + taxes + delete */}
                  <td className="tt-td tt-td-actions">
                    <div className="tt-action-btns">
                      {txId != null && (
                        <button
                          className="tt-btn-edit"
                          title="Editar transacción"
                          onClick={() => setEditingId(txId)}
                        >
                          ✏️
                        </button>
                      )}
                      {txId != null && (
                        <button
                          className="tt-btn-tax"
                          title="Editar retención"
                          onClick={() => setEditingTaxId(txId)}
                        >
                          💰
                        </button>
                      )}
                      {txId != null && (
                        <button
                          className="tt-btn-delete"
                          title="Eliminar transacción"
                          onClick={() => setConfirm({ mode: 'single', tx })}
                        >
                          🗑️
                        </button>
                      )}
                    </div>
                  </td>

                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {highlightTransactionId == null && (
        <div className="tt-pagination" aria-label="Paginacion de transacciones">
          <div className="tt-pagination-info">
            Mostrando {effectiveTotalElements === 0 ? 0 : pageStart + 1}-{pageEnd} de {effectiveTotalElements}
          </div>
          <div className="tt-pagination-controls">
            <button
              type="button"
              className="tt-page-btn"
              onClick={() => setPage(0)}
              disabled={currentPage === 0 || loadingPage}
              title="Primera pagina"
            >
              «
            </button>
            <button
              type="button"
              className="tt-page-btn"
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={currentPage === 0 || loadingPage}
              title="Pagina anterior"
            >
              ‹
            </button>
            <span className="tt-page-indicator">
              Pagina {effectiveTotalElements === 0 ? 0 : currentPage + 1} de {effectiveTotalPages}
            </span>
            <button
              type="button"
              className="tt-page-btn"
              onClick={() => setPage(p => Math.min(effectiveTotalPages - 1, p + 1))}
              disabled={currentPage >= effectiveTotalPages - 1 || loadingPage}
              title="Pagina siguiente"
            >
              ›
            </button>
            <button
              type="button"
              className="tt-page-btn"
              onClick={() => setPage(effectiveTotalPages - 1)}
              disabled={currentPage >= effectiveTotalPages - 1 || loadingPage}
              title="Ultima pagina"
            >
              »
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
