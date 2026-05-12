import React, { useMemo, useState, useEffect } from 'react';
import { Merchant, Category } from '../types/banking';
import { fetchMerchants, updateMerchant, fetchCategories, deleteMerchant, createMerchant } from '../services/merchantsService';
import { getCategoryVisual } from '../constants/visualConfig';
import './CreateTransactionModal.css';
import './MerchantEditPanel.css';

interface MerchantEditPanelProps {
  token: string;
  onUnauthorized?: (message: string) => void;
}

export const MerchantEditPanel: React.FC<MerchantEditPanelProps> = ({ token, onUnauthorized }) => {
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editValues, setEditValues] = useState<{ name: string; categoryId: number | null }>({ name: '', categoryId: null });
  const [showCategoryPicker, setShowCategoryPicker] = useState(false);
  const [categorySearch, setCategorySearch] = useState('');
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [createValues, setCreateValues] = useState<{ name: string; categoryId: number | null }>({ name: '', categoryId: null });
  const [creating, setCreating] = useState(false);
  const [showCreateCategoryPicker, setShowCreateCategoryPicker] = useState(false);

  const sortedMerchants = useMemo(
    () => [...merchants].sort((a, b) => a.name.localeCompare(b.name, 'es')),
    [merchants]
  );

  const selectedCategory = useMemo(
    () => categories.find(c => c.id === editValues.categoryId) || null,
    [categories, editValues.categoryId]
  );

  const selectedCreateCategory = useMemo(
    () => categories.find(c => c.id === createValues.categoryId) || null,
    [categories, createValues.categoryId]
  );

  const parentCategories = useMemo(
    () => categories.filter(c => !c.parentId),
    [categories]
  );

  const childCategoriesMap = useMemo(() => {
    const map: Record<number, Category[]> = {};
    categories.forEach(c => {
      if (!c.parentId) return;
      if (!map[c.parentId]) map[c.parentId] = [];
      map[c.parentId].push(c);
    });
    return map;
  }, [categories]);

  const normalizedSearch = categorySearch.trim().toLowerCase();

  const filteredParents = useMemo(() => {
    if (!normalizedSearch) return parentCategories;

    return parentCategories.filter(parent => {
      const inParent = [parent.name, parent.code].filter(Boolean).join(' ').toLowerCase().includes(normalizedSearch);
      if (inParent) return true;

      const children = childCategoriesMap[parent.id] || [];
      return children.some(child =>
        [child.name, child.code].filter(Boolean).join(' ').toLowerCase().includes(normalizedSearch)
      );
    });
  }, [normalizedSearch, parentCategories, childCategoriesMap]);

  const getVisibleChildren = (parentId: number) => {
    const children = childCategoriesMap[parentId] || [];
    if (!normalizedSearch) return children;
    return children.filter(child =>
      [child.name, child.code].filter(Boolean).join(' ').toLowerCase().includes(normalizedSearch)
    );
  };

  useEffect(() => {
    if (!token) return;
    
    const loadData = async () => {
      try {
        setLoading(true);
        setError(null);
        const [merchData, catData] = await Promise.all([
          fetchMerchants(token),
          fetchCategories(token),
        ]);
        setMerchants(merchData);
        setCategories(catData);
      } catch (err: any) {
        if (err?.response?.status === 401) {
          onUnauthorized?.('Sesión expirada');
        } else {
          setError(err?.response?.data?.message || 'Error loading merchants');
        }
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [token, onUnauthorized]);

  const handleEdit = (merchant: Merchant) => {
    setEditingId(merchant.id);
    setEditValues({ name: merchant.name, categoryId: merchant.categoryId || null });
    setShowCategoryPicker(false);
    setCategorySearch('');
  };

  const handleCancel = () => {
    setEditingId(null);
    setShowCategoryPicker(false);
    setCategorySearch('');
  };

  const pickCategory = (categoryId?: number, target: 'edit' | 'create' = 'edit') => {
    if (target === 'edit') {
      setEditValues(prev => ({ ...prev, categoryId: categoryId ?? null }));
      setShowCategoryPicker(false);
    } else {
      setCreateValues(prev => ({ ...prev, categoryId: categoryId ?? null }));
      setShowCreateCategoryPicker(false);
    }
    setCategorySearch('');
  };

  const handleSave = async (merchantId: number) => {
    try {
      const updated = await updateMerchant(token, merchantId, editValues);
      setMerchants(merchants.map(m => m.id === merchantId ? updated : m));
      setEditingId(null);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Error updating merchant');
    }
  };

  const handleCreate = async () => {
    const name = createValues.name.trim();
    if (!name) {
      setError('El nombre del merchant es obligatorio');
      return;
    }

    try {
      setCreating(true);
      setError(null);
      const created = await createMerchant(token, createValues);
      setMerchants(prev => [created, ...prev]);
      setCreateValues({ name: '', categoryId: null });
      setShowCreateCategoryPicker(false);
    } catch (err: any) {
      if (err?.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      } else {
        setError(err?.response?.data?.message || 'Error creating merchant');
      }
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (merchant: Merchant) => {
    const confirmed = window.confirm(`Se va a borrar el merchant \"${merchant.name}\". Las transacciones asociadas se quedaran sin merchant. Continuar?`);
    if (!confirmed) return;

    try {
      setDeletingId(merchant.id);
      setError(null);
      await deleteMerchant(token, merchant.id);
      setMerchants(prev => prev.filter(m => m.id !== merchant.id));
      if (editingId === merchant.id) {
        handleCancel();
      }
    } catch (err: any) {
      if (err?.response?.status === 401) {
        onUnauthorized?.('Sesión expirada');
      } else {
        setError(err?.response?.data?.message || 'Error deleting merchant');
      }
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) return <div className="merchant-manager merchant-manager--loading">Cargando merchants...</div>;

  return (
    <div className="merchant-manager">
      <div className="merchant-manager__hero">
        <div>
          <p className="merchant-manager__eyebrow">Merchants</p>
          <h2>Gestiona nombres y categoría por defecto</h2>
          <p className="merchant-manager__subtitle">
            Asigna categorías inteligentes para acelerar el etiquetado y mantener limpio el histórico.
          </p>
        </div>
        <div className="merchant-manager__stats">
          <span className="merchant-stat__value">{merchants.length}</span>
          <span className="merchant-stat__label">Merchants activos</span>
        </div>
      </div>

      {error && <div className="error-message">{error}</div>}

      <div className="merchant-manager__layout">
        <section className="merchant-manager__composer">
          <div className="merchant-card merchant-card--composer">
            <div className="merchant-card__header">
              <div>
                <h3>Nuevo merchant</h3>
                <p>Crea un merchant y deja preparada su categoría por defecto.</p>
              </div>
            </div>

            <div className="merchant-form-grid">
              <label className="merchant-field">
                <span>Nombre</span>
                <input
                  type="text"
                  value={createValues.name}
                  onChange={e => setCreateValues(prev => ({ ...prev, name: e.target.value }))}
                  placeholder="Ej. Amazon, Mercadona, Renfe"
                />
              </label>

              <div className="merchant-field">
                <span>Categoría por defecto</span>
                <button
                  type="button"
                  className={'category-trigger merchant-category-trigger' + (showCreateCategoryPicker ? ' active' : '')}
                  onClick={() => {
                    setShowCreateCategoryPicker(v => !v);
                    setShowCategoryPicker(false);
                    setCategorySearch('');
                  }}
                >
                  {selectedCreateCategory ? (
                    <span className="category-trigger-value">
                      <span>{getCategoryVisual(selectedCreateCategory.code || selectedCreateCategory.name).emoji}</span>
                      <span>{selectedCreateCategory.name}</span>
                    </span>
                  ) : (
                    <span className="category-trigger-placeholder">Seleccionar categoría...</span>
                  )}
                  <span className="category-trigger-arrow">{showCreateCategoryPicker ? '▲' : '▼'}</span>
                </button>
              </div>
            </div>

            <div className="merchant-composer__actions">
              <button type="button" className="merchant-btn merchant-btn--primary" onClick={handleCreate} disabled={creating}>
                {creating ? 'Creando...' : 'Añadir merchant'}
              </button>
              <button
                type="button"
                className="merchant-btn merchant-btn--ghost"
                onClick={() => {
                  setCreateValues({ name: '', categoryId: null });
                  setShowCreateCategoryPicker(false);
                  setCategorySearch('');
                }}
                disabled={creating}
              >
                Limpiar
              </button>
            </div>
          </div>
        </section>

        <section className="merchant-manager__list">
          {sortedMerchants.map((merchant) => (
            <article key={merchant.id} className={'merchant-card' + (editingId === merchant.id ? ' merchant-card--editing' : '')}>
              <div className="merchant-card__header">
                <div className="merchant-card__identity">
                  <span className="merchant-card__avatar">{merchant.name.charAt(0).toUpperCase()}</span>
                  <div>
                    {editingId === merchant.id ? (
                      <input
                        className="merchant-inline-input"
                        type="text"
                        value={editValues.name}
                        onChange={(e) => setEditValues({ ...editValues, name: e.target.value })}
                      />
                    ) : (
                      <h3>{merchant.name}</h3>
                    )}
                    <p>ID #{merchant.id}</p>
                  </div>
                </div>

                <div className="merchant-card__actions">
                  {editingId === merchant.id ? (
                    <>
                      <button className="merchant-btn merchant-btn--primary" onClick={() => handleSave(merchant.id)}>Guardar</button>
                      <button className="merchant-btn merchant-btn--ghost" onClick={handleCancel}>Cancelar</button>
                    </>
                  ) : (
                    <>
                      <button className="merchant-btn merchant-btn--ghost" onClick={() => handleEdit(merchant)}>Editar</button>
                      <button className="merchant-btn merchant-btn--danger" onClick={() => handleDelete(merchant)} disabled={deletingId === merchant.id}>
                        {deletingId === merchant.id ? 'Borrando...' : 'Borrar'}
                      </button>
                    </>
                  )}
                </div>
              </div>

              <div className="merchant-card__meta">
                <span className="merchant-meta__label">Categoría por defecto</span>
                {editingId === merchant.id ? (
                  <button
                    type="button"
                    className={'category-trigger merchant-category-trigger' + (showCategoryPicker ? ' active' : '')}
                    onClick={() => {
                      setShowCategoryPicker(v => !v);
                      setShowCreateCategoryPicker(false);
                      setCategorySearch('');
                    }}
                  >
                    {selectedCategory ? (
                      <span className="category-trigger-value">
                        <span>{getCategoryVisual(selectedCategory.code || selectedCategory.name).emoji}</span>
                        <span>{selectedCategory.name}</span>
                      </span>
                    ) : (
                      <span className="category-trigger-placeholder">Seleccionar categoría...</span>
                    )}
                    <span className="category-trigger-arrow">{showCategoryPicker ? '▲' : '▼'}</span>
                  </button>
                ) : merchant.categoryName ? (
                  <span className="merchant-category-pill">
                    <span>{getCategoryVisual(merchant.categoryName).emoji}</span>
                    <span>{merchant.categoryName}</span>
                  </span>
                ) : (
                  <span className="merchant-category-pill merchant-category-pill--empty">Sin categoría</span>
                )}
              </div>
            </article>
          ))}
        </section>
      </div>

      {editingId !== null && showCategoryPicker && (
        <div className="category-picker-overlay" onClick={() => setShowCategoryPicker(false)}>
          <div className="category-picker-dialog" onClick={e => e.stopPropagation()}>
            <div className="category-picker-header">
              <span>Seleccionar categoría</span>
              <button type="button" className="category-picker-close" onClick={() => setShowCategoryPicker(false)}>
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
                <button type="button" className="category-search-clear" onClick={() => setCategorySearch('')}>
                  ×
                </button>
              )}
            </div>

            <div className="category-picker-list">
              <div
                className={'category-none-item' + (!editValues.categoryId ? ' selected' : '')}
                onClick={() => pickCategory(undefined, 'edit')}
              >
                Sin categoría
              </div>

              <div className="category-picker-grid">
                {filteredParents.map(parent => {
                  const parentVisual = getCategoryVisual(parent.code || parent.name);
                  const children = getVisibleChildren(parent.id);
                  const totalChildren = (childCategoriesMap[parent.id] || []).length;

                  return (
                    <div key={parent.id} className="category-group">
                      <div
                        className={'category-parent-row' + (editValues.categoryId === parent.id ? ' selected' : '')}
                        onClick={() => pickCategory(parent.id, 'edit')}
                      >
                        <span className="cat-emoji">{parentVisual.emoji}</span>
                        <span className="cat-name">{parent.name}</span>
                        {totalChildren > 0 && <span className="cat-count">{totalChildren}</span>}
                      </div>

                      {children.map(child => {
                        const childVisual = getCategoryVisual(child.code || child.name);
                        return (
                          <div
                            key={child.id}
                            className={'category-child-row' + (editValues.categoryId === child.id ? ' selected' : '')}
                            onClick={() => pickCategory(child.id, 'edit')}
                          >
                            <span className="cat-emoji">{childVisual.emoji}</span>
                            <span className="cat-name">{child.name}</span>
                          </div>
                        );
                      })}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}

      {showCreateCategoryPicker && (
        <div className="category-picker-overlay" onClick={() => setShowCreateCategoryPicker(false)}>
          <div className="category-picker-dialog" onClick={e => e.stopPropagation()}>
            <div className="category-picker-header">
              <span>Seleccionar categoría</span>
              <button type="button" className="category-picker-close" onClick={() => setShowCreateCategoryPicker(false)}>
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
                <button type="button" className="category-search-clear" onClick={() => setCategorySearch('')}>
                  ×
                </button>
              )}
            </div>

            <div className="category-picker-list">
              <div
                className={'category-none-item' + (!createValues.categoryId ? ' selected' : '')}
                onClick={() => pickCategory(undefined, 'create')}
              >
                Sin categoría
              </div>

              <div className="category-picker-grid">
                {filteredParents.map(parent => {
                  const parentVisual = getCategoryVisual(parent.code || parent.name);
                  const children = getVisibleChildren(parent.id);
                  const totalChildren = (childCategoriesMap[parent.id] || []).length;

                  return (
                    <div key={parent.id} className="category-group">
                      <div
                        className={'category-parent-row' + (createValues.categoryId === parent.id ? ' selected' : '')}
                        onClick={() => pickCategory(parent.id, 'create')}
                      >
                        <span className="cat-emoji">{parentVisual.emoji}</span>
                        <span className="cat-name">{parent.name}</span>
                        {totalChildren > 0 && <span className="cat-count">{totalChildren}</span>}
                      </div>

                      {children.map(child => {
                        const childVisual = getCategoryVisual(child.code || child.name);
                        return (
                          <div
                            key={child.id}
                            className={'category-child-row' + (createValues.categoryId === child.id ? ' selected' : '')}
                            onClick={() => pickCategory(child.id, 'create')}
                          >
                            <span className="cat-emoji">{childVisual.emoji}</span>
                            <span className="cat-name">{child.name}</span>
                          </div>
                        );
                      })}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
