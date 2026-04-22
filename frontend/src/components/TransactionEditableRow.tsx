import { useState, useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import type { Transaction } from '../types/banking';
import type {
  TransactionCategory,
  Account,
  TransactionStatus,
  TransactionType,
  Tag,
} from '../services/catalogService';
import { getCategoryVisual, getInstitutionLogo } from '../constants/visualConfig';
import { updateTransaction } from '../services/transactionsService';
import './TransactionEditableRow.css';

// ─── Types ────────────────────────────────────────────────────────────────────

type EditField =
  | 'date' | 'description' | 'amount'
  | 'category' | 'status' | 'type' | 'sourceAccount' | 'tags'
  | null;

type PickerPos = { top: number; left: number; minWidth: number };

const STATUS_STYLES: Record<string, { background: string; color: string }> = {
  BOOKED:    { background: '#dcfce7', color: '#166534' },
  PENDING:   { background: '#fef9c3', color: '#854d0e' },
  CANCELLED: { background: '#f3f4f6', color: '#6b7280' },
  REJECTED:  { background: '#fee2e2', color: '#991b1b' },
};

// ─── Sub-components ───────────────────────────────────────────────────────────

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

// ─── Props ────────────────────────────────────────────────────────────────────

export interface TransactionEditableRowProps {
  tx: Transaction;
  accessToken: string;
  categories: TransactionCategory[];
  statuses: TransactionStatus[];
  types: TransactionType[];
  accounts: Account[];
  tags: Tag[];
  categoryCodeMap: Record<number, string>;
  categoryMap: Record<number, string>;
  statusMap: Record<number, string>;
  typeMap: Record<number, string>;
  accountDetailMap: Record<number, Account>;
  tagMap: Record<number, string>;
  selectMode: boolean;
  isSelected: boolean;
  onToggleSelect?: (id: number) => void;
  onDelete: () => void;
  onRefresh: () => void;
  onExitEdit: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function TransactionEditableRow({
  tx, accessToken,
  categories, statuses, types, accounts, tags,
  categoryCodeMap, categoryMap, statusMap, typeMap, accountDetailMap, tagMap,
  selectMode, isSelected, onToggleSelect, onDelete, onRefresh, onExitEdit,
}: TransactionEditableRowProps) {

  const [draft, setDraft]           = useState<Transaction>({ ...tx });
  const [activeField, setActiveField] = useState<EditField>(null);
  const [textDraft, setTextDraft]   = useState('');
  const [draftTagIds, setDraftTagIds] = useState<number[]>(tx.tagIds ?? []);
  const [catSearch, setCatSearch]   = useState('');
  const [saving, setSaving]         = useState(false);
  const [error, setError]           = useState<string | null>(null);
  const [pickerPos, setPickerPos]   = useState<PickerPos | null>(null);

  const pickerRef = useRef<HTMLDivElement>(null);
  const textInputRef = useRef<HTMLInputElement>(null);

  // Sync draft when parent refreshes tx
  useEffect(() => {
    setDraft({ ...tx });
    setDraftTagIds(tx.tagIds ?? []);
  }, [tx]);

  // Close floating pickers on outside click
  useEffect(() => {
    if (!activeField || activeField === 'date' || activeField === 'description' || activeField === 'amount') return;
    const handle = (e: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(e.target as Node)) {
        setActiveField(null);
      }
    };
    document.addEventListener('mousedown', handle);
    return () => document.removeEventListener('mousedown', handle);
  }, [activeField]);

  // ── Helpers ────────────────────────────────────────────────────────────────

  const fmt = (n: number) => n.toLocaleString('es-ES', { style: 'currency', currency: 'EUR' });

  const formatDateDisplay = (dateStr?: string) => {
    if (!dateStr) return { day: '—', rest: '' };
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return { day: '—', rest: '' };
    return {
      day: d.getDate().toString().padStart(2, '0'),
      rest: d.toLocaleDateString('es-ES', { month: 'short', year: 'numeric' }),
    };
  };

  // ── Save ───────────────────────────────────────────────────────────────────

  const save = useCallback(async (patch: Partial<Transaction>) => {
    if (!tx.id) return;
    setSaving(true);
    setError(null);
    try {
      const updated = { ...draft, ...patch };
      await updateTransaction(accessToken, tx.id, updated);
      setDraft(updated);
      onRefresh();
    } catch (err: unknown) {
      const e = err as { response?: { data?: { message?: string } }; message?: string };
      setError(e?.response?.data?.message || e?.message || 'Error guardando cambio');
    } finally {
      setSaving(false);
    }
  }, [accessToken, draft, tx.id, onRefresh]);

  // ── Text fields (date, description, amount) ────────────────────────────────

  const openText = (e: React.MouseEvent, field: 'date' | 'description' | 'amount') => {
    e.stopPropagation();
    setTextDraft(
      field === 'date'        ? (draft.bookingDate ?? '').slice(0, 10) :
      field === 'description' ? (draft.description ?? '') :
      String(draft.amount ?? '')
    );
    setActiveField(field);
    // Focus after render
    setTimeout(() => textInputRef.current?.focus(), 0);
  };

  const confirmText = async () => {
    const prev = activeField;
    setActiveField(null);
    if (prev === 'date')        await save({ bookingDate: textDraft ? `${textDraft}T00:00:00` : undefined });
    else if (prev === 'description') await save({ description: textDraft.trim() || undefined });
    else if (prev === 'amount') {
      const n = parseFloat(textDraft);
      if (!isNaN(n)) await save({ amount: n });
    }
  };

  const cancelText = () => setActiveField(null);

  const handleTextKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter')  confirmText();
    if (e.key === 'Escape') cancelText();
  };

  // ── Picker fields ──────────────────────────────────────────────────────────

  const openPicker = (e: React.MouseEvent, field: EditField) => {
    e.stopPropagation();
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setPickerPos({ top: rect.bottom + 4, left: rect.left, minWidth: Math.max(rect.width, 220) });
    setActiveField(field);
    setCatSearch('');
    if (field === 'tags') setDraftTagIds(draft.tagIds ?? []);
  };

  const closePicker = () => setActiveField(null);

  // ── Category helpers ───────────────────────────────────────────────────────

  const parentCats = categories.filter(c => !c.parentId);
  const childrenByParent: Record<number, TransactionCategory[]> = {};
  categories.forEach(c => {
    if (c.parentId) {
      if (!childrenByParent[c.parentId]) childrenByParent[c.parentId] = [];
      childrenByParent[c.parentId].push(c);
    }
  });

  const filteredParents = parentCats.filter(p => {
    if (!catSearch.trim()) return true;
    const q = catSearch.toLowerCase();
    return p.name.toLowerCase().includes(q) ||
      (childrenByParent[p.id] ?? []).some(c => c.name.toLowerCase().includes(q));
  });

  const visibleChildren = (parentId: number) => {
    const children = childrenByParent[parentId] ?? [];
    if (!catSearch.trim()) return children;
    const q = catSearch.toLowerCase();
    return children.filter(c => c.name.toLowerCase().includes(q));
  };

  const selectCategory = async (id: number | null) => {
    closePicker();
    await save({ categoryId: id ?? undefined });
  };

  // ── Display values ─────────────────────────────────────────────────────────

  const catCode   = draft.categoryId != null ? (categoryCodeMap[draft.categoryId] ?? '') : '';
  const catName   = draft.categoryId != null ? (categoryMap[draft.categoryId] ?? '') : '';
  const catVisual = getCategoryVisual(catCode || 'OTH');

  const statusCode  = draft.statusId != null ? statusMap[draft.statusId] : '';
  const statusStyle = statusCode ? (STATUS_STYLES[statusCode] ?? STATUS_STYLES.BOOKED) : null;

  const typeName  = draft.typeId != null ? typeMap[draft.typeId] : '';
  const typeClass = typeName ? `tt-type-badge tt-type-${typeName.toUpperCase()}` : 'tt-type-badge';

  const srcAcc = draft.sourceAccountId      != null ? accountDetailMap[draft.sourceAccountId]      : undefined;
  const dstAcc = draft.destinationAccountId != null ? accountDetailMap[draft.destinationAccountId] : undefined;
  const isNeg  = (draft.amount ?? 0) < 0;
  const { day, rest } = formatDateDisplay(draft.bookingDate);

  // ── Picker panels ──────────────────────────────────────────────────────────

  const renderCategoryPicker = () => (
    <div
      ref={pickerRef}
      className="tie-picker tie-cat-picker"
      style={{ top: pickerPos!.top, left: pickerPos!.left }}
    >
      <div className="tie-picker-search">
        <input
          type="text"
          className="tie-search-input"
          placeholder="Buscar categoría…"
          value={catSearch}
          onChange={e => setCatSearch(e.target.value)}
          autoFocus
        />
      </div>
      <div className="tie-picker-body">
        <button className="tie-cat-clear" onClick={() => selectCategory(null)}>
          <span className="tie-cat-emoji">✕</span>
          <span>Sin categoría</span>
        </button>
        {filteredParents.map(parent => {
          const parentVisual   = getCategoryVisual(parent.code || parent.name);
          const children       = visibleChildren(parent.id);
          const hasChildren    = (childrenByParent[parent.id] ?? []).length > 0;
          return (
            <div key={parent.id}>
              {!hasChildren ? (
                <button
                  className={`tie-cat-item tie-cat-parent${draft.categoryId === parent.id ? ' tie-cat-item--active' : ''}`}
                  onClick={() => selectCategory(parent.id)}
                >
                  <span className="tie-cat-emoji">{parentVisual.emoji}</span>
                  <span>{parent.name}</span>
                </button>
              ) : (
                <>
                  <div className="tie-cat-group-label">
                    <span className="tie-cat-emoji">{parentVisual.emoji}</span>
                    <span>{parent.name}</span>
                  </div>
                  {children.length === 0 && catSearch.trim() && null}
                  {children.map(child => {
                    const childVisual = getCategoryVisual(child.code || child.name);
                    return (
                      <button
                        key={child.id}
                        className={`tie-cat-item tie-cat-child${draft.categoryId === child.id ? ' tie-cat-item--active' : ''}`}
                        onClick={() => selectCategory(child.id)}
                      >
                        <span className="tie-cat-emoji">{childVisual.emoji}</span>
                        <span>{child.name}</span>
                      </button>
                    );
                  })}
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );

  const renderStatusPicker = () => (
    <div
      ref={pickerRef}
      className="tie-picker tie-status-picker"
      style={{ top: pickerPos!.top, left: pickerPos!.left, minWidth: pickerPos!.minWidth }}
    >
      {statuses.map(s => {
        const st = STATUS_STYLES[s.code] ?? STATUS_STYLES.BOOKED;
        return (
          <button
            key={s.id}
            className={`tie-status-item${draft.statusId === s.id ? ' tie-item--active' : ''}`}
            onClick={async () => { closePicker(); await save({ statusId: s.id }); }}
          >
            <span className="tt-status-badge" style={st}>{s.code}</span>
          </button>
        );
      })}
    </div>
  );

  const renderTypePicker = () => (
    <div
      ref={pickerRef}
      className="tie-picker tie-type-picker"
      style={{ top: pickerPos!.top, left: pickerPos!.left, minWidth: pickerPos!.minWidth }}
    >
      {types.map(t => {
        const cls = `tt-type-badge tt-type-${t.name.toUpperCase()}`;
        return (
          <button
            key={t.id}
            className={`tie-type-item${draft.typeId === t.id ? ' tie-item--active' : ''}`}
            onClick={async () => { closePicker(); await save({ typeId: t.id }); }}
          >
            <span className={cls}>{t.name}</span>
          </button>
        );
      })}
    </div>
  );

  const renderAccountPicker = () => (
    <div
      ref={pickerRef}
      className="tie-picker tie-acc-picker"
      style={{ top: pickerPos!.top, left: pickerPos!.left, minWidth: Math.max(pickerPos!.minWidth, 240) }}
    >
      {accounts.map(acc => (
        <button
          key={acc.id}
          className={`tie-acc-item${draft.sourceAccountId === acc.id ? ' tie-item--active' : ''}`}
          onClick={async () => { closePicker(); await save({ sourceAccountId: acc.id }); }}
        >
          <BankLogo name={acc.institutionName} />
          <div className="tie-acc-info">
            <span className="tie-acc-name">{acc.name}</span>
            {acc.institutionName && <span className="tie-acc-bank">{acc.institutionName}</span>}
          </div>
        </button>
      ))}
    </div>
  );

  const renderTagPicker = () => (
    <div
      ref={pickerRef}
      className="tie-picker tie-tag-picker"
      style={{ top: pickerPos!.top, left: pickerPos!.left, minWidth: Math.max(pickerPos!.minWidth, 240) }}
    >
      <div className="tie-tag-list">
        {tags.length === 0 && <span className="tie-empty-tags">No hay tags disponibles</span>}
        {tags.map(tag => {
          const isOn = draftTagIds.includes(tag.id);
          return (
            <button
              key={tag.id}
              className={`tie-tag-item${isOn ? ' tie-tag-item--on' : ''}`}
              onClick={() => setDraftTagIds(prev =>
                isOn ? prev.filter(id => id !== tag.id) : [...prev, tag.id]
              )}
            >
              #{tag.name}
            </button>
          );
        })}
      </div>
      <div className="tie-tag-actions">
        <button
          className="tie-picker-save"
          onClick={async () => { closePicker(); await save({ tagIds: draftTagIds }); }}
          disabled={saving}
        >
          Guardar
        </button>
        <button className="tie-picker-cancel" onClick={closePicker}>Cancelar</button>
      </div>
    </div>
  );

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <>
      <tr className={`tt-row tt-row--editing${isSelected ? ' selected' : ''}`}>

        {/* Checkbox */}
        {selectMode && (
          <td className="tt-td tt-td-check">
            {tx.id != null && (
              <input
                type="checkbox"
                className="tt-checkbox"
                checked={isSelected}
                onChange={() => onToggleSelect?.(tx.id!)}
              />
            )}
          </td>
        )}

        {/* Date */}
        <td
          className={`tt-td tt-td-date tie-editable${activeField === 'date' ? ' tie-cell--active' : ''}`}
          onClick={activeField !== 'date' ? e => openText(e, 'date') : undefined}
        >
          {activeField === 'date' ? (
            <div className="tie-text-edit tie-text-edit--date">
              <input
                ref={textInputRef}
                type="date"
                className="tie-input tie-input--date"
                value={textDraft}
                onChange={e => setTextDraft(e.target.value)}
                onKeyDown={handleTextKey}
                autoFocus
              />
              <button className="tie-btn-confirm" onClick={confirmText} disabled={saving}>✓</button>
              <button className="tie-btn-cancel" onClick={cancelText}>✕</button>
            </div>
          ) : (
            <>
              <span className="tt-date-day">{day}</span>
              <span className="tt-date-rest">{rest}</span>
            </>
          )}
        </td>

        {/* Description */}
        <td
          className={`tt-td tt-td-desc tie-editable${activeField === 'description' ? ' tie-cell--active' : ''}`}
          onClick={activeField !== 'description' ? e => openText(e, 'description') : undefined}
        >
          {activeField === 'description' ? (
            <div className="tie-text-edit tie-text-edit--wide">
              <input
                ref={textInputRef}
                type="text"
                className="tie-input tie-input--wide"
                value={textDraft}
                placeholder="Descripción…"
                onChange={e => setTextDraft(e.target.value)}
                onKeyDown={handleTextKey}
                autoFocus
              />
              <button className="tie-btn-confirm" onClick={confirmText} disabled={saving}>✓</button>
              <button className="tie-btn-cancel" onClick={cancelText}>✕</button>
            </div>
          ) : (
            <div className="tt-desc-wrap">
              {draft.description
                ? <span className="tt-desc-primary">{draft.description}</span>
                : <span className="tie-placeholder">Añadir descripción…</span>}
              {draft.merchantName && (
                <span className="tt-desc-merchant">{draft.merchantName}</span>
              )}
            </div>
          )}
        </td>

        {/* Amount */}
        <td
          className={`tt-td tt-td-amount tie-editable${activeField === 'amount' ? ' tie-cell--active' : ''}`}
          onClick={activeField !== 'amount' ? e => openText(e, 'amount') : undefined}
        >
          {activeField === 'amount' ? (
            <div className="tie-text-edit tie-text-edit--amount">
              <input
                ref={textInputRef}
                type="number"
                className="tie-input tie-input--amount"
                value={textDraft}
                step="0.01"
                onChange={e => setTextDraft(e.target.value)}
                onKeyDown={handleTextKey}
                onFocus={e => e.target.select()}
                autoFocus
              />
              <button className="tie-btn-confirm" onClick={confirmText} disabled={saving}>✓</button>
              <button className="tie-btn-cancel" onClick={cancelText}>✕</button>
            </div>
          ) : (
            <span className={`tt-amount ${isNeg ? 'tt-amount--neg' : 'tt-amount--pos'}`}>
              {typeof draft.amount === 'number' ? fmt(draft.amount) : '—'}
            </span>
          )}
        </td>

        {/* Category */}
        <td
          className={`tt-td tt-td-cat tie-editable${activeField === 'category' ? ' tie-cell--active' : ''}`}
          onClick={e => openPicker(e, 'category')}
        >
          {catCode ? (
            <span
              className="tt-category-badge"
              style={{ background: `${catVisual.color}22`, color: catVisual.color, borderColor: `${catVisual.color}55` }}
            >
              <span className="tt-cat-emoji">{catVisual.emoji}</span>
              <span className="tt-cat-name">{catName}</span>
            </span>
          ) : <span className="tie-placeholder">+ Categoría</span>}
        </td>

        {/* Source account */}
        <td
          className={`tt-td tt-td-acc tie-editable${activeField === 'sourceAccount' ? ' tie-cell--active' : ''}`}
          onClick={e => openPicker(e, 'sourceAccount')}
        >
          {srcAcc ? (
            <div className="tt-account-cell">
              <BankLogo name={srcAcc.institutionName} />
              <div className="tt-account-info">
                <span className="tt-account-name">{srcAcc.name}</span>
                {srcAcc.institutionName && <span className="tt-account-bank">{srcAcc.institutionName}</span>}
              </div>
            </div>
          ) : <span className="tie-placeholder">+ Cuenta</span>}
        </td>

        {/* Destination account (read-only — changing it affects the linked transfer pair) */}
        <td
          className="tt-td tt-td-acc tie-readonly"
          title="La cuenta destino es de solo lectura en transferencias enlazadas"
        >
          {dstAcc ? (
            <div className="tt-account-cell">
              <BankLogo name={dstAcc.institutionName} />
              <div className="tt-account-info">
                <span className="tt-account-name">{dstAcc.name}</span>
                {dstAcc.institutionName && <span className="tt-account-bank">{dstAcc.institutionName}</span>}
              </div>
            </div>
          ) : <span className="tt-empty-cell">—</span>}
        </td>

        {/* Status */}
        <td
          className={`tt-td tt-td-status tie-editable${activeField === 'status' ? ' tie-cell--active' : ''}`}
          onClick={e => openPicker(e, 'status')}
        >
          {statusCode && statusStyle
            ? <span className="tt-status-badge" style={statusStyle}>{statusCode}</span>
            : <span className="tie-placeholder">+ Estado</span>}
        </td>

        {/* Type */}
        <td
          className={`tt-td tt-td-type tie-editable${activeField === 'type' ? ' tie-cell--active' : ''}`}
          onClick={e => openPicker(e, 'type')}
        >
          <div className="tt-type-wrap">
            {typeName
              ? <span className={typeClass}>{typeName}</span>
              : <span className="tie-placeholder">+ Tipo</span>}
            {draft.linkedTransactionId != null && (
              <span className="tt-linked-badge" title="Transferencia enlazada">⛓️</span>
            )}
          </div>
        </td>

        {/* Tags */}
        <td
          className={`tt-td tt-td-tags tie-editable${activeField === 'tags' ? ' tie-cell--active' : ''}`}
          onClick={e => openPicker(e, 'tags')}
        >
          {draftTagIds.length > 0 ? (
            <div className="tt-tags-wrap">
              {draftTagIds.map(id => (
                <span key={id} className="tt-tag">{tagMap[id] ?? `#${id}`}</span>
              ))}
            </div>
          ) : <span className="tie-placeholder">+ Tags</span>}
        </td>

        {/* Actions */}
        <td className="tt-td tt-td-actions">
          <div className="tie-action-btns">
            {saving
              ? <span className="tie-saving-dot" title="Guardando…" />
              : (
                <button className="tie-exit-btn" title="Salir modo edición" onClick={onExitEdit}>
                  ✓
                </button>
              )
            }
            {tx.id != null && (
              <button className="tt-btn-delete" title="Eliminar transacción" onClick={onDelete}>
                🗑️
              </button>
            )}
          </div>
        </td>

      </tr>

      {/* Error row */}
      {error && (
        <tr className="tie-error-tr">
          <td colSpan={selectMode ? 11 : 10} style={{ padding: '0 14px 6px' }}>
            <div className="tie-error-bar">
              ⚠ {error}
              <button onClick={() => setError(null)}>✕</button>
            </div>
          </td>
        </tr>
      )}

      {/* Portaled floating pickers */}
      {activeField === 'category'      && pickerPos && createPortal(renderCategoryPicker(), document.body)}
      {activeField === 'status'        && pickerPos && createPortal(renderStatusPicker(),   document.body)}
      {activeField === 'type'          && pickerPos && createPortal(renderTypePicker(),     document.body)}
      {activeField === 'sourceAccount' && pickerPos && createPortal(renderAccountPicker(),  document.body)}
      {activeField === 'tags'          && pickerPos && createPortal(renderTagPicker(),      document.body)}
    </>
  );
}
