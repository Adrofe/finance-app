import React, { useEffect, useMemo, useRef, useState } from 'react';
import axios from 'axios';
import { INVESTMENT_CURRENCY_OPTIONS, getInvestmentTypeVisual } from '../constants/visualConfig';
import { CreateTransactionModal } from './CreateTransactionModal';
import { useInvestmentOperations } from '../hooks/useInvestmentOperations';
import { CatalogService, type TransactionCategory } from '../services/catalogService';
import { fetchInstruments, fetchInvestmentTypes, fetchPlatforms } from '../services/investmentCatalogService';
import { deleteTransaction } from '../services/transactionsService';
import { fetchExchangeRates } from '../services/exchangeRatesService';
import { dispatchFinanceEvent, FINANCE_EVENTS } from '../events/financeEvents';
import type { CreateTransactionRequest, Transaction } from '../types/banking';
import type {
  InvestmentInstrument,
  InvestmentOperation,
  InvestmentOperationDraft,
  InvestmentOperationType,
  InvestmentPlatform,
  InvestmentPosition,
  InvestmentType,
} from '../types/investments';
import './investment-operations.css';

type Props = {
  token: string;
  onUnauthorized?: (message: string) => void;
};

type DatePreset = 'ALL' | 'THIS_MONTH' | 'LAST_3_MONTHS' | 'LAST_YEAR' | 'PREVIOUS_YEAR' | 'CUSTOM';

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

const toIsoDate = (date: Date) => date.toISOString().slice(0, 10);

