import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { EMPTY_FILTERS, TransactionsFiltersPanel, type TransactionFilters } from './TransactionsFiltersPanel';
import { getCategoryVisual } from '../constants/visualConfig';
import { TransactionsTable } from './TransactionsTable';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import { searchTransactions } from '../services/transactionsService';

vi.mock('../hooks/useTransactionCatalogs', () => ({ useTransactionCatalogs: vi.fn() }));
vi.mock('../services/transactionsService', async importOriginal => ({
  ...await importOriginal<typeof import('../services/transactionsService')>(), searchTransactions: vi.fn(),
}));

afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe('transaction filter controls', () => {
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
    const child = screen.getByRole('checkbox', { name: 'Supermarket' }) as HTMLInputElement;
    fireEvent.click(child);
    expect(child.checked).toBe(true);
    expect(child.closest('label')?.classList.contains('ttf-cat--selected')).toBe(true);
    expect(parent.indeterminate).toBe(true);
    expect(parent.closest('label')?.classList.contains('ttf-cat--partial')).toBe(true);
    fireEvent.click(parent);
    expect(parent.checked).toBe(true);
    expect(parent.closest('label')?.classList.contains('ttf-cat--selected')).toBe(true);
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
    for (const [name, selectedClass] of [['Main account', 'ttf-chip--on'], ['PENDING', 'ttf-status-chip--on'], ['TRANSFER', 'ttf-type-chip--on'], ['OTHER', 'ttf-type-chip--on'], ['#Travel', 'ttf-chip--on']]) {
      const control = screen.getByRole('checkbox', { name }) as HTMLInputElement;
      expect(control.checked).toBe(true);
      expect(control.closest('label')?.classList.contains(selectedClass)).toBe(true);
    }
    expect(screen.getByRole('checkbox', { name: 'PENDING' }).closest('label')?.style.color).toBe('rgb(133, 77, 14)');
    expect(screen.getByRole('checkbox', { name: 'TRANSFER' }).closest('label')?.style.color).toBe('rgb(30, 64, 175)');
  });

  it('exposes the selected date mode and selected month', () => {
    render(<TransactionsFiltersPanel filters={{ ...EMPTY_FILTERS, dateMode: 'month', dateMonth: `${new Date().getFullYear()}-02` }} onChange={vi.fn()} accounts={[]} statuses={[]} types={[]} tags={[]} categories={[]} />);
    expect(screen.getByRole('button', { name: 'Mes' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Intervalo' }).getAttribute('aria-pressed')).toBe('false');
    expect(screen.getByRole('button', { name: 'Feb' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Ene' }).getAttribute('aria-pressed')).toBe('false');
  });
  it('shows the corresponding visuals for parent and child categories', () => {
    render(<TransactionsFiltersPanel filters={EMPTY_FILTERS} onChange={vi.fn()} accounts={[]} statuses={[]} types={[]} tags={[]} categories={[
      { id: 1, name: 'Food', code: 'FOOD' },
      { id: 2, name: 'Supermarket', code: 'FOOD.SUP', parentId: 1 },
    ]} />);
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