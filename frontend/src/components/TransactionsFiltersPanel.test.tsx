import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { EMPTY_FILTERS, TransactionsFiltersPanel, applyFilters, type TransactionFilters } from './TransactionsFiltersPanel';
import { getCategoryVisual } from '../constants/visualConfig';
import { TransactionsTable } from './TransactionsTable';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import { searchTransactions } from '../services/transactionsService';
import { getPresetRange } from '../utils/dashboardPresets';

vi.mock('../hooks/useTransactionCatalogs', () => ({ useTransactionCatalogs: vi.fn() }));
vi.mock('../services/transactionsService', async importOriginal => ({
  ...await importOriginal<typeof import('../services/transactionsService')>(), searchTransactions: vi.fn(),
}));

afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe('transaction filter controls', () => {
  it('uses dashboard calendar ranges, including year boundaries', () => {
    const today = new Date(2026, 0, 15, 12);
    expect(getPresetRange('this-month', today)).toEqual({ start: '2026-01-01', end: '2026-01-15' });
    expect(getPresetRange('last-month', today)).toEqual({ start: '2025-12-01', end: '2025-12-31' });
    expect(getPresetRange('3-months', today)).toEqual({ start: '2025-11-01', end: '2026-01-15' });
    expect(getPresetRange('6-months', today)).toEqual({ start: '2025-08-01', end: '2026-01-15' });
    expect(getPresetRange('this-year', today)).toEqual({ start: '2026-01-01', end: '2026-01-15' });
    expect(getPresetRange('last-year', today)).toEqual({ start: '2025-01-01', end: '2025-12-31' });
  });

  it('applies date presets, custom dates and clears the selected period', async () => {
    vi.mocked(useTransactionCatalogs).mockReturnValue({
      statusMap: {}, typeMap: {}, categoryMap: {}, categoryCodeMap: {}, accountDetailMap: {}, tagMap: {}, categories: [], merchants: [], tags: [], accounts: [], types: [], statuses: [],
    } as unknown as ReturnType<typeof useTransactionCatalogs>);
    vi.mocked(searchTransactions).mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    render(<TransactionsTable items={[]} accessToken="test" />);
    fireEvent.click(screen.getByRole('button', { name: /Filtros/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Año pasado' }));
    const range = getPresetRange('last-year');
    await waitFor(() => expect(searchTransactions).toHaveBeenLastCalledWith('test', expect.objectContaining({ startDate: range.start, endDate: range.end })));
    expect(screen.getByRole('button', { name: 'Año pasado' }).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: 'Personalizado' }));
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2025-02-01' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2025-02-28' } });
    await waitFor(() => expect(searchTransactions).toHaveBeenLastCalledWith('test', expect.objectContaining({ startDate: '2025-02-01', endDate: '2025-02-28' })));
    fireEvent.click(screen.getByRole('button', { name: /Limpiar filtros/ }));
    expect(screen.queryByLabelText('Desde')).toBeNull();
    await waitFor(() => expect(searchTransactions).toHaveBeenLastCalledWith('test', expect.objectContaining({ startDate: undefined, endDate: undefined })));
  });

  it('selects and searches multiple tags and clears them without closing the picker', () => {
    function Panel() {
      const [filters, setFilters] = useState<TransactionFilters>(EMPTY_FILTERS);
      return <TransactionsFiltersPanel filters={filters} onChange={setFilters} categories={[]} accounts={[]} statuses={[]} types={[]} tags={[{ id: 1, name: 'Viajes' }, { id: 2, name: 'Trabajo' }, { id: 3, name: 'Casa' }]} />;
    }
    render(<Panel />);
    expect(screen.queryByRole('checkbox', { name: '#Viajes' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Etiquetas' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '#Viajes' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '#Trabajo' }));
    expect(screen.getByRole('button', { name: 'Etiquetas' }).textContent).toContain('#Viajes, #Trabajo');
    fireEvent.change(screen.getByRole('searchbox', { name: 'Buscar etiquetas' }), { target: { value: 'casa' } });
    expect(screen.getByRole('checkbox', { name: '#Casa' })).toBeTruthy();
    expect(screen.queryByRole('checkbox', { name: '#Viajes' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Limpiar selección' }));
    expect(screen.getByRole('button', { name: 'Etiquetas' }).textContent).toContain('Todas las etiquetas');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.getByRole('button', { name: 'Etiquetas' }).getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Etiquetas' }));
  });

  it('retains parent transactions when a selected child is excluded', () => {
    const categories = [{ id: 1, name: 'Food', code: 'FOOD' }, { id: 2, name: 'Supermarket', code: 'FOOD.SUP', parentId: 1 }, { id: 3, name: 'Restaurant', code: 'FOOD.REST', parentId: 1 }];
    const transactions = [{ id: 1, categoryId: 1 }, { id: 2, categoryId: 2 }, { id: 3, categoryId: 3 }];
    const onChange = vi.fn();
    render(<TransactionsFiltersPanel filters={{ ...EMPTY_FILTERS, parentCategoryIds: [1] }} onChange={onChange} categories={categories} accounts={[]} statuses={[]} types={[]} tags={[]} />);
    fireEvent.click(screen.getByRole('checkbox', { name: 'Supermarket' }));
    const selected = onChange.mock.calls[0][0] as TransactionFilters;
    expect(selected.parentCategoryIds).toEqual([]);
    expect(selected.categoryIds).toEqual([1, 3]);
    expect(applyFilters(transactions, selected, categories).map(transaction => transaction.id)).toEqual([1, 3]);
  });

  it('sends the selected parent, remaining child and multiple tags to the server', async () => {
    vi.mocked(useTransactionCatalogs).mockReturnValue({
      statusMap: {}, typeMap: {}, categoryMap: {}, categoryCodeMap: {}, accountDetailMap: {}, tagMap: {}, merchants: [], accounts: [], types: [], statuses: [],
      categories: [{ id: 1, name: 'Food', code: 'FOOD' }, { id: 2, name: 'Supermarket', code: 'FOOD.SUP', parentId: 1 }, { id: 3, name: 'Restaurant', code: 'FOOD.REST', parentId: 1 }],
      tags: [{ id: 10, name: 'Travel' }, { id: 11, name: 'Work' }],
    } as unknown as ReturnType<typeof useTransactionCatalogs>);
    vi.mocked(searchTransactions).mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    render(<TransactionsTable items={[]} accessToken="test" />);
    fireEvent.click(screen.getByRole('button', { name: /Filtros/ }));
    fireEvent.click(screen.getByRole('checkbox', { name: /Food/ }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Supermarket' }));
    fireEvent.click(screen.getByRole('button', { name: 'Etiquetas' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '#Travel' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '#Work' }));
    await waitFor(() => expect(searchTransactions).toHaveBeenLastCalledWith('test', expect.objectContaining({ categoryIds: [1, 3], tagIds: [10, 11] })));
  });
  it('does not replace filtered results with a late response from an earlier search', async () => {
    vi.mocked(useTransactionCatalogs).mockReturnValue({
      statusMap: {}, typeMap: {}, categoryMap: {}, categoryCodeMap: {}, accountDetailMap: {}, tagMap: {}, categories: [], merchants: [], tags: [], accounts: [], types: [],
      statuses: [{ id: 1, code: 'BOOKED', description: '' }],
    } as unknown as ReturnType<typeof useTransactionCatalogs>);
    let finishFirst!: (result: Awaited<ReturnType<typeof searchTransactions>>) => void;
    const first = new Promise<Awaited<ReturnType<typeof searchTransactions>>>(resolve => { finishFirst = resolve; });
    const result = (description: string) => ({ content: [{ id: 1, description, bookingDate: '2026-01-01', amount: -5 }], page: 0, size: 25, totalElements: 1, totalPages: 1 });
    vi.mocked(searchTransactions).mockReturnValueOnce(first).mockResolvedValue(result('Filtered result'));
    render(<TransactionsTable items={[]} accessToken="test" />);
    fireEvent.click(screen.getByRole('button', { name: /Filtros/ }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'BOOKED' }));
    expect(await screen.findByText('Filtered result')).toBeTruthy();
    await act(async () => { finishFirst(result('Old result')); await first; });
    expect(screen.getByText('Filtered result')).toBeTruthy();
    expect(screen.queryByText('Old result')).toBeNull();
  });

  it('sends multiple selected filters and keeps the filter button active when closed', async () => {
    vi.mocked(useTransactionCatalogs).mockReturnValue({
      statusMap: {}, typeMap: {}, categoryMap: {}, categoryCodeMap: {}, accountDetailMap: {}, tagMap: {}, categories: [], merchants: [], tags: [],
      accounts: [{ id: 1, name: 'Account one' }, { id: 2, name: 'Account two' }],
      statuses: [{ id: 1, code: 'BOOKED', description: '' }, { id: 2, code: 'PENDING', description: '' }],
      types: [{ id: 1, name: 'EXPENSE', description: '' }, { id: 2, name: 'TRANSFER', description: '' }],
    } as unknown as ReturnType<typeof useTransactionCatalogs>);
    vi.mocked(searchTransactions).mockResolvedValue({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
    render(<TransactionsTable items={[]} accessToken="test" />);
    const filterButton = screen.getByRole('button', { name: /Filtros/ });
    expect(filterButton.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(filterButton);
    for (const name of ['Account one', 'Account two', 'BOOKED', 'PENDING', 'EXPENSE', 'TRANSFER']) {
      fireEvent.click(screen.getByRole('checkbox', { name }));
    }
    await waitFor(() => expect(searchTransactions).toHaveBeenLastCalledWith('test', expect.objectContaining({ sourceAccountIds: [1, 2], statusIds: [1, 2], typeIds: [1, 2] })));
    fireEvent.click(filterButton);
    expect(filterButton.getAttribute('aria-expanded')).toBe('false');
    expect(filterButton.classList.contains('has-filters')).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: /Limpiar filtros/ }));
    expect(filterButton.classList.contains('has-filters')).toBe(false);
    await waitFor(() => expect(searchTransactions).toHaveBeenLastCalledWith('test', expect.objectContaining({ sourceAccountIds: undefined, statusIds: undefined, typeIds: undefined })));
  });

  it('updates selected and partially selected category styles as checkboxes change', () => {
    function Panel() {
      const [filters, setFilters] = useState<TransactionFilters>(EMPTY_FILTERS);
      return <TransactionsFiltersPanel filters={filters} onChange={setFilters} accounts={[]} statuses={[]} types={[]} tags={[]} categories={[
        { id: 1, name: 'Food', code: 'FOOD' },
        { id: 2, name: 'Supermarket', code: 'FOOD.SUP', parentId: 1 },
        { id: 3, name: 'Restaurant', code: 'FOOD.REST', parentId: 1 },
      ]} />;
    }
    render(<Panel />);
    const parent = screen.getByRole('checkbox', { name: /Food/ }) as HTMLInputElement;
    expect(screen.queryByRole('checkbox', { name: 'Supermarket' })).toBeNull();
    fireEvent.click(parent);
    const child = screen.getByRole('checkbox', { name: 'Supermarket' }) as HTMLInputElement;
    expect(child.checked).toBe(true);
    fireEvent.click(child);
    expect(child.checked).toBe(false);
    expect(child.closest('label')?.classList.contains('ttf-cat--selected')).toBe(false);
    expect(parent.indeterminate).toBe(true);
    expect(parent.closest('.ttf-cat-parent-row')?.classList.contains('ttf-cat--partial')).toBe(true);
    fireEvent.click(parent);
    expect(parent.checked).toBe(true);
    expect(parent.closest('.ttf-cat-parent-row')?.classList.contains('ttf-cat--selected')).toBe(true);
    expect((screen.getByRole('checkbox', { name: 'Restaurant' }) as HTMLInputElement).checked).toBe(true);
    fireEvent.click(parent);
    expect(parent.checked).toBe(false);
    expect(child.checked).toBe(false);
    expect(child.closest('label')?.classList.contains('ttf-cat--selected')).toBe(false);
  });

  it('allows clearing a standalone category supplied as a parent filter', () => {
    const onChange = vi.fn();
    render(<TransactionsFiltersPanel filters={{ ...EMPTY_FILTERS, parentCategoryIds: [1] }} onChange={onChange} accounts={[]} statuses={[]} types={[]} tags={[]} categories={[{ id: 1, name: 'Food', code: 'FOOD' }]} />);
    const category = screen.getByRole('checkbox', { name: 'Food' }) as HTMLInputElement;
    expect(category.checked).toBe(true);
    expect(category.closest('label')?.classList.contains('ttf-cat--selected')).toBe(true);
    fireEvent.click(category);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ parentCategoryIds: [], categoryIds: [] }));
  });

  it('indicates selected status, type, account and tag controls', () => {
    render(<TransactionsFiltersPanel filters={{ ...EMPTY_FILTERS, sourceAccountIds: [1], statusIds: [2], typeIds: [3, 5], tagIds: [4] }} onChange={vi.fn()} categories={[]}
      accounts={[{ id: 1, name: 'Main account' }]} statuses={[{ id: 2, code: 'PENDING', description: '' }]}
      types={[{ id: 3, name: 'TRANSFER', description: '' }, { id: 5, name: 'OTHER', description: '' }]} tags={[{ id: 4, name: 'Travel' }]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Etiquetas' }));
    for (const [name, selectedClass] of [['Main account', 'ttf-chip--on'], ['PENDING', 'ttf-status-chip--on'], ['TRANSFER', 'ttf-type-chip--on'], ['OTHER', 'ttf-type-chip--on'], ['#Travel', 'selected']]) {
      const control = screen.getByRole('checkbox', { name }) as HTMLInputElement;
      expect(control.checked).toBe(true);
      expect(control.closest('label')?.classList.contains(selectedClass)).toBe(true);
    }
    expect(screen.getByRole('checkbox', { name: 'PENDING' }).closest('label')?.style.color).toBe('rgb(133, 77, 14)');
    expect(screen.getByRole('checkbox', { name: 'TRANSFER' }).closest('label')?.style.color).toBe('rgb(30, 64, 175)');
  });

  it('exposes the selected dashboard date preset', () => {
    render(<TransactionsFiltersPanel filters={{ ...EMPTY_FILTERS, datePreset: 'this-month' }} onChange={vi.fn()} accounts={[]} statuses={[]} types={[]} tags={[]} categories={[]} />);
    expect(screen.getByRole('button', { name: 'Este mes' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Mes anterior' }).getAttribute('aria-pressed')).toBe('false');
  });
  it('shows the corresponding visuals for parent and child categories', () => {
    render(<TransactionsFiltersPanel filters={EMPTY_FILTERS} onChange={vi.fn()} accounts={[]} statuses={[]} types={[]} tags={[]} categories={[
      { id: 1, name: 'Food', code: 'FOOD' },
      { id: 2, name: 'Supermarket', code: 'FOOD.SUP', parentId: 1 },
    ]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Desplegar Food' }));
    for (const [name, code] of [['Food', 'FOOD'], ['Supermarket', 'FOOD.SUP']]) {
      const icon = screen.getByRole('checkbox', { name: new RegExp(name) }).closest('label')!.querySelector<HTMLElement>('.ttf-cat-emoji')!;
      const expected = document.createElement('span');
      expected.style.color = getCategoryVisual(code).color;
      expect(icon.textContent).toBe(getCategoryVisual(code).emoji);
      expect(icon.style.color).toBe(expected.style.color);
    }
  });
  it('keeps account, status, type and tag filters keyboard-accessible', () => {
    const onChange = vi.fn();
    render(<TransactionsFiltersPanel
      filters={EMPTY_FILTERS}
      onChange={onChange}
      categories={[]}
      accounts={[{ id: 1, name: 'Main account' }]}
      statuses={[{ id: 2, code: 'BOOKED', description: '' }]}
      types={[{ id: 3, name: 'TRANSFER', description: '' }]}
      tags={[{ id: 4, name: 'Travel' }]}
    />);

    const account = screen.getByRole('checkbox', { name: 'Main account' });
    const status = screen.getByRole('checkbox', { name: 'BOOKED' });
    const type = screen.getByRole('checkbox', { name: 'TRANSFER' });
    fireEvent.click(screen.getByRole('button', { name: 'Etiquetas' }));
    const tag = screen.getByRole('checkbox', { name: '#Travel' });

    for (const control of [account, status, type, tag]) {
      control.focus();
      expect(document.activeElement).toBe(control);
      fireEvent.click(control);
    }
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ sourceAccountIds: [1] }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ statusIds: [2] }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ typeIds: [3] }));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ tagIds: [4] }));
  });
});