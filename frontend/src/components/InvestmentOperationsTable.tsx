import React, { useEffect, useMemo, useState } from 'react';
import { INVESTMENT_CURRENCY_OPTIONS, getInvestmentTypeVisual } from '../constants/visualConfig';
import { useInvestmentOperations } from '../hooks/useInvestmentOperations';
import { fetchInstruments, fetchPlatforms } from '../services/investmentCatalogService';
import type {
  InvestmentInstrument,
  InvestmentOperation,
  InvestmentOperationDraft,
  InvestmentOperationType,
  InvestmentPlatform,
  InvestmentPosition,
} from '../types/investments';
import './investment-operations.css';

type Props = {
  token: string;
  onUnauthorized?: (message: string) => void;
};

const EMPTY_FORM = {
  instrumentId: '',
  platformId: '',
  positionName: '',
  type: 'BUY' as InvestmentOperationType,
  operationDate: new Date().toISOString().slice(0, 10),
  quantity: '',
  unitPrice: '',
  fees: '',
  currency: 'EUR',
  notes: '',
};

const safeNumber = (value: number | null | undefined) => (typeof value === 'number' && Number.isFinite(value) ? value : 0);

const fmtMoney = (value: number | null | undefined, currency = 'EUR') =>
  safeNumber(value).toLocaleString('es-ES', { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2 });

const fmtNumber = (value: number | null | undefined) =>
  safeNumber(value).toLocaleString('es-ES', { minimumFractionDigits: 0, maximumFractionDigits: 8 });

const fmtDate = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString('es-ES', { day: '2-digit', month: 'short', year: 'numeric' });
};

const getInvestmentSubtitle = (investment?: InvestmentPosition) => {
  if (!investment) return 'Posición no disponible';
  const symbol = investment.instrumentSymbol || investment.instrumentName || `#${investment.instrumentId}`;
  const platform = investment.platformName || investment.platformCode;
  return platform ? `${symbol} · ${platform}` : symbol;
};

