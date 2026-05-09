import { useState } from 'react';
import type { WealthSnapshotCreateRequest, WealthSnapshotDTO, WealthSnapshotItemDTO } from '../types/wealth';
import { WealthSnapshotModal } from './WealthSnapshotModal';

type Props = {
  snapshots: WealthSnapshotDTO[];
  onDelete: (id: number) => Promise<void>;
  onUpsert: (payload: WealthSnapshotCreateRequest) => Promise<void>;
};

const fmtMoney = (value: number, currency = 'EUR') =>
  value.toLocaleString('es-ES', { style: 'currency', currency, minimumFractionDigits: 0, maximumFractionDigits: 0 });

const fmtDate = (dateStr: string) =>
  new Date(dateStr + 'T00:00:00').toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });

const TYPE_COLS: { key: keyof WealthSnapshotDTO; label: string }[] = [
  { key: 'cashValue',       label: 'Cash' },
  { key: 'fundsValue',      label: 'Fondos' },
  { key: 'etfsValue',       label: 'ETF' },
  { key: 'cryptoValue',     label: 'Crypto' },
  { key: 'stocksValue',     label: 'Acciones' },
  { key: 'bondsValue',      label: 'Bonos' },
  { key: 'realEstateValue', label: 'Inmuebles' },
  { key: 'otherValue',      label: 'Otros' },
];

const TYPE_META: Record<string, { label: string; emoji: string; color: string; bg: string }> = {
  CASH:        { label: 'Cash',      emoji: '💰', color: '#065f46', bg: '#d1fae5' },
  FUND:        { label: 'Fondos',    emoji: '📈', color: '#1e40af', bg: '#dbeafe' },
  ETF:         { label: 'ETF',       emoji: '🌐', color: '#4338ca', bg: '#e0e7ff' },
  CRYPTO:      { label: 'Crypto',    emoji: '🪙', color: '#92400e', bg: '#fef3c7' },
  STOCK:       { label: 'Acciones',  emoji: '📊', color: '#9d174d', bg: '#fce7f3' },
  BOND:        { label: 'Bonos',     emoji: '🏛️', color: '#374151', bg: '#f3f4f6' },
  REAL_ESTATE: { label: 'Inmuebles', emoji: '🏠', color: '#7c3aed', bg: '#ede9fe' },
  OTHER:       { label: 'Otros',     emoji: '📦', color: '#6b7280', bg: '#f9fafb' },
};

/** Group items by type preserving natural order */
function groupByType(items: WealthSnapshotItemDTO[]): [string, WealthSnapshotItemDTO[]][] {
  const map = new Map<string, WealthSnapshotItemDTO[]>();
  for (const item of items) {
    const key = item.type ?? 'OTHER';
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(item);
  }
  return Array.from(map.entries());
}

