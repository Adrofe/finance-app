import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useInvestmentOperations } from '../hooks/useInvestmentOperations';
import { fetchInstruments, fetchInvestmentTypes, fetchPlatforms } from '../services/investmentCatalogService';
import { CatalogService } from '../services/catalogService';
import type { InvestmentOperation, InvestmentPosition } from '../types/investments';
import { InvestmentOperationsTable } from './InvestmentOperationsTable';

vi.mock('../hooks/useInvestmentOperations', () => ({ useInvestmentOperations: vi.fn() }));
vi.mock('../services/investmentCatalogService', () => ({
  fetchInstruments: vi.fn(),
  fetchInvestmentTypes: vi.fn(),
  fetchPlatforms: vi.fn(),
}));

const positions: InvestmentPosition[] = [
  { id: 1, tenantId: 1, typeId: 1, name: 'Position A', instrumentId: 10, platformId: 1, currency: 'EUR', investedAmount: 20, quantity: 3.5 },
  { id: 2, tenantId: 1, typeId: 1, name: 'Position B', instrumentId: 10, platformId: 2, currency: 'EUR', investedAmount: 10, quantity: 1.25 },
];
const existingSale: InvestmentOperation = {
  id: 7, investmentId: 1, tenantId: 1, type: 'SELL', operationDate: '2025-01-10',
  quantity: 2, unitPrice: 11, fees: 0, totalAmount: 22, currency: 'EUR', eurExchangeRate: 1, totalAmountEur: 22,
};
const addOperation = vi.fn();
const editOperation = vi.fn();

beforeEach(() => {
  vi.mocked(useInvestmentOperations).mockReturnValue({
    operations: [existingSale], investments: positions, loading: false, error: null,
    clearError: vi.fn(), reload: vi.fn(), addOperation, editOperation, removeOperation: vi.fn(),
  });
  vi.mocked(fetchInstruments).mockResolvedValue([{ id: 10, typeId: 1, code: 'ABC', symbol: 'ABC', name: 'Fund ABC', currency: 'EUR', lastPrice: 12.5 }]);
  vi.mocked(fetchInvestmentTypes).mockResolvedValue([{ id: 1, code: 'FUND', name: 'Fund' }]);
  vi.mocked(fetchPlatforms).mockResolvedValue([{ id: 1, code: 'A', name: 'Platform A' }, { id: 2, code: 'B', name: 'Platform B' }]);
  vi.spyOn(CatalogService, 'fetchCategories').mockResolvedValue([]);
  addOperation.mockResolvedValue(existingSale);
  editOperation.mockResolvedValue(existingSale);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.resetAllMocks();
});

async function openNewSale() {
  render(<InvestmentOperationsTable token="test" />);
  const create = await screen.findByRole('button', { name: /Nueva operación/ });
  await waitFor(() => expect((create as HTMLButtonElement).disabled).toBe(false));
  fireEvent.click(create);
  const dialog = within(screen.getByRole('dialog'));
  fireEvent.change(dialog.getByLabelText('Plataforma'), { target: { value: '1' } });
  fireEvent.focus(dialog.getByLabelText('Instrumento'));
  fireEvent.mouseDown(dialog.getByText('Fund ABC'));
  fireEvent.click(dialog.getByRole('button', { name: 'Venta' }));
  return dialog;
}

describe('FIFO sale form', () => {
  it('prefills the available position and latest price, and calculates net proceeds', async () => {
    const dialog = await openNewSale();
    const quantity = dialog.getByLabelText('Cantidad') as HTMLInputElement;
    const price = dialog.getByLabelText('Precio unitario') as HTMLInputElement;
    expect(quantity.value).toBe('3.5');
    expect(quantity.max).toBe('3.5');
    expect(price.value).toBe('12.5');
    expect(dialog.getByText('Disponible: 3,5')).toBeTruthy();
    expect(dialog.getByText('43,75 €')).toBeTruthy();

    fireEvent.change(dialog.getByLabelText('Fees'), { target: { value: '2.25' } });
    expect(dialog.getByText('41,50 €')).toBeTruthy();
    fireEvent.change(quantity, { target: { value: '4' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Integrada' }));
    expect(dialog.getByText('La cantidad disponible para vender es 3,5.')).toBeTruthy();
    expect(addOperation).not.toHaveBeenCalled();

    fireEvent.change(quantity, { target: { value: '2' } });
    fireEvent.submit(dialog.getByLabelText('Cantidad').closest('form')!);
    await waitFor(() => expect(addOperation).toHaveBeenCalledWith(expect.objectContaining({ type: 'SELL', quantity: 2, unitPrice: 12.5, fees: 2.25, platformId: 1 })));
  });

  it('updates the limit when the platform changes and clears a price when currency changes', async () => {
    const dialog = await openNewSale();
    const quantity = dialog.getByLabelText('Cantidad') as HTMLInputElement;
    fireEvent.change(dialog.getByLabelText('Plataforma'), { target: { value: '2' } });
    expect(quantity.value).toBe('1.25');
    expect(quantity.max).toBe('1.25');
    fireEvent.change(dialog.getByLabelText('Plataforma'), { target: { value: '' } });
    expect(quantity.value).toBe('');
    expect(quantity.max).toBe('0');
    expect(dialog.getByText('No hay una posición disponible para esta plataforma')).toBeTruthy();
    fireEvent.change(dialog.getByLabelText('Divisa'), { target: { value: 'USD' } });
    expect((dialog.getByLabelText('Precio unitario') as HTMLInputElement).value).toBe('');
  });

  it('requires a manually entered price when no quote is available', async () => {
    vi.mocked(fetchInstruments).mockResolvedValue([{ id: 10, typeId: 1, code: 'ABC', symbol: 'ABC', name: 'Fund ABC', currency: 'EUR' }]);
    const dialog = await openNewSale();
    const price = dialog.getByLabelText('Precio unitario') as HTMLInputElement;
    expect(price.value).toBe('');
    expect(dialog.getByText('Sin cotización disponible')).toBeTruthy();
    fireEvent.change(price, { target: { value: '10' } });
    expect(dialog.getByText('35,00 €')).toBeTruthy();
  });

  it('includes the original units when editing a sale without replacing its price', async () => {
    render(<InvestmentOperationsTable token="test" />);
    await waitFor(() => expect(screen.getByRole('button', { name: /Nueva operación/ }).hasAttribute('disabled')).toBe(false));
    fireEvent.click(screen.getByTitle('Editar operación'));
    const dialog = within(screen.getByRole('dialog'));
    const quantity = dialog.getByLabelText('Cantidad') as HTMLInputElement;
    expect(quantity.value).toBe('2');
    expect(quantity.max).toBe('5.5');
    expect((dialog.getByLabelText('Precio unitario') as HTMLInputElement).value).toBe('11');
    expect(dialog.getByText('Disponible: 5,5')).toBeTruthy();
  });
});