export const InvestmentOperationsTable: React.FC<Props> = ({ token, onUnauthorized }) => {
  const {
    operations,
    investments,
    loading,
    error,
    clearError,
    addOperation,
    editOperation,
    removeOperation,
  } = useInvestmentOperations(token, onUnauthorized);

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<{ id: number; label: string } | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [instruments, setInstruments] = useState<InvestmentInstrument[]>([]);
  const [platforms, setPlatforms] = useState<InvestmentPlatform[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;

    setCatalogLoading(true);
    setCatalogError(null);

    Promise.all([fetchInstruments(token), fetchPlatforms(token)])
      .then(([loadedInstruments, loadedPlatforms]) => {
        setInstruments([...loadedInstruments].sort((a, b) => a.name.localeCompare(b.name)));
        setPlatforms([...loadedPlatforms].sort((a, b) => a.name.localeCompare(b.name)));
      })
      .catch((err: unknown) => {
        const resolved = err as { response?: { data?: { message?: string } }; message?: string };
        setCatalogError(resolved?.response?.data?.message || resolved?.message || 'Error loading catalog data');
      })
      .finally(() => setCatalogLoading(false));
  }, [token]);

  const investmentMap = useMemo(
    () => new Map(investments.map((investment) => [investment.id, investment])),
    [investments]
  );

  const totals = useMemo(() => {
    const buyTotal = operations
      .filter((operation) => operation.type === 'BUY')
      .reduce((sum, operation) => sum + operation.totalAmount, 0);
    const sellTotal = operations
      .filter((operation) => operation.type === 'SELL')
      .reduce((sum, operation) => sum + operation.totalAmount, 0);
    return { buyTotal, sellTotal };
  }, [operations]);

  const resetForm = () => {
    setForm({ ...EMPTY_FORM });
    setEditingId(null);
    setFormError(null);
  };

  const closeForm = () => {
    setShowForm(false);
    resetForm();
  };

  const openCreate = () => {
    resetForm();
    setShowForm(true);
  };

  const openEdit = (operation: InvestmentOperation) => {
    const relatedInvestment = investmentMap.get(operation.investmentId);
    setEditingId(operation.id);
    setFormError(null);
    setForm({
      instrumentId: relatedInvestment?.instrumentId ? String(relatedInvestment.instrumentId) : '',
      platformId: relatedInvestment?.platformId ? String(relatedInvestment.platformId) : '',
      positionName: relatedInvestment?.name ?? '',
      type: operation.type,
      operationDate: operation.operationDate,
      quantity: String(operation.quantity),
      unitPrice: String(operation.unitPrice),
      fees: String(operation.fees ?? 0),
      currency: operation.currency,
      notes: operation.notes ?? '',
    });
    setShowForm(true);
  };

  const onChange = (key: keyof typeof EMPTY_FORM, value: string) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setFormError(null);

    try {
      const parsedInstrumentId = Number(form.instrumentId);
      const payload: InvestmentOperationDraft = {
        instrumentId: parsedInstrumentId,
        platformId: form.platformId ? Number(form.platformId) : undefined,
        positionName: form.positionName.trim() || undefined,
        type: form.type,
        operationDate: form.operationDate,
        quantity: Number(form.quantity),
        unitPrice: Number(form.unitPrice),
        fees: form.fees !== '' ? Number(form.fees) : 0,
        currency: form.currency,
        notes: form.notes.trim() || undefined,
      };

      if (editingId) {
        await editOperation(editingId, payload);
      } else {
        await addOperation(payload);
      }

      closeForm();
    } catch (err: unknown) {
      const resolved = err as { response?: { data?: { message?: string } }; message?: string };
      setFormError(resolved?.response?.data?.message || resolved?.message || 'Error saving operation');
    } finally {
      setSubmitting(false);
    }
  };

  const selectedInstrumentId = form.instrumentId ? Number(form.instrumentId) : null;
  const selectedPlatformId = form.platformId ? Number(form.platformId) : null;
  const selectedInstrument = selectedInstrumentId
    ? instruments.find((instrument) => instrument.id === selectedInstrumentId)
    : undefined;
  const selectedPlatform = selectedPlatformId
    ? platforms.find((platform) => platform.id === selectedPlatformId)
    : undefined;

  const selectedInvestment = selectedInstrumentId
    ? investments.find((investment) => {
      const investmentPlatformId = investment.platformId ?? null;
      return investment.instrumentId === selectedInstrumentId && investmentPlatformId === selectedPlatformId;
    })
    : undefined;

  const confirmDeleteOperation = async () => {
    if (!confirmDelete) return;
    setDeleting(true);
    try {
      await removeOperation(confirmDelete.id);
      setConfirmDelete(null);
    } catch (err: unknown) {
      const resolved = err as { response?: { data?: { message?: string } }; message?: string };
      setFormError(resolved?.response?.data?.message || resolved?.message || 'Error deleting operation');
      setConfirmDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  if (loading) return <div className="iot-empty">Cargando operaciones…</div>;

  return (
    <div className="iot-wrapper">
      <div className="iot-summary-bar">
        <div className="iot-summary-stats">
          <div className="iot-stat">
            <span className="iot-stat-label">Operaciones</span>
            <span className="iot-stat-value">{operations.length}</span>
          </div>
          <div className="iot-stat iot-stat--buy">
            <span className="iot-stat-label">Compras</span>
            <span className="iot-stat-value">{fmtMoney(totals.buyTotal)}</span>
          </div>
          <div className="iot-stat iot-stat--sell">
            <span className="iot-stat-label">Ventas</span>
            <span className="iot-stat-value">{fmtMoney(totals.sellTotal)}</span>
          </div>
          <div className="iot-stat">
            <span className="iot-stat-label">Posiciones</span>
            <span className="iot-stat-value">{investments.length}</span>
          </div>
        </div>

        <button className={`iot-btn-add${showForm ? ' iot-btn-add--cancel' : ''}`} type="button" onClick={() => showForm ? closeForm() : openCreate()} disabled={catalogLoading && !showForm}>
          {showForm ? '✕ Cancelar' : '+ Nueva operación'}
        </button>
      </div>

      {error && (
        <div className="iot-toast-error" role="alert">
          <span>⚠ {error}</span>
          <button type="button" onClick={() => clearError()}>Cerrar</button>
        </div>
      )}

      {investments.length === 0 && !showForm && (
        <div className="iot-empty">
          Aún no hay posiciones. Puedes crear la primera directamente al registrar una operación.
        </div>
      )}

      {showForm && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal iot-modal">
            <div className="modal-header">
              <h4>{editingId ? 'Editar operación' : 'Añadir operación'}</h4>
              <button className="modal-close" type="button" onClick={closeForm}>✕</button>
            </div>

            <form className="modal-body iot-form" onSubmit={submit}>
              <div className="modal-row">
                <label>Instrumento</label>
                <select required value={form.instrumentId} onChange={(event) => onChange('instrumentId', event.target.value)}>
                  <option value="">Selecciona un instrumento</option>
                  {instruments.map((instrument) => (
                    <option key={instrument.id} value={instrument.id}>
                      {instrument.name} · {instrument.symbol}
                    </option>
                  ))}
                </select>
              </div>

              <div className="modal-row">
                <label>Plataforma</label>
                <select value={form.platformId} onChange={(event) => onChange('platformId', event.target.value)}>
                  <option value="">Sin plataforma</option>
                  {platforms.map((platform) => (
                    <option key={platform.id} value={platform.id}>{platform.name}</option>
                  ))}
                </select>
              </div>

              <div className="modal-row">
                <label>Nombre de la posición (opcional)</label>
                <input
                  type="text"
                  maxLength={150}
                  placeholder="Ej: Cartera principal SP500"
                  value={form.positionName}
                  onChange={(event) => onChange('positionName', event.target.value)}
                />
              </div>

              <div className="iot-type-row">
                <label>Tipo</label>
                <div className="iot-type-toggle">
                  <button type="button" className={`iot-type-btn${form.type === 'BUY' ? ' active buy' : ''}`} onClick={() => onChange('type', 'BUY')}>
                    Compra
                  </button>
                  <button type="button" className={`iot-type-btn${form.type === 'SELL' ? ' active sell' : ''}`} onClick={() => onChange('type', 'SELL')}>
                    Venta
                  </button>
                </div>
              </div>

              <div className="iot-form-grid">
                <div className="modal-row">
                  <label>Fecha</label>
                  <input required type="date" value={form.operationDate} onChange={(event) => onChange('operationDate', event.target.value)} />
                </div>
                <div className="modal-row">
                  <label>Cantidad</label>
                  <input required type="number" min="0.00000001" step="0.00000001" value={form.quantity} onChange={(event) => onChange('quantity', event.target.value)} />
                </div>
                <div className="modal-row">
                  <label>Precio unitario</label>
                  <input required type="number" min="0.00000001" step="0.00000001" value={form.unitPrice} onChange={(event) => onChange('unitPrice', event.target.value)} />
                </div>
                <div className="modal-row">
                  <label>Fees</label>
                  <input type="number" min="0" step="0.01" value={form.fees} onChange={(event) => onChange('fees', event.target.value)} />
                </div>
                <div className="modal-row">
                  <label>Divisa</label>
                  <select value={form.currency} onChange={(event) => onChange('currency', event.target.value)}>
                    {INVESTMENT_CURRENCY_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </div>
              </div>

              {selectedInstrument && (
                <div className="iot-selected-investment">
                  <span className="iot-selected-investment-title">
                    {selectedInvestment ? 'Se usará la posición existente' : 'Se creará una nueva posición automáticamente'}
                  </span>
                  <strong>{selectedInvestment?.name || form.positionName.trim() || selectedInstrument.symbol || selectedInstrument.name}</strong>
                  <span>
                    {selectedInstrument.symbol || selectedInstrument.name}
                    {selectedPlatform ? ` · ${selectedPlatform.name}` : ''}
                  </span>
                </div>
              )}

              {catalogError && <p className="modal-error">{catalogError}</p>}

              <div className="modal-row">
                <label>Notas</label>
                <textarea rows={4} placeholder="Comentario opcional de la operación" value={form.notes} onChange={(event) => onChange('notes', event.target.value)} />
              </div>

              {formError && <p className="modal-error">{formError}</p>}

              <div className="modal-actions">
                <button className="btn" type="button" onClick={closeForm} disabled={submitting}>Cancelar</button>
                <button className="btn primary" type="submit" disabled={submitting}>{submitting ? 'Guardando…' : editingId ? 'Guardar cambios' : 'Crear operación'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {confirmDelete && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal iot-confirm-modal">
            <div className="modal-header">
              <h4>Eliminar operación</h4>
            </div>
            <div className="modal-body iot-confirm-body">
              <p>
                Vas a eliminar la operación <strong>{confirmDelete.label}</strong>. Esta acción recalculará FIFO y no se puede deshacer.
              </p>
              <div className="modal-actions">
                <button className="btn" type="button" onClick={() => setConfirmDelete(null)} disabled={deleting}>Cancelar</button>
                <button className="btn danger" type="button" onClick={confirmDeleteOperation} disabled={deleting}>{deleting ? 'Eliminando…' : 'Eliminar'}</button>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="iot-container">
        <table className="iot-table">
          <thead>
            <tr>
              <th className="iot-th">Fecha</th>
              <th className="iot-th">Tipo</th>
              <th className="iot-th">Posición</th>
              <th className="iot-th iot-th--right">Cantidad</th>
              <th className="iot-th iot-th--right">Precio</th>
              <th className="iot-th iot-th--right">Fees</th>
              <th className="iot-th iot-th--right">Total</th>
              <th className="iot-th">Notas</th>
              <th className="iot-th iot-th--actions">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {operations.length === 0 && (
              <tr>
                <td className="iot-empty-row" colSpan={9}>Todavía no hay operaciones registradas.</td>
              </tr>
            )}

            {operations.map((operation) => {
              const investment = investmentMap.get(operation.investmentId);
              const typeVisual = investment ? getInvestmentTypeVisual(investment.typeCode, investment.typeName) : null;

              return (
                <tr key={operation.id} className="iot-row">
                  <td className="iot-td">{fmtDate(operation.operationDate)}</td>
                  <td className="iot-td">
                    <span className={`iot-type-pill ${operation.type === 'BUY' ? 'buy' : 'sell'}`}>
                      {operation.type === 'BUY' ? 'Compra' : 'Venta'}
                    </span>
                  </td>
                  <td className="iot-td">
                    <div className="iot-investment-cell">
                      <span className="iot-investment-name">{investment?.name || `Posición #${operation.investmentId}`}</span>
                      <span className="iot-investment-meta">
                        {typeVisual ? `${typeVisual.emoji} ` : ''}{getInvestmentSubtitle(investment)}
                      </span>
                    </div>
                  </td>
                  <td className="iot-td iot-td--right">{fmtNumber(operation.quantity)}</td>
                  <td className="iot-td iot-td--right">{fmtMoney(operation.unitPrice, operation.currency)}</td>
                  <td className="iot-td iot-td--right">{fmtMoney(operation.fees ?? 0, operation.currency)}</td>
                  <td className="iot-td iot-td--right">
                    <div className="iot-total-cell">
                      <strong>{fmtMoney(operation.totalAmount, operation.currency)}</strong>
                      <span>{fmtMoney(operation.totalAmountEur, 'EUR')}</span>
                    </div>
                  </td>
                  <td className="iot-td iot-notes">{operation.notes?.trim() || '—'}</td>
                  <td className="iot-td">
                    <div className="iot-actions">
                      <button className="iot-btn-icon" type="button" onClick={() => openEdit(operation)} title="Editar operación">✏️</button>
                      <button
                        className="iot-btn-icon danger"
                        type="button"
                        onClick={() => setConfirmDelete({ id: operation.id, label: `${operation.type} · ${fmtDate(operation.operationDate)}` })}
                        title="Eliminar operación"
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
    </div>
  );
};