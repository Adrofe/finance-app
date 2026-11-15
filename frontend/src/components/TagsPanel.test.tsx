import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { CatalogService } from '../services/catalogService';
import { fetchTransactions } from '../services/transactionsService';
import { FINANCE_EVENTS } from '../events/financeEvents';
import { getCategoryVisual } from '../constants/visualConfig';
import { TagsPanel } from './TagsPanel';

vi.mock('../services/transactionsService', () => ({ fetchTransactions: vi.fn() }));
vi.mock('recharts', () => {
  const Container = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  return { BarChart: Container, Bar: Container, XAxis: Container, YAxis: Container, CartesianGrid: Container, Tooltip: Container, ResponsiveContainer: Container, PieChart: Container, Pie: Container, Cell: Container, Legend: Container };
});

beforeEach(() => {
  vi.spyOn(CatalogService, 'fetchTags').mockResolvedValue([{ id: 1, name: 'Vacaciones' }, { id: 2, name: 'Transferencias' }]);
  vi.spyOn(CatalogService, 'fetchAccounts').mockResolvedValue([
    { id: 1, name: 'Cuenta principal', currency: 'EUR' },
    { id: 2, name: 'Cuenta ahorro', currency: 'EUR' },
  ]);
  vi.spyOn(CatalogService, 'fetchCategories').mockResolvedValue([
    { id: 10, name: 'Viajes', code: 'TRAVEL' },
    { id: 11, name: 'Comida', code: 'TRAVEL.FOOD', parentId: 10 },
    { id: 20, name: 'Hogar', code: 'HOME' },
    { id: 21, name: 'Comida', code: 'HOME.FOOD', parentId: 20 },
  ]);
  vi.mocked(fetchTransactions).mockResolvedValue([
    { id: 100, tagIds: [1], categoryId: 11, sourceAccountId: 1, bookingDate: '2025-01-10', description: 'Cena', merchantName: 'Restauración', amount: -20, currency: 'EUR' },
    { id: 101, tagIds: [1], categoryId: 21, sourceAccountId: 2, bookingDate: '2025-01-11', description: 'Compra', amount: -5, currency: 'USD' },
    { id: 102, tagIds: [1], categoryId: 11, sourceAccountId: 1, bookingDate: '2025-01-12', description: 'Reembolso', amount: 10, currency: 'EUR' },
    { id: 103, tagIds: [1], sourceAccountId: 1, bookingDate: '2025-01-13', description: 'Sin asignar', amount: -7, currency: 'EUR' },
    { id: 104, tagIds: [1, 2], categoryId: 11, sourceAccountId: 1, destinationAccountId: 2, linkedTransactionId: 105, bookingDate: '2025-01-14', description: 'Transferencia', amount: -10, currency: 'EUR' },
    { id: 106, tagIds: [3], categoryId: 11, sourceAccountId: 1, bookingDate: '2025-01-15', description: 'Otra etiqueta', amount: -1, currency: 'EUR' },
  ]);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.resetAllMocks();
});

async function openTag(name = 'Vacaciones') {
  render(<TagsPanel token="test" />);
  fireEvent.click(await screen.findByText(`# ${name}`));
  const section = screen.getByRole('heading', { name: 'Transacciones' }).closest('section')!;
  return within(section);
}

function selectCategory(section: ReturnType<typeof within>, name: string) {
  fireEvent.click(section.getByRole('button', { name: /Filtrar transacciones por categoría:/ }));
  const menu = within(section.getByRole('group', { name: 'Categorías de transacciones' }));
  fireEvent.click(menu.getByRole('button', { name }));
}

