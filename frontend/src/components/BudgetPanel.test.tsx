import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { BudgetPanel } from './BudgetPanel';
import { Dashboard } from './Dashboard';
import { fetchBudgetPlans, refreshBudgetSnapshot, setPrimaryBudgetPlan } from '../services/budgetService';
import { fetchDashboardSummary, fetchSpendingByCategory, fetchTimeSeries } from '../services/dashboardService';
import { useTransactionCatalogs } from '../hooks/useTransactionCatalogs';
import type { BudgetPlanDTO, BudgetSnapshotDTO, BudgetSnapshotLineDTO } from '../types/budget';

vi.mock('../services/budgetService', () => ({ fetchBudgetPlans: vi.fn(), refreshBudgetSnapshot: vi.fn(), setPrimaryBudgetPlan: vi.fn(), createBudgetPlan: vi.fn(), deleteBudgetPlan: vi.fn() }));
vi.mock('../services/dashboardService', () => ({ fetchDashboardSummary: vi.fn(), fetchSpendingByCategory: vi.fn(), fetchTimeSeries: vi.fn() }));
vi.mock('../hooks/useTransactionCatalogs', () => ({ useTransactionCatalogs: vi.fn() }));
vi.mock('recharts', () => {
  const Container = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>;
  const Bar = ({ dataKey, onClick }: { dataKey: string; onClick?: (value: { payload: { key: string; name: string } }) => void }) => (
    <button type="button" data-testid={`bar-${dataKey}`} onClick={() => onClick?.({ payload: { key: '10', name: 'Food' } })}>{dataKey}</button>
  );
  return { BarChart: Container, Bar, LineChart: Container, Line: Container, XAxis: Container, YAxis: Container, CartesianGrid: Container, Tooltip: Container, Legend: Container, ResponsiveContainer: Container, PieChart: Container, Pie: Container, Cell: Container };
});

const plan: BudgetPlanDTO = {
  id: 1, name: 'Plan principal', period: 'MONTHLY', currency: 'EUR', active: true, primary: true,
  lines: [
    { id: 1, categoryId: 10, categoryCode: 'FOOD.SHOP', categoryName: 'Food', budgetAmount: 100, lineType: 'EXPENSE' },
    { id: 2, categoryId: 20, categoryCode: 'TRAVEL.AIR', categoryName: 'Travel', budgetAmount: 60, lineType: 'EXPENSE' },
  ],
};
const makeLine = (id: number, categoryId: number, name: string, code: string, budget: number, spent: number): BudgetSnapshotLineDTO => ({
  id, categoryId, categoryName: name, categoryCode: code, budgetAmount: budget,
  spentAmount: spent, variance: budget - spent, compliant: spent <= budget, lineType: 'EXPENSE', transactionCount: 1,
});
const snapshot: BudgetSnapshotDTO = {
  id: 1, budgetPlanId: 1, periodStart: '2025-12-01', periodEnd: '2026-01-31', generatedAt: '2026-01-31T12:00:00',
  totalBudget: 320, totalSpent: 280, variance: 40, compliant: true, totalExpectedIncome: 0, totalIncome: 0, incomeVariance: 0, netBalance: -280,
  lines: [makeLine(1, 10, 'Food', 'FOOD.SHOP', 200, 160), makeLine(2, 20, 'Travel', 'TRAVEL.AIR', 120, 120)],
  months: [
    { monthStart: '2025-12-01', totalBudget: 160, totalSpent: 170, variance: -10, compliant: false, totalExpectedIncome: 0, totalIncome: 0,
      lines: [makeLine(3, 10, 'Food', 'FOOD.SHOP', 100, 120), makeLine(4, 20, 'Travel', 'TRAVEL.AIR', 60, 50)] },
    { monthStart: '2026-01-01', totalBudget: 160, totalSpent: 110, variance: 50, compliant: true, totalExpectedIncome: 0, totalIncome: 0,
      lines: [makeLine(5, 10, 'Food', 'FOOD.SHOP', 100, 40), makeLine(6, 20, 'Travel', 'TRAVEL.AIR', 60, 70)] },
  ],
};

beforeEach(() => {
  vi.mocked(fetchBudgetPlans).mockResolvedValue([plan]);
  vi.mocked(refreshBudgetSnapshot).mockResolvedValue(snapshot);
  vi.mocked(setPrimaryBudgetPlan).mockResolvedValue(plan);
  vi.mocked(fetchDashboardSummary).mockResolvedValue({ totalIncome: 0, totalExpenses: 0, totalInvestments: 0, net: 0, savingsRate: null, transactionCount: 0 });
  vi.mocked(fetchSpendingByCategory).mockResolvedValue([]);
  vi.mocked(fetchTimeSeries).mockResolvedValue([]);
  vi.mocked(useTransactionCatalogs).mockReturnValue({ categoryCodeMap: {}, typeMap: {}, categories: [] } as unknown as ReturnType<typeof useTransactionCatalogs>);
});

afterEach(() => { cleanup(); vi.resetAllMocks(); });