const getPresetRange = (preset: DatePreset): { from?: string; to?: string } => {
  const now = new Date();

  if (preset === 'ALL') {
    return {};
  }

  if (preset === 'THIS_MONTH') {
    const from = new Date(now.getFullYear(), now.getMonth(), 1);
    const to = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    return { from: toIsoDate(from), to: toIsoDate(to) };
  }

  if (preset === 'LAST_3_MONTHS') {
    const to = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    const from = new Date(now.getFullYear(), now.getMonth() - 2, 1);
    return { from: toIsoDate(from), to: toIsoDate(to) };
  }

  if (preset === 'LAST_YEAR') {
    const to = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const from = new Date(now.getFullYear() - 1, now.getMonth(), now.getDate());
    return { from: toIsoDate(from), to: toIsoDate(to) };
  }

  if (preset === 'PREVIOUS_YEAR') {
    const year = now.getFullYear() - 1;
    return { from: `${year}-01-01`, to: `${year}-12-31` };
  }

  return {};
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
  const [confirmDelete, setConfirmDelete] = useState<{ id: number; label: string; linkedTransactionId?: number } | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [form, setForm] = useState({ ...EMPTY_FORM });
  const [instruments, setInstruments] = useState<InvestmentInstrument[]>([]);
  const [investmentTypes, setInvestmentTypes] = useState<InvestmentType[]>([]);
  const [platforms, setPlatforms] = useState<InvestmentPlatform[]>([]);
  const [transactionCategories, setTransactionCategories] = useState<TransactionCategory[]>([]);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [showIntegratedTransactionModal, setShowIntegratedTransactionModal] = useState(false);
  const [integratedEurRate, setIntegratedEurRate] = useState<number | null>(null);
  const [datePreset, setDatePreset] = useState<DatePreset>('ALL');
  const [customFromDate, setCustomFromDate] = useState('');
  const [customToDate, setCustomToDate] = useState('');
  const [operationFilter, setOperationFilter] = useState<'ALL' | InvestmentOperationType>('ALL');
  const [investmentTypeFilter, setInvestmentTypeFilter] = useState<'ALL' | string>('ALL');
  const [instrumentSearch, setInstrumentSearch] = useState('');
  const [instrumentDropdownOpen, setInstrumentDropdownOpen] = useState(false);
  const instrumentComboRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!token) return;

    setCatalogLoading(true);
    setCatalogError(null);

    Promise.all([
      fetchInstruments(token),
      fetchInvestmentTypes(token),
      fetchPlatforms(token),
      CatalogService.fetchCategories(token),
    ])
      .then(([loadedInstruments, loadedTypes, loadedPlatforms, loadedCategories]) => {
        setInstruments([...loadedInstruments].sort((a, b) => a.name.localeCompare(b.name)));
        setInvestmentTypes([...loadedTypes].sort((a, b) => a.name.localeCompare(b.name)));
        setPlatforms([...loadedPlatforms].sort((a, b) => a.name.localeCompare(b.name)));
        setTransactionCategories(loadedCategories);
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

  const filteredInstruments = useMemo(() => {
    const q = instrumentSearch.toLowerCase().trim();
    if (!q) return instruments;
    return instruments.filter(
      (i) => i.name.toLowerCase().includes(q) || i.symbol.toLowerCase().includes(q)
    );
  }, [instruments, instrumentSearch]);

  const activeDateRange = useMemo(() => {
    if (datePreset === 'CUSTOM') {
      return {
        from: customFromDate || undefined,
        to: customToDate || undefined,
      };
    }
    return getPresetRange(datePreset);
  }, [datePreset, customFromDate, customToDate]);

  const filteredOperations = useMemo(() => {
    return operations.filter((operation) => {
      if (operationFilter !== 'ALL' && operation.type !== operationFilter) {
        return false;
      }

      if (activeDateRange.from && operation.operationDate < activeDateRange.from) {
        return false;
      }

      if (activeDateRange.to && operation.operationDate > activeDateRange.to) {
        return false;
      }

      if (investmentTypeFilter !== 'ALL') {
        const relatedInvestment = investmentMap.get(operation.investmentId);
        if (!relatedInvestment) {
          return false;
        }
        if (String(relatedInvestment.typeId) !== investmentTypeFilter) {
          return false;
        }
      }

      return true;
    });
  }, [operations, operationFilter, activeDateRange, investmentTypeFilter, investmentMap]);

  const totals = useMemo(() => {
    const buyTotal = filteredOperations
      .filter((operation) => operation.type === 'BUY')
      .reduce((sum, operation) => sum + operation.totalAmount, 0);
    const sellTotal = filteredOperations
      .filter((operation) => operation.type === 'SELL')
      .reduce((sum, operation) => sum + operation.totalAmount, 0);
    return { buyTotal, sellTotal };
  }, [filteredOperations]);

  const filteredPositionCount = useMemo(
    () => new Set(filteredOperations.map((operation) => operation.investmentId)).size,
    [filteredOperations]
  );

  const resetForm = () => {
    setForm({ ...EMPTY_FORM });
    setEditingId(null);
    setFormError(null);
    setShowIntegratedTransactionModal(false);
    setInstrumentSearch('');
    setInstrumentDropdownOpen(false);
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
    const instrId = relatedInvestment?.instrumentId;
    const instrItem = instrId ? instruments.find((i) => i.id === instrId) : undefined;
    setInstrumentSearch(instrItem ? `${instrItem.symbol} · ${instrItem.name}` : '');
    setInstrumentDropdownOpen(false);
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

  const onInstrumentChange = (instrumentIdRaw: string) => {
    const instrumentId = Number(instrumentIdRaw);
    const matchedInstrument = Number.isFinite(instrumentId)
      ? instruments.find((instrument) => instrument.id === instrumentId)
      : undefined;

    setForm((current) => ({
      ...current,
      instrumentId: instrumentIdRaw,
      currency: matchedInstrument?.currency || current.currency,
    }));
  };

  const getOperationDraft = (link?: { linkedAccountId?: number; linkedTransactionId?: number }): { payload?: InvestmentOperationDraft; error?: string } => {
    const instrumentId = Number(form.instrumentId);
    const quantity = Number(form.quantity);
    const unitPrice = Number(form.unitPrice);
    const fees = form.fees !== '' ? Number(form.fees) : 0;

    if (!Number.isFinite(instrumentId) || instrumentId <= 0) {
      return { error: 'Selecciona un instrumento válido para continuar.' };
    }

    if (!form.operationDate) {
      return { error: 'La fecha de la operación es obligatoria.' };
    }

    if (!Number.isFinite(quantity) || quantity <= 0) {
      return { error: 'La cantidad debe ser mayor que cero.' };
    }

    if (!Number.isFinite(unitPrice) || unitPrice <= 0) {
      return { error: 'El precio unitario debe ser mayor que cero.' };
    }

    if (!form.currency || form.currency.trim().length !== 3) {
      return { error: 'La divisa debe ser un código ISO de 3 letras.' };
    }

    return {
      payload: {
        instrumentId,
        platformId: form.platformId ? Number(form.platformId) : undefined,
        positionName: form.positionName.trim() || undefined,
        type: form.type,
        operationDate: form.operationDate,
        quantity,
        unitPrice,
        fees,
        currency: form.currency,
        linkedAccountId: link?.linkedAccountId,
        linkedTransactionId: link?.linkedTransactionId,
        notes: form.notes.trim() || undefined,
      },
    };
  };

  const buildOperationDraft = (link?: { linkedAccountId?: number; linkedTransactionId?: number }): InvestmentOperationDraft | null => {
    const { payload, error: draftError } = getOperationDraft(link);
    if (!payload) {
      setFormError(draftError || 'La operación no es válida.');
      return null;
    }
    return payload;
  };

  const integratedCashAmount = useMemo(() => {
    const { payload } = getOperationDraft();
    if (!payload) return null;
    const grossAmount = payload.quantity * payload.unitPrice;
    const rawAmount = payload.type === 'BUY'
      ? -(grossAmount + (payload.fees ?? 0))
      : grossAmount - (payload.fees ?? 0);
    // Convert to EUR if the operation is in a foreign currency and we have the rate
    if (form.currency !== 'EUR' && integratedEurRate !== null && integratedEurRate > 0) {
      return rawAmount / integratedEurRate;
    }
    return rawAmount;
  }, [form, integratedEurRate]);

  const openIntegratedTransactionModal = async () => {
    if (editingId) return;
    setFormError(null);
    if (!buildOperationDraft()) {
      return;
    }
    if (form.currency !== 'EUR') {
      try {
        const rates = await fetchExchangeRates(token, {
          fromCurrency: 'EUR',
          toCurrency: form.currency.toUpperCase(),
          from: form.operationDate,
          to: form.operationDate,
        });
        setIntegratedEurRate(rates.length > 0 ? rates[0].rate : null);
      } catch {
        setIntegratedEurRate(null);
      }
    } else {
      setIntegratedEurRate(1);
    }
    setShowIntegratedTransactionModal(true);
  };

  const handleIntegratedTransactionSubmit = async (createdTransaction: Transaction, _transactionPayload: CreateTransactionRequest) => {
    const operationPayload = buildOperationDraft({
      linkedAccountId: createdTransaction.sourceAccountId,
      linkedTransactionId: createdTransaction.id,
    });

    if (!operationPayload) {
      throw new Error('La operación ya no es válida para completar la integración.');
    }

    try {
      await addOperation(operationPayload);
      closeForm();
    } catch (err) {
      if (createdTransaction.id) {
        try {
          await deleteTransaction(token, createdTransaction.id);
        } catch (rollbackError) {
          const rollbackMessage = axios.isAxiosError(rollbackError)
            ? (rollbackError.response?.data?.message || rollbackError.message)
            : 'No se pudo revertir la transacción bancaria creada';
          throw new Error(`${(err as { message?: string })?.message || 'Error creando la operación'}; además ${rollbackMessage}.`);
        }
      }
      throw err;
    }
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setFormError(null);

    try {
      const payload = buildOperationDraft();
      if (!payload) return;

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
  const selectedInstrumentType = selectedInstrument
    ? investmentTypes.find((type) => type.id === selectedInstrument.typeId)
    : undefined;
  const selectedPlatform = selectedPlatformId
    ? platforms.find((platform) => platform.id === selectedPlatformId)
    : undefined;

  const integratedCategoryId = useMemo(() => {
    if (transactionCategories.length === 0) return undefined;

    if (form.type === 'SELL') {
      return transactionCategories.find((category) => (category.code ?? '').toUpperCase() === 'INC.INVEST')?.id;
    }

    const investmentCategoryByTypeCode: Record<string, string> = {
      STOCK: 'SAV.STOCKS',
      BOND: 'SAV.BONDS',
      ETF: 'SAV.ETFS',
      FUND: 'SAV.FUNDS',
      CRYPTO: 'SAV.CRYPTO',
    };

    const instrumentTypeCode = (selectedInstrumentType?.code ?? '').toUpperCase();
    const preferredCode = investmentCategoryByTypeCode[instrumentTypeCode] || 'SAV.OTH';

    return transactionCategories.find((category) => (category.code ?? '').toUpperCase() === preferredCode)?.id
      ?? transactionCategories.find((category) => (category.code ?? '').toUpperCase() === 'SAV.OTH')?.id
      ?? transactionCategories.find((category) => (category.code ?? '').toUpperCase() === 'SAV')?.id;
  }, [form.type, selectedInstrumentType, transactionCategories]);

  const integratedTransactionTitle = form.type === 'BUY'
    ? '💳 Compra enlazada a cuenta'
    : '💳 Venta enlazada a cuenta';

  const integratedTransactionDescription = useMemo(() => {
    const label = selectedInstrument?.symbol || selectedInstrument?.name || form.positionName.trim() || 'inversión';
    const baseDesc = form.type === 'BUY'
      ? `Compra de ${label}`
      : `Venta de ${label}`;
    if (form.currency !== 'EUR' && integratedEurRate !== null && integratedEurRate > 0) {
      return `${baseDesc} (cambio ${form.currency}/EUR ${integratedEurRate.toFixed(4)})`;
    }
    return baseDesc;
  }, [form.positionName, form.type, form.currency, selectedInstrument, integratedEurRate]);

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
      if (confirmDelete.linkedTransactionId != null) {
        try {
          await deleteTransaction(token, confirmDelete.linkedTransactionId);
        } catch {
          // operation already removed; swallow banking error to avoid confusing the user
        }
        dispatchFinanceEvent(FINANCE_EVENTS.TRANSACTIONS_UPDATED);
      }
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
            <span className="iot-stat-value">{filteredOperations.length}</span>
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
            <span className="iot-stat-value">{filteredPositionCount}</span>
          </div>
        </div>

        <button className={`iot-btn-add${showForm ? ' iot-btn-add--cancel' : ''}`} type="button" onClick={() => showForm ? closeForm() : openCreate()} disabled={catalogLoading && !showForm}>
          {showForm ? '✕ Cancelar' : '+ Nueva operación'}
        </button>
      </div>

      <div className="iot-filters" aria-label="Filtros de operaciones FIFO">

        {/* ── Fecha ─────────────────────────────────────────────────────── */}
        <div className="iot-filter-group">
          <span className="iot-filter-label">Fecha</span>
          <div className="iot-pill-row">
            {([
              { value: 'ALL',           label: 'Todo' },
              { value: 'THIS_MONTH',    label: 'Este mes' },
              { value: 'LAST_3_MONTHS', label: 'Últimos 3m' },
              { value: 'LAST_YEAR',     label: 'Último año' },
              { value: 'PREVIOUS_YEAR', label: 'Año pasado' },
              { value: 'CUSTOM',        label: '📅 Personalizado' },
            ] as { value: DatePreset; label: string }[]).map(({ value, label }) => (
              <button
                key={value}
                type="button"
                className={`iot-preset-btn${datePreset === value ? ' active' : ''}`}
                onClick={() => setDatePreset(value)}
              >
                {label}
              </button>
            ))}
          </div>

          {datePreset === 'CUSTOM' && (
            <div className="iot-custom-dates">
              <label>
                Desde
                <input type="date" value={customFromDate} onChange={(e) => setCustomFromDate(e.target.value)} />
              </label>
              <span className="iot-date-sep">–</span>
              <label>
                Hasta
                <input type="date" value={customToDate} onChange={(e) => setCustomToDate(e.target.value)} />
              </label>
            </div>
          )}
        </div>

        {/* ── Tipo operación ─────────────────────────────────────────────── */}
        <div className="iot-filter-group">
          <span className="iot-filter-label">Operación</span>
          <div className="iot-pill-row">
            <button
              type="button"
              className={`iot-preset-btn${operationFilter === 'ALL' ? ' active' : ''}`}
              onClick={() => setOperationFilter('ALL')}
            >
              📊 Todas
            </button>
            <button
              type="button"
              className={`iot-preset-btn iot-preset-btn--buy${operationFilter === 'BUY' ? ' active' : ''}`}
              onClick={() => setOperationFilter('BUY')}
            >
              🔴 Compra
            </button>
            <button
              type="button"
              className={`iot-preset-btn iot-preset-btn--sell${operationFilter === 'SELL' ? ' active' : ''}`}
              onClick={() => setOperationFilter('SELL')}
            >
              🟢 Venta
            </button>
          </div>
        </div>

        {/* ── Tipo inversión ─────────────────────────────────────────────── */}
        <div className="iot-filter-group">
          <span className="iot-filter-label">Instrumento</span>
          <div className="iot-pill-row">
            <button
              type="button"
              className={`iot-preset-btn${investmentTypeFilter === 'ALL' ? ' active' : ''}`}
              onClick={() => setInvestmentTypeFilter('ALL')}
            >
              ✨ Todos
            </button>
            {investmentTypes.map((type) => {
              const visual = getInvestmentTypeVisual(type.code, type.name);
              const isActive = investmentTypeFilter === String(type.id);
              return (
                <button
                  key={type.id}
                  type="button"
                  className={`iot-preset-btn iot-preset-btn--type${isActive ? ' active' : ''}`}
                  style={isActive ? { background: visual.background, color: visual.color, borderColor: visual.color } : undefined}
                  onClick={() => setInvestmentTypeFilter(isActive ? 'ALL' : String(type.id))}
                >
                  {visual.emoji} {type.name}
                </button>
              );
            })}
          </div>
        </div>

        {/* ── Limpiar ────────────────────────────────────────────────────── */}
        <button
          type="button"
          className="iot-btn-clear-filters"
          onClick={() => {
            setDatePreset('ALL');
            setCustomFromDate('');
            setCustomToDate('');
            setOperationFilter('ALL');
            setInvestmentTypeFilter('ALL');
          }}
        >
          ✕ Limpiar
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
            {!editingId && (
              <button
                className="iot-integrated-trigger"
                type="button"
                onClick={openIntegratedTransactionModal}
                disabled={submitting || catalogLoading}
                title="Abrir creación integrada con una transacción bancaria"
              >
                Integrada
              </button>
            )}
            <div className="modal-header">
              <h4>{editingId ? 'Editar operación' : 'Añadir operación'}</h4>
              <button className="modal-close" type="button" onClick={closeForm}>✕</button>
            </div>

            <form className="modal-body iot-form" onSubmit={submit}>
              <div className="iot-form-row iot-form-row--pair">
                <div className="modal-row">
                  <label>Instrumento</label>
                  <div className="iot-instrument-combo" ref={instrumentComboRef}>
                    <input
                      type="text"
                      className="iot-select iot-instrument-input"
                      placeholder="Buscar por nombre o símbolo…"
                      value={instrumentSearch}
                      autoComplete="off"
                      onChange={(e) => {
                        setInstrumentSearch(e.target.value);
                        setInstrumentDropdownOpen(true);
                        if (form.instrumentId) onChange('instrumentId', '');
                      }}
                      onFocus={() => setInstrumentDropdownOpen(true)}
                      onBlur={() => setTimeout(() => setInstrumentDropdownOpen(false), 160)}
                    />
                    {instrumentDropdownOpen && (
                      <div className="iot-instrument-dropdown">
                        {filteredInstruments.length === 0 ? (
                          <div className="iot-instrument-empty">Sin resultados</div>
                        ) : (
                          filteredInstruments.map((instrument) => (
                            <div
                              key={instrument.id}
                              className={`iot-instrument-option${form.instrumentId === String(instrument.id) ? ' selected' : ''}`}
                              onMouseDown={() => {
                                onInstrumentChange(String(instrument.id));
                                setInstrumentSearch(`${instrument.symbol} · ${instrument.name}`);
                                setInstrumentDropdownOpen(false);
                              }}
                            >
                              <span className="iot-instr-symbol">{instrument.symbol}</span>
                              <span className="iot-instr-name">{instrument.name}</span>
                            </div>
                          ))
                        )}
                      </div>
                    )}
                  </div>
                </div>

                <div className="modal-row">
                  <label>Plataforma</label>
                  <select className="iot-select" value={form.platformId} onChange={(event) => onChange('platformId', event.target.value)}>
                    <option value="">Sin plataforma</option>
                    {platforms.map((platform) => (
                      <option key={platform.id} value={platform.id}>{platform.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="iot-form-row">
                <div className="modal-row">
                  <label>Nombre de la posición (opcional)</label>
                  <input
                    className="iot-input"
                    type="text"
                    maxLength={150}
                    placeholder="Ej: Cartera principal SP500"
                    value={form.positionName}
                    onChange={(event) => onChange('positionName', event.target.value)}
                  />
                </div>
              </div>

              <div className="iot-form-row iot-form-row--triple">
                <div className="modal-row">
                  <label>Divisa</label>
                  <select className="iot-select" value={form.currency} onChange={(event) => onChange('currency', event.target.value)}>
                    {INVESTMENT_CURRENCY_OPTIONS.map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                </div>

                <div className="modal-row">
                  <label>Fecha</label>
                  <input className="iot-input" required type="date" value={form.operationDate} onChange={(event) => onChange('operationDate', event.target.value)} />
                </div>

                <div className="iot-type-row iot-type-row--compact">
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
              </div>

              <div className="iot-form-row iot-form-row--triple">
                <div className="modal-row">
                  <label>Cantidad</label>
                  <input className="iot-input" required type="number" min="0.00000001" step="0.00000001" value={form.quantity} onChange={(event) => onChange('quantity', event.target.value)} />
                </div>
                <div className="modal-row">
                  <label>Precio unitario</label>
                  <input className="iot-input" required type="number" min="0.00000001" step="0.00000001" value={form.unitPrice} onChange={(event) => onChange('unitPrice', event.target.value)} />
                </div>
                <div className="modal-row">
                  <label>Fees</label>
                  <input className="iot-input" type="number" min="0" step="0.01" value={form.fees} onChange={(event) => onChange('fees', event.target.value)} />
                </div>
              </div>

              {selectedInstrument && (
                <div className="iot-selected-investment iot-field--full">
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

              <div className="modal-row iot-field--full">
                <label>Notas</label>
                <textarea className="iot-textarea" rows={4} placeholder="Comentario opcional de la operación" value={form.notes} onChange={(event) => onChange('notes', event.target.value)} />
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

      {showIntegratedTransactionModal && !editingId && (
        <CreateTransactionModal
          accessToken={token}
          onClose={() => setShowIntegratedTransactionModal(false)}
          onSuccess={() => undefined}
          title={integratedTransactionTitle}
          submitLabel="Crear transacción y operación"
          hideDestinationAccount
          lockAmount
          lockBookingDate
          lockValueDate
          initialValues={{
            amount: integratedCashAmount ?? undefined,
            bookingDate: form.operationDate,
            valueDate: form.operationDate,
            currency: (form.currency !== 'EUR' && integratedEurRate !== null && integratedEurRate > 0) ? 'EUR' : form.currency,
            description: integratedTransactionDescription,
            categoryId: integratedCategoryId,
          }}
          onSubmitTransaction={handleIntegratedTransactionSubmit}
        />
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
              {confirmDelete.linkedTransactionId != null && (
                <p className="iot-confirm-warning">
                  La <strong>transacción bancaria vinculada</strong> también se eliminará.
                </p>
              )}
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
            {filteredOperations.length === 0 && (
              <tr>
                <td className="iot-empty-row" colSpan={9}>No hay operaciones para los filtros seleccionados.</td>
              </tr>
            )}

            {filteredOperations.map((operation) => {
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
                        onClick={() => setConfirmDelete({ id: operation.id, label: `${operation.type} · ${fmtDate(operation.operationDate)}`, linkedTransactionId: operation.linkedTransactionId })}
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