import { useEffect, useRef, useState } from 'react';
import type { TransactionCategory, Account, TransactionStatus, TransactionType, Tag } from '../services/catalogService';
import { getCategoryVisual, getInstitutionLogo } from '../constants/visualConfig';
import type { DashboardPreset } from '../types/dashboard';
import { getPresetRange, PRESETS, PRESET_LABELS } from '../utils/dashboardPresets';
import './Dashboard.css';
import './TransactionsFiltersPanel.css';

// ─── Visual style maps (same as TransactionsTable) ─────────────────────────

const FILTER_STATUS_STYLES: Record<string, { bg: string; color: string }> = {
  BOOKED:    { bg: '#dcfce7', color: '#166534' },
  PENDING:   { bg: '#fef9c3', color: '#854d0e' },
  CANCELLED: { bg: '#f3f4f6', color: '#6b7280' },
  REJECTED:  { bg: '#fee2e2', color: '#991b1b' },
};

const FILTER_TYPE_STYLES: Record<string, { bg: string; color: string }> = {
  EXPENSE:    { bg: '#fff1f2', color: '#9f1239' },
  INCOME:     { bg: '#f0fdf4', color: '#166534' },
  TRANSFER:   { bg: '#eff6ff', color: '#1e40af' },
  ADJUSTMENT: { bg: '#faf5ff', color: '#6b21a8' },
};

// ─── Filter state type ──────────────────────────────────────────────────────

export type DateMode = 'range' | 'month' | 'year';

export type TransactionFilters = {
  datePreset?: DashboardPreset | null;
  dateMode: DateMode;
  dateFrom: string;       // YYYY-MM-DD (range mode)
  dateTo: string;         // YYYY-MM-DD (range mode)
  dateMonth: string;      // YYYY-MM  (month mode)
  dateYear: string;       // YYYY     (year mode)
  descriptionText: string;
  amountMin: string;
  amountMax: string;
  categoryIds: number[];        // specific categories
  parentCategoryIds: number[];  // parent categories → includes all children
  sourceAccountIds: number[];
  statusIds: number[];
  typeIds: number[];
  tagIds: number[];
};

export const EMPTY_FILTERS: TransactionFilters = {
  datePreset: null,
  dateMode: 'range',
  dateFrom: '',
  dateTo: '',
  dateMonth: '',
  dateYear: '',
  descriptionText: '',
  amountMin: '',
  amountMax: '',
  categoryIds: [],
  parentCategoryIds: [],
  sourceAccountIds: [],
  statusIds: [],
  typeIds: [],
  tagIds: [],
};

export function countActiveFilters(f: TransactionFilters): number {
  const dateActive =
    (f.dateMode === 'range'  && (f.dateFrom || f.dateTo)) ||
    (f.dateMode === 'month'  && f.dateMonth) ||
    (f.dateMode === 'year'   && f.dateYear);
  return [
    dateActive,
    f.descriptionText.trim(),
    f.amountMin || f.amountMax,
    f.categoryIds.length > 0 || f.parentCategoryIds.length > 0,
    f.sourceAccountIds.length > 0,
    f.statusIds.length > 0,
    f.typeIds.length > 0,
    f.tagIds.length > 0,
  ].filter(Boolean).length;
}

// ─── Filter application ─────────────────────────────────────────────────────

import type { Transaction } from '../types/banking';

