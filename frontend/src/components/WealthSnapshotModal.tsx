import React, { useState } from 'react';
import type {
  WealthItemType,
  WealthSnapshotCreateRequest,
  WealthSnapshotDTO,
  WealthSnapshotItemInput,
} from '../types/wealth';

const ITEM_TYPES: WealthItemType[] = ['CASH', 'FUND', 'ETF', 'CRYPTO', 'STOCK', 'BOND', 'REAL_ESTATE', 'OTHER'];

const TYPE_LABELS: Record<WealthItemType, string> = {
  CASH:        'Cash / Efectivo',
  FUND:        'Fondo de inversión',
  ETF:         'ETF',
  CRYPTO:      'Criptomoneda',
  STOCK:       'Acción',
  BOND:        'Bono / Renta fija',
  REAL_ESTATE: 'Inmueble',
  OTHER:       'Otro activo',
};

type ItemDraft = {
  type: WealthItemType;
  subtype: string;
  label: string;
  quantity: string;
  unitPrice: string;
  value: string;
  currency: string;
};

const newItem = (): ItemDraft => ({
  type:      'CASH',
  subtype:   '',
  label:     '',
  quantity:  '',
  unitPrice: '',
  value:     '',
  currency:  'EUR',
});

type Props = {
  existing: WealthSnapshotDTO | null;
  onClose:  () => void;
  onSubmit: (payload: WealthSnapshotCreateRequest) => Promise<void>;
};

