import { useState } from 'react';
import type { WealthSnapshotCreateRequest, WealthSnapshotDTO } from '../types/wealth';
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

export function WealthHistory({ snapshots, onDelete, onUpsert }: Props) {
  const [editingSnapshot, setEditingSnapshot] = useState<WealthSnapshotDTO | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [deletingId, setDeletingId]   = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleting, setDeleting]       = useState(false);

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
              {sorted.map((s) => (
                <tr key={s.id}>
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
                  <td>
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
              ))}
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