export function applyFilters(
  items: Transaction[],
  f: TransactionFilters,
  categories: TransactionCategory[],
): Transaction[] {
  if (countActiveFilters(f) === 0) return items;

  // Build child → parent map
  const childToParent: Record<number, number> = {};
  categories.forEach(c => { if (c.parentId != null) childToParent[c.id] = c.parentId; });

  return items.filter(tx => {
    // Date filter
    const bd = tx.bookingDate ?? '';
    if (f.dateMode === 'range') {
      if (f.dateFrom && bd && bd < f.dateFrom) return false;
      if (f.dateTo   && bd && bd > f.dateTo)   return false;
    } else if (f.dateMode === 'month' && f.dateMonth) {
      // YYYY-MM: bookingDate starts with YYYY-MM
      if (!bd.startsWith(f.dateMonth)) return false;
    } else if (f.dateMode === 'year' && f.dateYear) {
      if (!bd.startsWith(f.dateYear)) return false;
    }

    // Description / merchant text search
    if (f.descriptionText.trim()) {
      const q = f.descriptionText.toLowerCase();
      if (!tx.description?.toLowerCase().includes(q) && !tx.merchantName?.toLowerCase().includes(q)) return false;
    }

    // Amount range
    if (f.amountMin !== '' && tx.amount != null && tx.amount < Number(f.amountMin)) return false;
    if (f.amountMax !== '' && tx.amount != null && tx.amount > Number(f.amountMax)) return false;

    // Category
    if (f.categoryIds.length > 0 || f.parentCategoryIds.length > 0) {
      const catId = tx.categoryId;
      if (catId == null) return false;
      const parentId = childToParent[catId];
      const matched =
        f.categoryIds.includes(catId) ||
        (parentId != null && f.parentCategoryIds.includes(parentId)) ||
        f.parentCategoryIds.includes(catId);  // category IS a parent and is directly selected
      if (!matched) return false;
    }

    // Source account
    if (f.sourceAccountIds.length > 0 &&
        (tx.sourceAccountId == null || !f.sourceAccountIds.includes(tx.sourceAccountId))) return false;

    // Status
    if (f.statusIds.length > 0 &&
        (tx.statusId == null || !f.statusIds.includes(tx.statusId))) return false;

    // Type
    if (f.typeIds.length > 0 &&
        (tx.typeId == null || !f.typeIds.includes(tx.typeId))) return false;

    // Tags – OR logic (has at least one of the selected tags)
    if (f.tagIds.length > 0) {
      const txTags = tx.tagIds ?? [];
      if (!txTags.some(id => f.tagIds.includes(id))) return false;
    }

    return true;
  });
}

// ─── Helper ─────────────────────────────────────────────────────────────────

function toggle<T>(arr: T[], val: T): T[] {
  return arr.includes(val) ? arr.filter(x => x !== val) : [...arr, val];
}

// ─── Component ──────────────────────────────────────────────────────────────

type Props = {
  filters: TransactionFilters;
  onChange: (f: TransactionFilters) => void;
  categories: TransactionCategory[];
  accounts: Account[];
  statuses: TransactionStatus[];
  types: TransactionType[];
  tags: Tag[];
};

function DateFilter({ filters, onChange }: { filters: TransactionFilters; onChange: (f: TransactionFilters) => void }) {
  return (
    <div className="ttf-date-filter">
      <div className="db-presets" role="group" aria-label="Periodo de transacciones">
        {PRESETS.map(preset => {
          const selected = filters.datePreset === preset;
          return <button key={preset} type="button" className={`db-preset-btn${selected ? ' active' : ''}`} aria-pressed={selected}
            onClick={() => {
              const range = selected ? { start: '', end: '' } : getPresetRange(preset);
              onChange({ ...filters, datePreset: selected ? null : preset, dateMode: 'range', dateFrom: range.start, dateTo: range.end, dateMonth: '', dateYear: '' });
            }}>{PRESET_LABELS[preset]}</button>;
        })}
      </div>
      {filters.datePreset === 'custom' && (
        <div className="db-custom-dates">
          <label>Desde <input type="date" value={filters.dateFrom} onChange={event => onChange({ ...filters, dateFrom: event.target.value })} /></label>
          <label>Hasta <input type="date" value={filters.dateTo} onChange={event => onChange({ ...filters, dateTo: event.target.value })} /></label>
        </div>
      )}
    </div>
  );
}