export function WealthSnapshotModal({ existing, onClose, onSubmit }: Props) {
  const today = new Date().toISOString().split('T')[0];

  const [date,     setDate]     = useState(existing?.snapshotDate ?? today);
  const [currency, setCurrency] = useState(existing?.currency ?? 'EUR');
  const [notes,    setNotes]    = useState(existing?.notes ?? '');
  const [items, setItems] = useState<ItemDraft[]>(
    existing?.items?.map((i) => ({
      type:      i.type,
      subtype:   i.subtype   ?? '',
      label:     i.label,
      quantity:  i.quantity  != null ? String(i.quantity)  : '',
      unitPrice: i.unitPrice != null ? String(i.unitPrice) : '',
      value:     String(i.value),
      currency:  i.currency  ?? 'EUR',
    })) ?? [newItem()]
  );

  const [submitting, setSubmitting] = useState(false);
  const [error,      setError]      = useState<string | null>(null);

  const updateItem = (idx: number, field: keyof ItemDraft, val: string) => {
    setItems((prev) => {
      const next = [...prev];
      next[idx] = { ...next[idx], [field]: val };
      if (field === 'quantity' || field === 'unitPrice') {
        const qty   = parseFloat(field === 'quantity'  ? val : next[idx].quantity);
        const price = parseFloat(field === 'unitPrice' ? val : next[idx].unitPrice);
        if (!isNaN(qty) && !isNaN(price) && qty > 0 && price > 0) {
          next[idx].value = (qty * price).toFixed(2);
        }
      }
      return next;
    });
  };

  const addItem    = () => setItems((prev) => [...prev, newItem()]);
  const removeItem = (idx: number) => {
    if (items.length <= 1) return;
    setItems((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const parsedItems: WealthSnapshotItemInput[] = items.map((i) => ({
      type:      i.type,
      subtype:   i.subtype   || undefined,
      label:     i.label.trim(),
      quantity:  i.quantity  ? parseFloat(i.quantity)  : undefined,
      unitPrice: i.unitPrice ? parseFloat(i.unitPrice) : undefined,
      value:     parseFloat(i.value),
      currency:  i.currency  || undefined,
      source:    'manual',
    }));

    if (parsedItems.some((i) => !i.label || isNaN(i.value) || i.value < 0)) {
      setError('Cada elemento necesita un nombre y un valor positivo válido.');
      return;
    }

    try {
      setSubmitting(true);
      await onSubmit({
        snapshotDate: date,
        currency,
        notes: notes.trim() || undefined,
        items: parsedItems,
      });
    } catch {
      setError('Error al guardar el snapshot. Inténtalo de nuevo.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="w-modal" onClick={(e) => e.stopPropagation()}>

        {/* Header */}
        <div className="w-modal-header">
          <h3>{existing ? '✏️ Editar snapshot' : '➕ Nuevo snapshot manual'}</h3>
          <button type="button" className="w-modal-close" onClick={onClose} aria-label="Cerrar">✕</button>
        </div>

        <form className="w-modal-body" onSubmit={handleSubmit}>

          {/* Snapshot info */}
          <div className="w-modal-section">
            <h4 className="w-modal-section-title">Información general</h4>
            {existing && (
              <p className="w-modal-info-note">
                ℹ️ Los elementos marcados como "banking" o "investments" se sobreescribirán en la próxima actualización automática.
              </p>
            )}
            <div className="w-modal-row3">
              <div className="w-field">
                <label htmlFor="w-date">Fecha</label>
                <input
                  id="w-date"
                  type="date"
                  value={date}
                  onChange={(e) => setDate(e.target.value)}
                  required
                />
              </div>
              <div className="w-field">
                <label htmlFor="w-currency">Moneda</label>
                <select id="w-currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
                  <option value="EUR">EUR €</option>
                  <option value="USD">USD $</option>
                  <option value="GBP">GBP £</option>
                  <option value="CHF">CHF</option>
                </select>
              </div>
              <div className="w-field">
                <label htmlFor="w-notes">Notas</label>
                <input
                  id="w-notes"
                  type="text"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Comentario opcional..."
                />
              </div>
            </div>
          </div>

          {/* Items list */}
          <div className="w-modal-section">
            <div className="w-modal-section-header">
              <h4 className="w-modal-section-title">Elementos ({items.length})</h4>
              <button type="button" className="w-add-item-btn" onClick={addItem}>
                + Añadir elemento
              </button>
            </div>

            <div className="w-items-list">
              {/* Column headers */}
              <div className="w-items-header">
                <span style={{ minWidth: 130 }}>Tipo</span>
                <span style={{ flex: 2, minWidth: 140 }}>Nombre *</span>
                <span style={{ flex: 1, minWidth: 90 }}>Subtipo</span>
                <span style={{ width: 100 }}>Cantidad</span>
                <span style={{ width: 110 }}>P. Unitario</span>
                <span style={{ width: 110 }}>Valor * €</span>
                <span style={{ width: 65 }}>Moneda</span>
                <span style={{ width: 32 }} />
              </div>

              {items.map((item, idx) => (
                <div key={idx} className="w-item-row">
                  <div className="w-item-row-fields">
                    <select
                      value={item.type}
                      onChange={(e) => updateItem(idx, 'type', e.target.value)}
                      className="w-item-type-select"
                      title="Tipo de activo"
                    >
                      {ITEM_TYPES.map((t) => (
                        <option key={t} value={t}>{TYPE_LABELS[t]}</option>
                      ))}
                    </select>
                    <input
                      type="text"
                      placeholder="Nombre del activo *"
                      value={item.label}
                      onChange={(e) => updateItem(idx, 'label', e.target.value)}
                      required
                      className="w-item-label-input"
                    />
                    <input
                      type="text"
                      placeholder="Subtipo"
                      value={item.subtype}
                      onChange={(e) => updateItem(idx, 'subtype', e.target.value)}
                      className="w-item-small"
                    />
                    <input
                      type="number"
                      placeholder="Cantidad"
                      value={item.quantity}
                      onChange={(e) => updateItem(idx, 'quantity', e.target.value)}
                      min="0"
                      step="any"
                      className="w-item-num"
                      title="Número de unidades (opcional)"
                    />
                    <input
                      type="number"
                      placeholder="Precio unit."
                      value={item.unitPrice}
                      onChange={(e) => updateItem(idx, 'unitPrice', e.target.value)}
                      min="0"
                      step="any"
                      className="w-item-num"
                      title="Precio unitario — calcula valor automáticamente"
                    />
                    <input
                      type="number"
                      placeholder="Valor *"
                      value={item.value}
                      onChange={(e) => updateItem(idx, 'value', e.target.value)}
                      min="0"
                      step="any"
                      required
                      className="w-item-num w-item-value"
                    />
                    <input
                      type="text"
                      placeholder="EUR"
                      value={item.currency}
                      onChange={(e) => updateItem(idx, 'currency', e.target.value)}
                      className="w-item-currency"
                      maxLength={3}
                    />
                  </div>
                  <button
                    type="button"
                    className="at-icon-btn at-icon-btn--danger w-item-remove"
                    onClick={() => removeItem(idx)}
                    disabled={items.length <= 1}
                    title="Eliminar elemento"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>

          {error && <p className="state error" style={{ margin: 0 }}>{error}</p>}

          <div className="w-modal-footer">
            <button type="button" className="btn secondary" onClick={onClose} disabled={submitting}>
              Cancelar
            </button>
            <button type="submit" className="btn" disabled={submitting}>
              {submitting
                ? 'Guardando...'
                : existing ? '💾 Actualizar snapshot' : '✅ Crear snapshot'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