describe('budget detail', () => {
  it('marks a plan as primary', async () => {
    vi.mocked(fetchBudgetPlans).mockResolvedValue([{ ...plan, primary: false }]);
    render(<BudgetPanel token="test" />);
    fireEvent.click(await screen.findByRole('button', { name: 'Marcar como principal' }));
    await waitFor(() => expect(setPrimaryBudgetPlan).toHaveBeenCalledWith('test', 1));
    expect(await screen.findByRole('button', { name: 'Presupuesto principal' })).toBeTruthy();
  });

  it('shows monthly compliance, filters by year, and opens a category trend from the chart', async () => {
    render(<BudgetPanel token="test" initialPlanId={1} />);
    await screen.findByRole('region', { name: 'Seguimiento mensual' });
    expect(screen.getByText('2025-12-01')).toBeTruthy();
    expect(screen.queryByText(/Invalid Date/)).toBeNull();
    expect(screen.getByText(/diciembre de 2025/i)).toBeTruthy();
    expect(screen.getByText(/enero de 2026/i)).toBeTruthy();
    expect(screen.getByText('1 mes excedido')).toBeTruthy();
    fireEvent.change(screen.getByRole('combobox', { name: 'Año' }), { target: { value: '2025' } });
    expect(screen.queryByText(/enero de 2026/i)).toBeNull();
    expect(screen.getByText('170,00 €')).toBeTruthy();
    fireEvent.click(screen.getByTestId('bar-Gastado'));
    expect(screen.getByText('Categoría:', { exact: false })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Seguimiento mensual · Food' })).toBeTruthy();
    expect(screen.getByText('Superado')).toBeTruthy();
    expect(within(screen.getByRole('group', { name: 'Filtrar por categoría' })).getByRole('button', { name: 'Food' }).getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(screen.getByRole('button', { name: 'Todas las categorías' }));
    expect(screen.getByRole('heading', { name: 'Seguimiento mensual' })).toBeTruthy();
  });

  it('shows the primary budget on the dashboard and opens its detail', async () => {
    const open = vi.fn();
    render(<Dashboard token="test" transactions={[]} onUnauthorized={vi.fn()} onOpenBudget={open} />);
    const region = within(await screen.findByRole('region', { name: 'Presupuesto principal' }));
    expect(region.getByText('320,00 €')).toBeTruthy();
    expect(region.getByText('280,00 €')).toBeTruthy();
    expect(region.getByText('40,00 €')).toBeTruthy();
    fireEvent.click(region.getByRole('button', { name: 'Ver presupuesto' }));
    expect(open).toHaveBeenCalledWith(1);
  });

  it('includes income lines in the monthly detail and resets filters for a custom range', async () => {
    const salary: BudgetSnapshotLineDTO = { ...makeLine(7, 30, 'Salary', 'INC.SALARY', 200, 250), lineType: 'INCOME', variance: 50, compliant: true };
    vi.mocked(refreshBudgetSnapshot).mockResolvedValue({ ...snapshot, totalExpectedIncome: 200, totalIncome: 250,
      lines: [...snapshot.lines, salary], months: [{ ...snapshot.months[0], totalExpectedIncome: 200, totalIncome: 250, lines: [...snapshot.months[0].lines, salary] }, snapshot.months[1]] });
    render(<BudgetPanel token="test" initialPlanId={1} />);
    await screen.findByRole('region', { name: 'Seguimiento mensual' });
    const month = screen.getByText(/diciembre de 2025/i).closest('details')!;
    fireEvent.click(within(month).getByText(/diciembre de 2025/i));
    expect(within(month).getByText('Salary')).toBeTruthy();
    fireEvent.change(screen.getByRole('combobox', { name: 'Año' }), { target: { value: '2025' } });
    fireEvent.click(within(screen.getByRole('group', { name: 'Filtrar por categoría' })).getByRole('button', { name: 'Food' }));
    fireEvent.click(screen.getByRole('button', { name: 'Personalizado' }));
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2025-12-01' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-01-31' } });
    fireEvent.click(screen.getByRole('button', { name: /Calcular/ }));
    await waitFor(() => expect(refreshBudgetSnapshot).toHaveBeenLastCalledWith('test', 1, '2025-12-01', '2026-01-31'));
    expect((screen.getByRole('combobox', { name: 'Año' }) as HTMLSelectElement).value).toBe('ALL');
    expect(screen.getByRole('heading', { name: 'Seguimiento mensual' })).toBeTruthy();
  });

  it('does not label an unfinished month as fully completed', async () => {
    vi.mocked(refreshBudgetSnapshot).mockResolvedValue({ ...snapshot, periodStart: '2026-01-01', periodEnd: '2026-01-15', months: [snapshot.months[1]] });
    render(<BudgetPanel token="test" initialPlanId={1} />);
    const monthly = within(await screen.findByRole('region', { name: 'Seguimiento mensual' }));
    expect(monthly.getByText('Dentro del presupuesto')).toBeTruthy();
    expect(monthly.queryByText('Cumplido')).toBeNull();
  });

  it('explains currency mismatches instead of showing an incorrect total', async () => {
    vi.mocked(refreshBudgetSnapshot).mockRejectedValue({ isAxiosError: true, response: { status: 422 } });
    render(<BudgetPanel token="test" initialPlanId={1} />);
    expect(await screen.findByText(/Hay transacciones en otra divisa/)).toBeTruthy();
    expect(screen.queryByText('320,00 €')).toBeNull();
  });

  it('shows a full exceeded progress bar when spending exists with a zero budget', async () => {
    vi.mocked(refreshBudgetSnapshot).mockResolvedValue({ ...snapshot, totalBudget: 0, totalSpent: 5, variance: -5, compliant: false });
    const { container } = render(<BudgetPanel token="test" initialPlanId={1} />);
    await screen.findByText(/Presupuesto superado/);
    expect(container.querySelector<HTMLElement>('.bdg-bar-track.global .bdg-bar-fill')?.style.width).toBe('100%');
  });
});