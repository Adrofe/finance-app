import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import type { CreateTransactionRequest } from '../types/banking';
import { CatalogService, Account, Tag, Merchant, TransactionCategory, TransactionStatus, TransactionType } from '../services/catalogService';
import { getCategoryVisual, getMerchantLogo, getInstitutionLogo } from '../constants/visualConfig';
import './CreateTransactionModal.css';

type CreateTransactionModalProps = {
  accessToken: string;
  onClose: () => void;
  onSuccess: () => void;
};

export function CreateTransactionModal({ accessToken, onClose, onSuccess }: CreateTransactionModalProps) {
  // Form state
  const [sourceAccountId, setSourceAccountId] = useState<number | undefined>();
  const [destinationAccountId, setDestinationAccountId] = useState<number | undefined>();
  const [amount, setAmount] = useState<string>('');
  const [bookingDate, setBookingDate] = useState<string>(new Date().toISOString().split('T')[0]);
  const [valueDate, setValueDate] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [merchantId, setMerchantId] = useState<number | undefined>();
  const [statusId, setStatusId] = useState<number | undefined>();
  const [selectedTags, setSelectedTags] = useState<number[]>([]);

  // Catalogs
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [categories, setCategories] = useState<TransactionCategory[]>([]);
  const [statuses, setStatuses] = useState<TransactionStatus[]>([]);
  const [types, setTypes] = useState<TransactionType[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [merchants, setMerchants] = useState<Merchant[]>([]);

  // Category picker state
  const [showCategoryPicker, setShowCategoryPicker] = useState(false);
  const [categorySearch, setCategorySearch] = useState('');
  const categoryPickerRef = useRef<HTMLDivElement>(null);
  const categoryDialogRef = useRef<HTMLDivElement>(null);

  // Account dropdown state
  const [sourceAccountOpen, setSourceAccountOpen] = useState(false);
  const [destinationAccountOpen, setDestinationAccountOpen] = useState(false);
  const sourceAccountRef = useRef<HTMLDivElement>(null);
  const destinationAccountRef = useRef<HTMLDivElement>(null);

  // Merchant dropdown state
  const [merchantQuery, setMerchantQuery] = useState('');
  const [merchantOpen, setMerchantOpen] = useState(false);
  const merchantRef = useRef<HTMLDivElement>(null);

  // Tag dropdown state
  const [tagQuery, setTagQuery] = useState('');
  const [tagOpen, setTagOpen] = useState(false);
  const tagRef = useRef<HTMLDivElement>(null);

  // Loading states
  const [loading, setLoading] = useState(false);
  const [catalogsLoading, setCatalogsLoading] = useState(true);

  // Load catalogs on mount
  useEffect(() => {
    if (!accessToken) return;
    setCatalogsLoading(true);
    Promise.all([
      CatalogService.fetchAccounts(accessToken),
      CatalogService.fetchCategories(accessToken),
      CatalogService.fetchStatuses(accessToken),
      CatalogService.fetchTypes(accessToken),
      CatalogService.fetchTags(accessToken),
      CatalogService.fetchMerchants(accessToken),
    ]).then(([accs, cats, stats, typs, tgs, merch]) => {
      setAccounts(accs);
      setCategories(cats);
      setStatuses(stats);
      setTypes(typs);
      setTags(tgs);
      setMerchants(merch);
      const bookedStatus = stats.find(s => s.code === 'BOOKED');
      if (bookedStatus) setStatusId(bookedStatus.id);
    }).finally(() => setCatalogsLoading(false));
  }, [accessToken]);

  // Close dropdowns/picker on outside click
  useEffect(() => {
    const handle = (e: MouseEvent) => {
      const target = e.target as Node;

      if (merchantRef.current && !merchantRef.current.contains(e.target as Node)) {
        setMerchantOpen(false);
      }
      if (tagRef.current && !tagRef.current.contains(e.target as Node)) {
        setTagOpen(false);
      }
      if (sourceAccountRef.current && !sourceAccountRef.current.contains(e.target as Node)) {
        setSourceAccountOpen(false);
      }
      if (destinationAccountRef.current && !destinationAccountRef.current.contains(e.target as Node)) {
        setDestinationAccountOpen(false);
      }

      const clickedCategoryTrigger = categoryPickerRef.current?.contains(target);
      const clickedCategoryDialog = categoryDialogRef.current?.contains(target);
      if (!clickedCategoryTrigger && !clickedCategoryDialog) {
        setShowCategoryPicker(false);
      }
    };
    document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, []);

  // Currency from source account
  const selectedSourceAccount = accounts.find(a => a.id === sourceAccountId);
  const selectedDestinationAccount = accounts.find(a => a.id === destinationAccountId);
  const currency = selectedSourceAccount?.currency;

  const renderInstitutionLogo = (institutionName?: string) => {
    const logo = getInstitutionLogo(institutionName || '');
    if (logo.startsWith('/')) {
      return (
        <>
          <img
            src={logo}
            alt={institutionName || 'Banco'}
            onError={e => {
              (e.currentTarget.style.display = 'none');
              const fallback = e.currentTarget.nextElementSibling as HTMLSpanElement | null;
              if (fallback) fallback.style.display = 'inline';
            }}
          />
          <span style={{ display: 'none' }}>🏦</span>
        </>
      );
    }
    return <span>{logo}</span>;
  };

  // ── Category helpers ──────────────────────────────────────────────────────
  const parentCategories = categories.filter(c => !c.parentId);
  const childCategoriesMap = categories.reduce<Record<number, TransactionCategory[]>>((acc, cat) => {
    if (cat.parentId) {
      if (!acc[cat.parentId]) acc[cat.parentId] = [];
      acc[cat.parentId].push(cat);
    }
    return acc;
  }, {});

  const selectedCategory = categories.find(c => c.id === categoryId);

  const filteredParents = parentCategories.filter(p => {
    if (!categorySearch.trim()) return true;
    const q = categorySearch.toLowerCase();
    return (
      p.name.toLowerCase().includes(q) ||
      (childCategoriesMap[p.id] || []).some(c => c.name.toLowerCase().includes(q))
    );
  });

  const getVisibleChildren = (parentId: number): TransactionCategory[] => {
    const children = childCategoriesMap[parentId] || [];
    if (!categorySearch.trim()) return children;
    const q = categorySearch.toLowerCase();
    return children.filter(c => c.name.toLowerCase().includes(q));
  };

  const pickCategory = (id: number | undefined) => {
    setCategoryId(id);
    setShowCategoryPicker(false);
    setCategorySearch('');
  };

  // ── Merchant helpers ──────────────────────────────────────────────────────
  const selectedMerchant = merchants.find(m => m.id === merchantId);

  const filteredMerchants = merchantQuery.trim()
    ? merchants.filter(m => m.name.toLowerCase().includes(merchantQuery.toLowerCase()))
    : merchants;

  const handleCreateMerchant = async (name: string) => {
    const cleanName = name.trim();
    if (!cleanName) return;

    try {
      const created = await CatalogService.createMerchant(accessToken, cleanName);
      setMerchants(prev => [...prev, created]);
      setMerchantId(created.id);
      setMerchantQuery('');
      setMerchantOpen(false);
    } catch (err) {
      console.error('Error creating merchant:', err);
      const message = axios.isAxiosError(err)
        ? (err.response?.data?.message || err.response?.data?.error || err.message)
        : 'No se pudo crear el comerciante';
      alert(`Error al crear comerciante: ${message}`);
    }
  };

  // ── Tag helpers ───────────────────────────────────────────────────────────
  const filteredTags = tagQuery.trim()
    ? tags.filter(t => t.name.toLowerCase().includes(tagQuery.toLowerCase()))
    : tags;

  const toggleTag = (tagId: number) => {
    setSelectedTags(prev =>
      prev.includes(tagId) ? prev.filter(id => id !== tagId) : [...prev, tagId]
    );
  };

  const handleCreateTag = async (name: string) => {
    const cleanName = name.trim();
    if (!cleanName) return;

    try {
      const created = await CatalogService.createTag(accessToken, cleanName);
      setTags(prev => [...prev, created]);
      setSelectedTags(prev => [...prev, created.id]);
      setTagQuery('');
    } catch (err) {
      console.error('Error creating tag:', err);
      const message = axios.isAxiosError(err)
        ? (err.response?.data?.message || err.response?.data?.error || err.message)
        : 'No se pudo crear la etiqueta';
      alert(`Error al crear etiqueta: ${message}`);
    }
  };

  // ── Submit ────────────────────────────────────────────────────────────────
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sourceAccountId || !amount || !bookingDate) return;

    setLoading(true);
    try {
      const amountNum = parseFloat(amount);

      let typeId: number | undefined;
      if (sourceAccountId && destinationAccountId) {
        typeId = types.find(t => t.name.toLowerCase().includes('transfer'))?.id;
      } else if (amountNum > 0) {
        typeId = types.find(t => t.name.toLowerCase().includes('income') || t.name.toLowerCase().includes('ingreso'))?.id;
      } else {
        typeId = types.find(t => t.name.toLowerCase().includes('expense') || t.name.toLowerCase().includes('gasto'))?.id;
      }

      const transaction: CreateTransactionRequest = {
        sourceAccountId,
        destinationAccountId,
        amount: amountNum,
        bookingDate: `${bookingDate}T00:00:00`,
        valueDate: `${(valueDate || bookingDate)}T00:00:00`,
        currency,
        description: description || undefined,
        merchantId,
        categoryId,
        tagIds: selectedTags.length > 0 ? selectedTags : undefined,
        statusId,
        typeId,
      };

      const { createTransaction } = await import('../services/transactionsService');
      await createTransaction(accessToken, transaction);
      onSuccess();
      onClose();
    } catch (error) {
      console.error('Error creating transaction:', error);
      const message = axios.isAxiosError(error)
        ? (error.response?.data?.message || error.response?.data?.error || error.message)
        : 'Error desconocido';
      alert(`Error al crear la transaccion: ${message}`);
    } finally {
      setLoading(false);
    }
  };

  // ── Loading screen ────────────────────────────────────────────────────────
  if (catalogsLoading) {
    return (
      <div className="transaction-modal-backdrop" onClick={onClose}>
        <div className="transaction-modal" onClick={e => e.stopPropagation()}>
          <div className="transaction-modal-header">
            <h2>Cargando...</h2>
          </div>
        </div>
      </div>
    );
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="transaction-modal-backdrop" onClick={onClose}>
      <div className="transaction-modal" onClick={e => e.stopPropagation()}>
        <div className="transaction-modal-header">
          <h2>💳 Nueva Transacción</h2>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="transaction-modal-body">
            <div className="form-grid">

              {/* Accounts */}
              <div className="form-group">
                <label htmlFor="sourceAccount" className="form-label">
                  Cuenta Origen <span className="required">*</span>
                </label>
                <div className="searchable-dropdown" ref={sourceAccountRef}>
                  <button
                    id="sourceAccount"
                    type="button"
                    className={'form-input account-select-trigger' + (sourceAccountOpen ? ' active' : '')}
                    onClick={() => setSourceAccountOpen(v => !v)}
                  >
                    {selectedSourceAccount ? (
                      <span className="account-option">
                        <span className="account-logo">{renderInstitutionLogo(selectedSourceAccount.institutionName)}</span>
                        <span className="account-info">
                          <span className="account-name">{selectedSourceAccount.name}</span>
                          <span className="account-bank">{selectedSourceAccount.institutionName || 'Sin banco'}</span>
                        </span>
                      </span>
                    ) : (
                      <span className="category-trigger-placeholder">Selecciona la cuenta origen</span>
                    )}
                    <span className="category-trigger-arrow">{sourceAccountOpen ? '▲' : '▼'}</span>
                  </button>

                  {sourceAccountOpen && (
                    <div className="dropdown-results">
                      {accounts.map(acc => (
                        <div
                          key={acc.id}
                          className={'dropdown-item' + (sourceAccountId === acc.id ? ' selected' : '')}
                          onMouseDown={e => e.preventDefault()}
                          onClick={() => { setSourceAccountId(acc.id); setSourceAccountOpen(false); }}
                        >
                          <span className="account-logo">{renderInstitutionLogo(acc.institutionName)}</span>
                          <span className="account-info">
                            <span className="account-name">{acc.name}</span>
                            <span className="account-bank">{acc.institutionName || 'Sin banco'}</span>
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="destinationAccount" className="form-label">Cuenta Destino</label>
                <div className="searchable-dropdown" ref={destinationAccountRef}>
                  <button
                    id="destinationAccount"
                    type="button"
                    className={'form-input account-select-trigger' + (destinationAccountOpen ? ' active' : '')}
                    onClick={() => setDestinationAccountOpen(v => !v)}
                  >
                    {selectedDestinationAccount ? (
                      <span className="account-option">
                        <span className="account-logo">{renderInstitutionLogo(selectedDestinationAccount.institutionName)}</span>
                        <span className="account-info">
                          <span className="account-name">{selectedDestinationAccount.name}</span>
                          <span className="account-bank">{selectedDestinationAccount.institutionName || 'Sin banco'}</span>
                        </span>
                      </span>
                    ) : (
                      <span className="category-trigger-placeholder">Sin cuenta destino</span>
                    )}
                    <span className="category-trigger-arrow">{destinationAccountOpen ? '▲' : '▼'}</span>
                  </button>

                  {destinationAccountOpen && (
                    <div className="dropdown-results">
                      <div
                        className={'dropdown-item' + (!destinationAccountId ? ' selected' : '')}
                        onMouseDown={e => e.preventDefault()}
                        onClick={() => { setDestinationAccountId(undefined); setDestinationAccountOpen(false); }}
                      >
                        <span className="account-logo">🏦</span>
                        <span className="account-info">
                          <span className="account-name">Sin cuenta destino</span>
                          <span className="account-bank">Opcional</span>
                        </span>
                      </div>
                      {accounts.map(acc => (
                        <div
                          key={acc.id}
                          className={'dropdown-item' + (destinationAccountId === acc.id ? ' selected' : '')}
                          onMouseDown={e => e.preventDefault()}
                          onClick={() => { setDestinationAccountId(acc.id); setDestinationAccountOpen(false); }}
                        >
                          <span className="account-logo">{renderInstitutionLogo(acc.institutionName)}</span>
                          <span className="account-info">
                            <span className="account-name">{acc.name}</span>
                            <span className="account-bank">{acc.institutionName || 'Sin banco'}</span>
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Amount + Status */}
              <div className="form-group">
                <label htmlFor="amount" className="form-label">
                  Cantidad <span className="required">*</span>
                </label>
                <input
                  id="amount"
                  type="number"
                  step="0.01"
                  className="form-input"
                  value={amount}
                  onChange={e => setAmount(e.target.value)}
                  placeholder="100.00"
                  required
                />
                {currency && <span className="currency-badge">{currency}</span>}
              </div>

              <div className="form-group">
                <label htmlFor="status" className="form-label">Estado</label>
                <select
                  id="status"
                  className="form-input"
                  value={statusId || ''}
                  onChange={e => setStatusId(Number(e.target.value))}
                >
                  {statuses.map(s => (
                    <option key={s.id} value={s.id}>{s.code}</option>
                  ))}
                </select>
              </div>

              {/* Dates */}
              <div className="form-group">
                <label htmlFor="bookingDate" className="form-label">
                  Fecha de Reserva <span className="required">*</span>
                </label>
                <input
                  id="bookingDate"
                  type="date"
                  className="form-input"
                  value={bookingDate}
                  onChange={e => setBookingDate(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="valueDate" className="form-label">Fecha de Valor</label>
                <input
                  id="valueDate"
                  type="date"
                  className="form-input"
                  value={valueDate}
                  onChange={e => setValueDate(e.target.value)}
                />
                <small className="form-hint">Por defecto se usa la fecha de reserva</small>
              </div>

              {/* Category Picker */}
              <div className="form-group" ref={categoryPickerRef}>
                <label className="form-label">Categoría</label>

                <button
                  type="button"
                  className={'category-trigger' + (showCategoryPicker ? ' active' : '')}
                  onClick={() => { setShowCategoryPicker(v => !v); setCategorySearch(''); }}
                >
                  {selectedCategory ? (
                    <span className="category-trigger-value">
                      <span>{getCategoryVisual(selectedCategory.code || selectedCategory.name).emoji}</span>
                      <span>
                        {selectedCategory.parentName
                          ? selectedCategory.parentName + ' › ' + selectedCategory.name
                          : selectedCategory.name}
                      </span>
                    </span>
                  ) : (
                    <span className="category-trigger-placeholder">Seleccionar categoría...</span>
                  )}
                  <span className="category-trigger-arrow">{showCategoryPicker ? '▲' : '▼'}</span>
                </button>
              </div>

              {/* Category picker floating dialog */}
              {showCategoryPicker && (
                <div
                  className="category-picker-overlay"
                  onClick={() => setShowCategoryPicker(false)}
                >
                  <div
                    ref={categoryDialogRef}
                    className="category-picker-dialog"
                    onClick={e => e.stopPropagation()}
                  >
                    <div className="category-picker-header">
                      <span>Seleccionar categoría</span>
                      <button
                        type="button"
                        className="category-picker-close"
                        onClick={() => setShowCategoryPicker(false)}
                      >
                        ×
                      </button>
                    </div>

                    <div className="category-picker-search-bar">
                      <span className="category-search-icon">🔍</span>
                      <input
                        className="category-search-input"
                        placeholder="Filtrar categorías..."
                        value={categorySearch}
                        onChange={e => setCategorySearch(e.target.value)}
                        autoFocus
                      />
                      {categorySearch && (
                        <button type="button" className="category-search-clear" onClick={() => setCategorySearch('')}>×</button>
                      )}
                    </div>

                    <div className="category-picker-list">
                      <div
                        className={'category-none-item' + (!categoryId ? ' selected' : '')}
                        onClick={() => pickCategory(undefined)}
                      >
                        Sin categoría
                      </div>

                      <div className="category-picker-grid">
                        {filteredParents.map(parent => {
                          const pv = getCategoryVisual(parent.code || parent.name);
                          const children = getVisibleChildren(parent.id);
                          const totalChildren = (childCategoriesMap[parent.id] || []).length;
                          return (
                            <div key={parent.id} className="category-group">
                              <div
                                className={'category-parent-row' + (categoryId === parent.id ? ' selected' : '')}
                                onClick={() => pickCategory(parent.id)}
                              >
                                <span className="cat-emoji">{pv.emoji}</span>
                                <span className="cat-name">{parent.name}</span>
                                {totalChildren > 0 && (
                                  <span className="cat-count">{totalChildren}</span>
                                )}
                              </div>
                              {children.map(child => {
                                const cv = getCategoryVisual(child.code || child.name);
                                return (
                                  <div
                                    key={child.id}
                                    className={'category-child-row' + (categoryId === child.id ? ' selected' : '')}
                                    onClick={() => pickCategory(child.id)}
                                  >
                                    <span className="cat-emoji">{cv.emoji}</span>
                                    <span className="cat-name">{child.name}</span>
                                  </div>
                                );
                              })}
                            </div>
                          );
                        })}
                      </div>

                      {filteredParents.length === 0 && (
                        <div className="category-no-results">
                          Sin resultados para "{categorySearch}"
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* Merchant live search */}
              <div className="form-group">
                <label className="form-label">Comerciante</label>
                <div className="searchable-dropdown" ref={merchantRef}>
                  {selectedMerchant ? (
                    <div className="selected-chip">
                      <span className="chip-emoji">{getMerchantLogo(selectedMerchant.name)}</span>
                      <span className="chip-label">{selectedMerchant.name}</span>
                      <button
                        type="button"
                        className="chip-clear"
                        onClick={() => setMerchantId(undefined)}
                      >
                        ×
                      </button>
                    </div>
                  ) : (
                    <input
                      type="text"
                      className="form-input"
                      placeholder="Buscar comerciante..."
                      value={merchantQuery}
                      onChange={e => { setMerchantQuery(e.target.value); setMerchantOpen(true); }}
                      onFocus={() => setMerchantOpen(true)}
                    />
                  )}

                  {merchantOpen && !selectedMerchant && (
                    <div className="dropdown-results">
                      {filteredMerchants.slice(0, 25).map(m => (
                        <div
                          key={m.id}
                          className="dropdown-item"
                          onMouseDown={e => e.preventDefault()}
                          onClick={() => { setMerchantId(m.id); setMerchantOpen(false); setMerchantQuery(''); }}
                        >
                          <span className="merchant-logo">{getMerchantLogo(m.name)}</span>
                          <span>{m.name}</span>
                        </div>
                      ))}
                      {merchantQuery.trim() && filteredMerchants.length === 0 && (
                        <>
                          <div className="dropdown-no-results">Sin resultados para "{merchantQuery}"</div>
                          <div
                            className="dropdown-item create-new"
                            onMouseDown={e => e.preventDefault()}
                            onClick={() => handleCreateMerchant(merchantQuery.trim())}
                          >
                            ➕ Crear "{merchantQuery.trim()}"
                          </div>
                        </>
                      )}
                      {!merchantQuery.trim() && merchants.length === 0 && (
                        <div className="dropdown-no-results">No hay comerciantes aun</div>
                      )}
                    </div>
                  )}
                </div>
              </div>

              {/* Tags live search + multi-select */}
              <div className="form-group">
                <label className="form-label">Etiquetas</label>
                <div className="searchable-dropdown" ref={tagRef}>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Buscar o añadir etiquetas..."
                    value={tagQuery}
                    onChange={e => { setTagQuery(e.target.value); setTagOpen(true); }}
                    onFocus={() => setTagOpen(true)}
                  />

                  {tagOpen && (
                    <div className="dropdown-results">
                      {filteredTags.slice(0, 25).map(tag => (
                        <div
                          key={tag.id}
                          className={'dropdown-item' + (selectedTags.includes(tag.id) ? ' selected' : '')}
                          onMouseDown={e => e.preventDefault()}
                          onClick={() => { toggleTag(tag.id); setTagQuery(''); }}
                        >
                          <span>🏷️</span>
                          <span>{tag.name}</span>
                          {selectedTags.includes(tag.id) && <span className="item-check">✓</span>}
                        </div>
                      ))}
                      {tagQuery.trim() && filteredTags.length === 0 && (
                        <>
                          <div className="dropdown-no-results">Sin resultados para "{tagQuery}"</div>
                          <div
                            className="dropdown-item create-new"
                            onMouseDown={e => e.preventDefault()}
                            onClick={() => handleCreateTag(tagQuery.trim())}
                          >
                            ➕ Crear "{tagQuery.trim()}"
                          </div>
                        </>
                      )}
                      {!tagQuery.trim() && tags.length === 0 && (
                        <div className="dropdown-no-results">No hay etiquetas aun</div>
                      )}
                    </div>
                  )}
                </div>

                {selectedTags.length > 0 && (
                  <div className="tag-pills">
                    {selectedTags.map(tagId => {
                      const tag = tags.find(t => t.id === tagId);
                      return tag ? (
                        <span key={tagId} className="tag-pill" onClick={() => toggleTag(tagId)}>
                          {tag.name}
                          <span className="tag-pill-remove">×</span>
                        </span>
                      ) : null;
                    })}
                  </div>
                )}
              </div>

              {/* Description */}
              <div className="form-group full-width">
                <label htmlFor="description" className="form-label">Descripción</label>
                <textarea
                  id="description"
                  className="form-input form-textarea"
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  placeholder="Añade una nota sobre esta transacción..."
                  rows={3}
                />
              </div>

            </div>
          </div>

          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose} disabled={loading}>
              Cancelar
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading || !sourceAccountId || !amount || !bookingDate}
            >
              {loading && <span className="loading-spinner" />}
              {loading ? 'Creando...' : 'Crear Transacción'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