describe('tag transactions', () => {
  it('orders year-prefixed tags newest first and names alphabetically within each year', async () => {
    vi.mocked(CatalogService.fetchTags).mockResolvedValue([
      { id: 1, name: '2025_Zaragoza' }, { id: 2, name: '2026_Zaragoza' },
      { id: 3, name: '2026_Ávila' }, { id: 4, name: 'Familia' }, { id: 5, name: 'Amigos' },
      { id: 6, name: '2024_Lisboa' },
    ]);
    const { container } = render(<TagsPanel token="test" />);
    await screen.findByText('# 2026_Ávila');
    expect([...container.querySelectorAll('.tp-tag-list .tp-tag-pill')].map(element => element.textContent?.trim())).toEqual([
      '# 2026_Ávila', '# 2026_Zaragoza', '# 2025_Zaragoza', '# 2024_Lisboa', '# Amigos', '# Familia',
    ]);
  });

  it('lists all tagged transactions with account, currency and newest first', async () => {
    const section = await openTag();
    const rows = section.getAllByRole('row').slice(1);
    expect(rows).toHaveLength(5);
    expect(rows[0].textContent).toContain('Transferencia');
    expect(rows[0].textContent).toContain('Cuenta principal');
    expect(rows[0].textContent).toContain('Cuenta ahorro');
    expect(section.getByText('Restauración')).toBeTruthy();
    expect(section.getByText(/5,00\s*US\$/)).toBeTruthy();
    expect(section.queryByText('Otra etiqueta')).toBeNull();
  });

  it('filters by parent and child category identity and allows clearing the filter', async () => {
    const section = await openTag();
    fireEvent.click(screen.getByRole('button', { name: 'Viajes' }));
    expect(section.getByText('Cena')).toBeTruthy();
    expect(section.getByText('Reembolso')).toBeTruthy();
    expect(section.getByText('Transferencia')).toBeTruthy();
    expect(section.queryByText('Compra')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Hijos' }));
    const categories = screen.getByRole('heading', { name: 'Desglose por categoría' }).closest('.tp-breakdown-card')!;
    const categoryButtons = within(categories as HTMLElement);
    expect(categoryButtons.getByRole('button', { name: 'Hogar › Comida' })).toBeTruthy();
    fireEvent.click(categoryButtons.getByRole('button', { name: 'Viajes › Comida' }));
    expect(section.getByText('Cena')).toBeTruthy();
    expect(section.queryByText('Compra')).toBeNull();
    fireEvent.click(section.getByRole('button', { name: 'Todas las categorías' }));
    expect(section.getByText('Compra')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Padre' }));
    fireEvent.click(screen.getByRole('button', { name: 'Sin categoría' }));
    expect(section.getByText('Sin asignar')).toBeTruthy();
    expect(section.queryByText('Cena')).toBeNull();
  });

  it('searches transaction details and navigates to the selected transaction', async () => {
    const listener = vi.fn();
    window.addEventListener(FINANCE_EVENTS.NAVIGATE_TO_TRANSACTION, listener);
    try {
      const section = await openTag();
      fireEvent.change(section.getByRole('searchbox', { name: 'Buscar transacciones de la tag' }), { target: { value: 'restauracion' } });
      expect(section.getByText('Cena')).toBeTruthy();
      expect(section.queryByText('Compra')).toBeNull();
      fireEvent.click(section.getByRole('button', { name: 'Ver transacción Cena' }));
      expect(listener).toHaveBeenCalledOnce();
      expect((listener.mock.calls[0][0] as CustomEvent).detail).toEqual({ transactionId: 100 });
      fireEvent.change(section.getByRole('searchbox', { name: 'Buscar transacciones de la tag' }), { target: { value: 'no existe' } });
      expect(section.getByText('No hay transacciones para estos filtros.')).toBeTruthy();
    } finally {
      window.removeEventListener(FINANCE_EVENTS.NAVIGATE_TO_TRANSACTION, listener);
    }
  });

  it('shows transfer-only tags and clears filters when changing tags', async () => {
    const section = await openTag();
    fireEvent.click(screen.getByRole('button', { name: 'Viajes' }));
    fireEvent.change(section.getByRole('searchbox', { name: 'Buscar transacciones de la tag' }), { target: { value: 'Cena' } });
    fireEvent.click(screen.getByText('# Transferencias'));
    expect(screen.getByText('No hay ingresos ni gastos para el análisis.')).toBeTruthy();
    expect(section.getByText('Transferencia')).toBeTruthy();
    expect((section.getByRole('searchbox', { name: 'Buscar transacciones de la tag' }) as HTMLInputElement).value).toBe('');
    expect(section.queryByRole('button', { name: 'Todas las categorías' })).toBeNull();
    selectCategory(section, 'Viajes');
    expect(section.getByText('Transferencia')).toBeTruthy();
    expect(section.getByRole('button', { name: 'Todas las categorías' })).toBeTruthy();
  });

  it('allows selecting a category that contains only income', async () => {
    vi.mocked(CatalogService.fetchCategories).mockResolvedValue([{ id: 30, name: 'Nómina', code: 'INC' }]);
    vi.mocked(fetchTransactions).mockResolvedValue([{ id: 108, tagIds: [1], categoryId: 30, sourceAccountId: 1, bookingDate: '2025-01-01', description: 'Ingreso', amount: 100, currency: 'EUR' }]);
    const section = await openTag();
    selectCategory(section, 'Nómina');
    expect(section.getByText('Ingreso')).toBeTruthy();
    expect(section.getByText('Categoría: Nómina')).toBeTruthy();
  });

  it('shows matching category colors and icons and supports keyboard navigation', async () => {
    const section = await openTag();
    const trigger = section.getByRole('button', { name: /Filtrar transacciones por categoría:/ });
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    const menu = section.getByRole('group', { name: 'Categorías de transacciones' });
    expect(document.activeElement).toBe(within(menu).getByRole('button', { name: 'Todas las categorías' }));
    const travel = within(menu).getByRole('button', { name: 'Viajes' });
    const icon = travel.querySelector<HTMLElement>('.tp-category-icon')!;
    const breakdownSection = screen.getByRole('heading', { name: 'Desglose por categoría' }).closest('.tp-breakdown-card')!;
    const breakdown = within(breakdownSection as HTMLElement).getByRole('button', { name: 'Viajes' });
    expect(icon.textContent).toBe(getCategoryVisual('TRAVEL').emoji);
    expect(icon.style.color).toBe((breakdown as HTMLElement).style.color);
    fireEvent.click(travel);
    expect(trigger.getAttribute('aria-label')).toContain('Viajes');
    expect(trigger.querySelector('.tp-category-icon')?.textContent).toBe(getCategoryVisual('TRAVEL').emoji);
    expect(document.activeElement).toBe(trigger);
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    fireEvent.keyDown(document.activeElement!, { key: 'Escape' });
    expect((trigger.closest('details') as HTMLDetailsElement).open).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });

  it('orders category options alphabetically and closes the menu on outside click', async () => {
    const section = await openTag();
    const trigger = section.getByRole('button', { name: /Filtrar transacciones por categoría:/ });
    fireEvent.click(trigger);
    const menu = section.getByRole('group', { name: 'Categorías de transacciones' });
    expect(within(menu).getAllByRole('button').map(button => button.textContent?.trim().replace(/^[^\p{L}]+/u, ''))).toEqual([
      'Todas las categorías', 'Hogar', 'Sin categoría', 'Viajes',
    ]);
    fireEvent.mouseDown(document.body);
    expect(section.queryByRole('group', { name: 'Categorías de transacciones' })).toBeNull();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('paginates transactions and returns to the first page when searching', async () => {
    vi.mocked(fetchTransactions).mockResolvedValue(Array.from({ length: 25 }, (_, index) => ({
      id: index + 1, tagIds: [1], sourceAccountId: 1, bookingDate: '2025-01-01', description: `Movimiento ${index + 1}`, amount: -1, currency: 'EUR',
    })));
    const section = await openTag();
    expect(section.getAllByRole('row')).toHaveLength(21);
    fireEvent.click(section.getByRole('button', { name: 'Página siguiente' }));
    expect(section.getAllByRole('row')).toHaveLength(6);
    expect(section.getByText('21–25 de 25')).toBeTruthy();
    fireEvent.change(section.getByRole('searchbox', { name: 'Buscar transacciones de la tag' }), { target: { value: 'Movimiento' } });
    expect(section.getAllByRole('row')).toHaveLength(21);
    expect(section.getByText('1–20 de 25')).toBeTruthy();
  });
});