import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { EMPTY_FILTERS, TransactionsFiltersPanel } from './TransactionsFiltersPanel';
import { getCategoryVisual } from '../constants/visualConfig';

afterEach(cleanup);

describe('transaction filter controls', () => {
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