export function TransactionsFiltersPanel({ filters, onChange, categories, accounts, statuses, types, tags }: Props) {
  const [tagsOpen, setTagsOpen] = useState(false);
  const [tagSearch, setTagSearch] = useState('');
  const tagsPickerRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!tagsOpen) return;
    const closeOutside = (event: PointerEvent) => {
      if (!tagsPickerRef.current?.contains(event.target as Node)) setTagsOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setTagsOpen(false);
        tagsPickerRef.current?.querySelector<HTMLButtonElement>('.ttf-tags-trigger')?.focus();
      }
    };
    document.addEventListener('pointerdown', closeOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [tagsOpen]);
  const [expandedParents, setExpandedParents] = useState<Set<number>>(() => new Set([
    ...filters.parentCategoryIds,
    ...categories.filter(category => category.parentId != null && filters.categoryIds.includes(category.id)).map(category => category.parentId!),
  ]));
  // Build category tree
  const parentCategories = categories.filter(c => c.parentId == null);
  const childrenByParent: Record<number, TransactionCategory[]> = {};
  categories.forEach(c => {
    if (c.parentId != null) {
      if (!childrenByParent[c.parentId]) childrenByParent[c.parentId] = [];
      childrenByParent[c.parentId].push(c);
    }
  });

  const parentsWithChildren = parentCategories.filter(c => (childrenByParent[c.id]?.length ?? 0) > 0);
  const standaloneCategories = parentCategories.filter(c => !(childrenByParent[c.id]?.length > 0));

  const toggleParentCat = (parentId: number) => {
    const childIds = childrenByParent[parentId]?.map(c => c.id) ?? [];
    if (filters.parentCategoryIds.includes(parentId)) {
      // Deselect parent and its children
      onChange({
        ...filters,
        parentCategoryIds: filters.parentCategoryIds.filter(id => id !== parentId),
        categoryIds: filters.categoryIds.filter(id => id !== parentId && !childIds.includes(id)),
      });
    } else {
      setExpandedParents(current => new Set(current).add(parentId));
      // Select parent; remove individual child selections (parent covers them all)
      onChange({
        ...filters,
        parentCategoryIds: [...filters.parentCategoryIds, parentId],
        categoryIds: filters.categoryIds.filter(id => id !== parentId && !childIds.includes(id)),
      });
    }
  };

  const toggleChildCat = (childId: number, parentId: number) => {
    if (filters.parentCategoryIds.includes(parentId)) {
      // Parent is selected → deselect parent, select all siblings except this one
      const siblings = (childrenByParent[parentId] ?? [])
        .filter(c => c.id !== childId)
        .map(c => c.id);
      onChange({
        ...filters,
        parentCategoryIds: filters.parentCategoryIds.filter(id => id !== parentId),
        categoryIds: [...new Set([
          ...filters.categoryIds.filter(id => !(childrenByParent[parentId]?.map(c => c.id) ?? []).includes(id)),
          parentId,
          ...siblings,
        ])],
      });
    } else {
      onChange({ ...filters, categoryIds: toggle(filters.categoryIds, childId) });
    }
  };

  return (
    <div id="transaction-filters" className="ttf-panel">

      {/* ── Row 1: Date · Description · Amount ── */}
      <div className="ttf-row">

        <div className="ttf-group ttf-group--dates">
          <span className="ttf-label">Fecha</span>
          <DateFilter filters={filters} onChange={onChange} />
        </div>

        <div className="ttf-group ttf-group--wide">
          <span className="ttf-label">Descripción</span>
          <input
            type="text"
            className="ttf-input ttf-input--wide"
            value={filters.descriptionText}
            onChange={e => onChange({ ...filters, descriptionText: e.target.value })}
            placeholder="Buscar en descripción o comercio…"
          />
        </div>

        <div className="ttf-group">
          <span className="ttf-label">Importe</span>
          <div className="ttf-range">
            <input
              type="number"
              className="ttf-input"
              value={filters.amountMin}
              onChange={e => onChange({ ...filters, amountMin: e.target.value })}
              placeholder="Mín"
              step="0.01"
            />
            <span className="ttf-range-sep">–</span>
            <input
              type="number"
              className="ttf-input"
              value={filters.amountMax}
              onChange={e => onChange({ ...filters, amountMax: e.target.value })}
              placeholder="Máx"
              step="0.01"
            />
          </div>
        </div>

      </div>

      {/* ── Row 2: Category · Account · Status · Type ── */}
      <div className="ttf-row ttf-row--multi">

        {/* Category tree */}
        <div className="ttf-group ttf-group--scroll">
          <span className="ttf-label">Categoría</span>
          <div className="ttf-cat-tree">

            {parentsWithChildren.map(parent => {
              const children = childrenByParent[parent.id] ?? [];
              const isParentSel = filters.parentCategoryIds.includes(parent.id);
              const selectedChildCount = children.filter(c => filters.categoryIds.includes(c.id)).length;
              const isIndeterminate = !isParentSel && (selectedChildCount > 0 || filters.categoryIds.includes(parent.id));
              const expanded = expandedParents.has(parent.id);
              const parentVisual = getCategoryVisual(parent.code || parent.name);

              return (
                <div key={parent.id} className="ttf-cat-group">
                  <div className={`ttf-cat-parent-row${isParentSel ? ' ttf-cat--selected' : isIndeterminate ? ' ttf-cat--partial' : ''}`}>
                  <label className="ttf-cat-parent">
                    <input
                      type="checkbox"
                      className="ttf-checkbox"
                      checked={isParentSel}
                      ref={el => { if (el) el.indeterminate = isIndeterminate; }}
                      onChange={() => toggleParentCat(parent.id)}
                    />
                    <span className="ttf-cat-emoji" style={{ color: parentVisual.color, background: `${parentVisual.color}22` }} aria-hidden="true">{parentVisual.emoji}</span>
                    <span className="ttf-cat-parent-name">{parent.name}</span>
                    <span className="ttf-cat-count">({children.length})</span>
                  </label>
                  <button type="button" className="ttf-cat-expand" aria-label={`${expanded ? 'Contraer' : 'Desplegar'} ${parent.name}`} aria-expanded={expanded}
                    onClick={() => setExpandedParents(current => {
                      const next = new Set(current);
                      if (next.has(parent.id)) next.delete(parent.id); else next.add(parent.id);
                      return next;
                    })}>{expanded ? '▾' : '▸'}</button>
                  </div>
                  {expanded && <div className="ttf-cat-children">
                    {children.map(child => {
                      const childVisual = getCategoryVisual(child.code || child.name);
                      return (
                        <label key={child.id} className={`ttf-cat-child${isParentSel || filters.categoryIds.includes(child.id) ? ' ttf-cat--selected' : ''}`}>
                          <input
                            type="checkbox"
                            className="ttf-checkbox"
                            checked={isParentSel || filters.categoryIds.includes(child.id)}
                            onChange={() => toggleChildCat(child.id, parent.id)}
                          />
                          <span className="ttf-cat-emoji ttf-cat-emoji--child" style={{ color: childVisual.color, background: `${childVisual.color}22` }} aria-hidden="true">{childVisual.emoji}</span>
                          <span>{child.name}</span>
                        </label>
                      );
                    })}
                  </div>}
                </div>
              );
            })}

            {standaloneCategories.map(cat => {
              const visual = getCategoryVisual(cat.code || cat.name);
              return (
                <label key={cat.id} className={`ttf-cat-standalone${filters.categoryIds.includes(cat.id) || filters.parentCategoryIds.includes(cat.id) ? ' ttf-cat--selected' : ''}`}>
                  <input
                    type="checkbox"
                    className="ttf-checkbox"
                    checked={filters.categoryIds.includes(cat.id) || filters.parentCategoryIds.includes(cat.id)}
                    onChange={() => onChange({
                      ...filters,
                      categoryIds: filters.parentCategoryIds.includes(cat.id) ? filters.categoryIds.filter(id => id !== cat.id) : toggle(filters.categoryIds, cat.id),
                      parentCategoryIds: filters.parentCategoryIds.filter(id => id !== cat.id),
                    })}
                  />
                  <span className="ttf-cat-emoji" style={{ color: visual.color, background: `${visual.color}22` }} aria-hidden="true">{visual.emoji}</span>
                  <span>{cat.name}</span>
                </label>
              );
            })}

          </div>
        </div>

        {/* Source account */}
        <div className="ttf-group ttf-group--scroll">
          <span className="ttf-label">Cuenta origen</span>
          <div className="ttf-chip-list">
            {accounts.map(acc => {
              const logoSrc = getInstitutionLogo(acc.institutionName || '');
              const isOn = filters.sourceAccountIds.includes(acc.id);
              return (
                <label
                  key={acc.id}
                  className={`ttf-chip ttf-chip--acc ${isOn ? 'ttf-chip--on' : ''}`}
                >
                  <input
                    type="checkbox"
                    className="ttf-visually-hidden"
                    checked={isOn}
                    onChange={() => onChange({ ...filters, sourceAccountIds: toggle(filters.sourceAccountIds, acc.id) })}
                  />
                  {logoSrc.startsWith('/')
                    ? <img src={logoSrc} className="ttf-acc-logo" alt="" onError={e => { (e.target as HTMLImageElement).style.display = 'none'; }} />
                    : <span className="ttf-acc-emoji" aria-hidden="true">{logoSrc}</span>
                  }
                  {acc.name}
                  {isOn && <span className="ttf-selection-check" aria-hidden="true">&#10003;</span>}
                </label>
              );
            })}
          </div>
        </div>

        {/* Status */}
        <div className="ttf-group">
          <span className="ttf-label">Estado</span>
          <div className="ttf-chip-list">
            {statuses.map(s => {
              const isOn = filters.statusIds.includes(s.id);
              const st = FILTER_STATUS_STYLES[s.code.toUpperCase()] ?? { bg: '#e8f1ff', color: '#164b9c' };
              return (
                <label
                  key={s.id}
                  className={`ttf-status-chip${isOn ? ' ttf-status-chip--on' : ''}`}
                  style={isOn ? { background: st.bg, color: st.color, borderColor: st.color + '55' } : {}}
                >
                  <input
                    type="checkbox"
                    className="ttf-visually-hidden"
                    checked={isOn}
                    onChange={() => onChange({ ...filters, statusIds: toggle(filters.statusIds, s.id) })}
                  />
                  <span className="ttf-status-dot" style={{ background: isOn ? st.color : '#9ca3af' }} />
                  {s.code}
                  {isOn && <span className="ttf-selection-check" aria-hidden="true">&#10003;</span>}
                </label>
              );
            })}
          </div>
        </div>

        {/* Type */}
        <div className="ttf-group">
          <span className="ttf-label">Tipo</span>
          <div className="ttf-chip-list">
            {types.map(t => {
              const isOn = filters.typeIds.includes(t.id);
              const ty = FILTER_TYPE_STYLES[t.name.toUpperCase()];
              return (
                <label
                  key={t.id}
                  className={`ttf-type-chip${isOn ? ' ttf-type-chip--on' : ''}`}
                  style={isOn && ty ? { background: ty.bg, color: ty.color, borderColor: `${ty.color}66` } : {}}
                >
                  <input
                    type="checkbox"
                    className="ttf-visually-hidden"
                    checked={isOn}
                    onChange={() => onChange({ ...filters, typeIds: toggle(filters.typeIds, t.id) })}
                  />
                  {t.name}
                  {isOn && <span className="ttf-selection-check" aria-hidden="true">&#10003;</span>}
                </label>
              );
            })}
          </div>
        </div>

      </div>

      {tags.length > 0 && (
        <div className="ttf-row">
          <div className="ttf-group ttf-group--tags">
            <span className="ttf-label">Etiquetas</span>
            <div className="ttf-tags-picker" ref={tagsPickerRef}>
              <button type="button" className={`ttf-tags-trigger${filters.tagIds.length ? ' active' : ''}`} aria-label="Etiquetas" aria-expanded={tagsOpen} aria-controls="transaction-tag-options"
                onClick={() => { setTagsOpen(open => !open); setTagSearch(''); }}>
                <span>{filters.tagIds.length ? tags.filter(tag => filters.tagIds.includes(tag.id)).map(tag => `#${tag.name}`).join(', ') : 'Todas las etiquetas'}</span>
                {filters.tagIds.length > 0 && <span className="ttf-tags-count">{filters.tagIds.length}</span>}
                <span aria-hidden="true">{tagsOpen ? '▴' : '▾'}</span>
              </button>
              {tagsOpen && <div id="transaction-tag-options" className="ttf-tags-dropdown" role="group" aria-label="Seleccionar etiquetas">
                <input type="search" className="ttf-input ttf-tags-search" placeholder="Buscar etiquetas" aria-label="Buscar etiquetas" value={tagSearch} onChange={event => setTagSearch(event.target.value)} />
                <div className="ttf-tags-options">
                  {tags.filter(tag => tag.name.toLocaleLowerCase().includes(tagSearch.trim().toLocaleLowerCase())).map(tag => (
                    <label key={tag.id} className={`ttf-tag-option${filters.tagIds.includes(tag.id) ? ' selected' : ''}`}>
                      <input type="checkbox" className="ttf-checkbox" checked={filters.tagIds.includes(tag.id)} onChange={() => onChange({ ...filters, tagIds: toggle(filters.tagIds, tag.id) })} />
                      <span>#{tag.name}</span>
                    </label>
                  ))}
                  {!tags.some(tag => tag.name.toLocaleLowerCase().includes(tagSearch.trim().toLocaleLowerCase())) && <span className="ttf-tags-empty">Sin resultados</span>}
                </div>
                {filters.tagIds.length > 0 && <button type="button" className="ttf-tags-clear" onClick={() => onChange({ ...filters, tagIds: [] })}>Limpiar selección</button>}
              </div>}
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
