import { useEffect, useState } from 'react';
import type { TaxType, TransactionTax, TransactionTaxRequest } from '../types/banking';
import { CatalogService } from '../services/catalogService';
import './EditTransactionTaxModal.css';

type Props = {
  transactionId: number;
  transactionDesc?: string;
  token: string;
  onClose: () => void;
  onSuccess: () => void;
  onUnauthorized: () => void;
};

export function EditTransactionTaxModal({
  transactionId,
  transactionDesc,
  token,
  onClose,
  onSuccess,
  onUnauthorized,
}: Props) {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Tax record data
  const [currentTax, setCurrentTax] = useState<TransactionTax | null>(null);
  const [taxTypes, setTaxTypes] = useState<TaxType[]>([]);

  // Form state
  const [hasTax, setHasTax] = useState(false);
  const [taxGross, setTaxGross] = useState('');
  const [taxAmount, setTaxAmount] = useState('');
  const [taxTypeId, setTaxTypeId] = useState<number | ''>('');
  const [taxNotes, setTaxNotes] = useState('');

  useEffect(() => {
    loadData();
  }, [transactionId, token]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      // Load tax types catalog
      const types = await CatalogService.fetchTaxTypes(token);
      setTaxTypes(types);

      // Load current tax record for this transaction
      const existing = await CatalogService.getTransactionTax(token, transactionId);
      setCurrentTax(existing);

      if (existing) {
        setHasTax(true);
        setTaxGross(existing.grossAmount.toString());
        setTaxAmount(existing.taxAmount.toString());
        setTaxTypeId(existing.taxType.id);
        setTaxNotes(existing.notes || '');
      }
    } catch (err: any) {
      if (err?.response?.status === 401) {
        onUnauthorized();
      } else {
        setError('Error al cargar datos de retención');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (!hasTax) {
      // Just close if toggled off
      onClose();
      return;
    }

    if (!taxGross || !taxAmount || !taxTypeId) {
      setError('Todos los campos son requeridos');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      const request: TransactionTaxRequest = {
        grossAmount: parseFloat(taxGross),
        taxAmount: parseFloat(taxAmount),
        taxTypeId: Number(taxTypeId),
        notes: taxNotes || undefined,
      };

      await CatalogService.saveTransactionTax(token, transactionId, request);
      onSuccess();
      onClose();
    } catch (err: any) {
      if (err?.response?.status === 401) {
        onUnauthorized();
      } else {
        setError(err?.response?.data?.message || 'Error al guardar retención');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!currentTax) return;
    setSaving(true);
    setError(null);
    try {
      await CatalogService.deleteTransactionTax(token, transactionId);
      onSuccess();
      onClose();
    } catch (err: any) {
      if (err?.response?.status === 401) {
        onUnauthorized();
      } else {
        setError('Error al eliminar retención');
      }
    } finally {
      setSaving(false);
    }
  };

  const netAmount = taxGross && taxAmount
    ? (parseFloat(taxGross) - parseFloat(taxAmount)).toLocaleString('es-ES', {
        style: 'currency',
        currency: 'EUR',
      })
    : '—';

  if (loading) {
    return (
      <div className="etm-overlay" onClick={onClose}>
        <div className="etm-modal" onClick={e => e.stopPropagation()}>
          <div className="etm-content">
            <p style={{ textAlign: 'center', color: '#666' }}>Cargando...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="etm-overlay" onClick={onClose}>
      <div className="etm-modal" onClick={e => e.stopPropagation()}>
        <div className="etm-header">
          <h3>Editar Retención Fiscal</h3>
          <button className="etm-close" onClick={onClose}>✕</button>
        </div>

        <div className="etm-body">
          {transactionDesc && (
            <p className="etm-tx-desc">
              <strong>Transacción:</strong> {transactionDesc}
            </p>
          )}

          {error && <div className="etm-error">{error}</div>}

          <label className="etm-toggle-label">
            <input
              type="checkbox"
              className="etm-toggle-checkbox"
              checked={hasTax}
              onChange={e => setHasTax(e.target.checked)}
              disabled={saving}
            />
            <span>Tiene retención fiscal</span>
          </label>

          {hasTax && (
            <div className="etm-fields">
              <div className="etm-field">
                <label htmlFor="taxGross">Importe Bruto (€)</label>
                <input
                  id="taxGross"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={taxGross}
                  onChange={e => setTaxGross(e.target.value)}
                  disabled={saving}
                />
              </div>

              <div className="etm-field">
                <label htmlFor="taxAmount">Retención (€)</label>
                <input
                  id="taxAmount"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="0.00"
                  value={taxAmount}
                  onChange={e => setTaxAmount(e.target.value)}
                  disabled={saving}
                />
              </div>

              <div className="etm-field">
                <label>Neto: <strong>{netAmount}</strong></label>
              </div>

              <div className="etm-field">
                <label htmlFor="taxType">Tipo de Retención</label>
                <select
                  id="taxType"
                  value={taxTypeId}
                  onChange={e => setTaxTypeId(e.target.value ? Number(e.target.value) : '')}
                  disabled={saving}
                >
                  <option value="">— Selecciona tipo —</option>
                  {taxTypes.map(t => (
                    <option key={t.id} value={t.id}>
                      {t.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="etm-field">
                <label htmlFor="taxNotes">Notas (opcional)</label>
                <textarea
                  id="taxNotes"
                  placeholder="Observaciones sobre la retención..."
                  value={taxNotes}
                  onChange={e => setTaxNotes(e.target.value)}
                  disabled={saving}
                  rows={3}
                />
              </div>
            </div>
          )}
        </div>

        <div className="etm-footer">
          {currentTax && hasTax && (
            <button
              className="etm-btn etm-btn-delete"
              onClick={handleDelete}
              disabled={saving}
            >
              🗑️ Eliminar Retención
            </button>
          )}
          <div style={{ flex: 1 }} />
          <button className="etm-btn etm-btn-cancel" onClick={onClose} disabled={saving}>
            Cancelar
          </button>
          <button
            className="etm-btn etm-btn-save"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? 'Guardando…' : '💾 Guardar'}
          </button>
        </div>
      </div>
    </div>
  );
}