/** Modal that shows the breakdown of a snapshot's items */
function SnapshotDetailModal({ snapshot, onClose }: { snapshot: WealthSnapshotDTO; onClose: () => void }) {
  const groups = groupByType(snapshot.items ?? []);
  const total = snapshot.totalValue ?? 0;

  return (
    <div className="w-detail-overlay" onClick={onClose}>
      <div className="w-detail-modal" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="w-detail-modal-header">
          <div>
            <h3 className="w-detail-modal-title">📅 {fmtDate(snapshot.snapshotDate)}</h3>
            <span className="w-detail-modal-subtitle">
              Patrimonio total: <strong>{fmtMoney(total, snapshot.currency)}</strong>
            </span>
          </div>
          <button className="w-detail-modal-close" onClick={onClose} title="Cerrar">✕</button>
        </div>

        {/* Body */}
        <div className="w-detail-modal-body">
          {groups.length === 0 ? (
            <p className="w-detail-empty">Sin items registrados.</p>
          ) : (
            groups.map(([type, items]) => {
              const meta = TYPE_META[type] ?? { label: type, emoji: '📦', color: '#6b7280', bg: '#f9fafb' };
              const groupTotal = items.reduce((acc, i) => acc + (i.value ?? 0), 0);
              const pct = total > 0 ? (groupTotal / total) * 100 : 0;
              return (
                <div key={type} className="w-detail-category">
                  {/* Category header */}
                  <div className="w-detail-cat-header" style={{ background: meta.bg }}>
                    <span className="w-detail-cat-emoji">{meta.emoji}</span>
                    <span className="w-detail-cat-label" style={{ color: meta.color }}>{meta.label}</span>
                    <span className="w-detail-cat-pct" style={{ color: meta.color }}>{pct.toFixed(1)}%</span>
                    <span className="w-detail-cat-total" style={{ color: meta.color }}>
                      {fmtMoney(groupTotal, snapshot.currency)}
                    </span>
                  </div>
                  {/* Progress bar */}
                  <div className="w-detail-bar-track">
                    <div
                      className="w-detail-bar-fill"
                      style={{ width: `${pct}%`, background: meta.color }}
                    />
                  </div>
                  {/* Items list */}
                  <ul className="w-detail-items-list">
                    {items.map((item) => (
                      <li key={item.id} className="w-detail-list-item">
                        <span className="w-detail-list-label" title={item.label}>{item.label}</span>
                        {item.subtype && (
                          <span className="w-detail-list-sub">{item.subtype}</span>
                        )}
                        <span className="w-detail-list-value">{fmtMoney(item.value ?? 0, item.currency)}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })
          )}
        </div>

        {/* Footer */}
        {snapshot.notes && (
          <div className="w-detail-modal-footer">
            <span className="w-detail-modal-notes">📝 {snapshot.notes}</span>
          </div>
        )}
      </div>
    </div>
  );
}

export function WealthHistory({ snapshots, onDelete, onUpsert }: Props) {
  const [editingSnapshot, setEditingSnapshot] = useState<WealthSnapshotDTO | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [deletingId, setDeletingId]     = useState<number | null>(null);
  const [deleteError, setDeleteError]   = useState<string | null>(null);
  const [deleting, setDeleting]         = useState(false);
  const [detailSnapshot, setDetailSnapshot] = useState<WealthSnapshotDTO | null>(null);

  const sorted = [...snapshots].sort((a, b) => b.snapshotDate.localeCompare(a.snapshotDate));

  const handleDeleteConfirm = async () => {
    if (deletingId == null) return;
    try {
      setDeleting(true);
      setDeleteError(null);
      await onDelete(deletingId);
      setDeletingId(null);
    } catch {
      setDeleteError('Error eliminando el snapshot. Inténtalo de nuevo.');
    } finally {
      setDeleting(false);
    }
  };

  const openEdit = (s: WealthSnapshotDTO) => {
    setEditingSnapshot(s);
    setShowModal(true);
  };

  const openCreate = () => {
    setEditingSnapshot(null);
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingSnapshot(null);
  };

  return (
    <article className="sheet">
      <div className="sheet-header">
        <div>
          <h3>Historial de patrimonio</h3>
          <span>{snapshots.length} {snapshots.length === 1 ? 'snapshot' : 'snapshots'} registrados</span>
        </div>
        <button className="btn" type="button" onClick={openCreate}>
          + Añadir snapshot manual
        </button>
      </div>

      {sorted.length === 0 ? (
        <div className="w-empty">
          <div className="w-empty-icon">📋</div>
          <h3>Sin historial registrado</h3>
          <p>Usa el botón "Actualizar" en el dashboard o añade un snapshot manual.</p>
        </div>
      ) : (
        <div className="w-history-table-wrapper">
          <table className="w-history-table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th className="w-num">Total</th>
                {TYPE_COLS.map((c) => (
                  <th key={c.key as string} className="w-num w-col-type">{c.label}</th>
                ))}
                <th>Notas</th>
                <th className="w-actions-col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((s) => {
                const hasItems = (s.items?.length ?? 0) > 0;
                return (
                  <tr
                    key={s.id}
                    className={`w-data-row${hasItems ? ' w-clickable' : ''}`}
                    onClick={() => hasItems && setDetailSnapshot(s)}
                    title={hasItems ? 'Haz clic para ver el desglose' : undefined}
                  >
                    <td>
                      <div className="w-date-cell">{fmtDate(s.snapshotDate)}</div>
                      <div className="w-date-sub">{s.currency}</div>
                    </td>
                    <td className="w-num w-total-cell">{fmtMoney(s.totalValue, s.currency)}</td>
                    {TYPE_COLS.map((c) => {
                      const val = (s[c.key] as number) ?? 0;
                      return (
                        <td key={c.key as string} className="w-num w-col-type">
                          {val > 0 ? fmtMoney(val, s.currency) : <span className="w-zero">—</span>}
                        </td>
                      );
                    })}
                    <td className="w-notes-cell" title={s.notes ?? ''}>{s.notes ?? '—'}</td>
                    <td onClick={(e) => e.stopPropagation()}>
                      <div className="w-row-actions">
                        <button
                          className="at-icon-btn"
                          type="button"
                          title="Editar snapshot"
                          onClick={() => openEdit(s)}
                        >
                          ✏️
                        </button>
                        <button
                          className="at-icon-btn at-icon-btn--danger"
                          type="button"
                          title="Eliminar snapshot"
                          onClick={() => setDeletingId(s.id)}
                        >
                          🗑️
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Delete confirmation ── */}
      {deletingId != null && (
        <div className="at-confirm-overlay">
          <div className="at-confirm-dialog">
            <div className="at-confirm-header">
              <span className="at-confirm-icon">⚠️</span>
              <h4 className="at-confirm-title">Eliminar snapshot</h4>
            </div>
            <p className="at-confirm-body">
              ¿Seguro que quieres eliminar este snapshot de patrimonio?
              Esta acción no se puede deshacer.
            </p>
            {deleteError && <p className="at-confirm-error">{deleteError}</p>}
            <div className="at-confirm-actions">
              <button
                className="at-confirm-cancel"
                type="button"
                onClick={() => { setDeletingId(null); setDeleteError(null); }}
                disabled={deleting}
              >
                Cancelar
              </button>
              <button
                className="at-confirm-delete"
                type="button"
                onClick={handleDeleteConfirm}
                disabled={deleting}
              >
                {deleting ? 'Eliminando...' : '🗑️ Eliminar'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Snapshot detail modal ── */}
      {detailSnapshot && (
        <SnapshotDetailModal
          snapshot={detailSnapshot}
          onClose={() => setDetailSnapshot(null)}
        />
      )}

      {/* ── Add / Edit modal ── */}
      {showModal && (
        <WealthSnapshotModal
          existing={editingSnapshot}
          onClose={closeModal}
          onSubmit={async (payload) => {
            await onUpsert(payload);
            closeModal();
          }}
        />
      )}
    </article>
  );
}
