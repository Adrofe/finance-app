import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { Account } from '../types/account';
import { fetchAccounts, updateAccount } from '../services/accountsService';
import { fetchInstitutions } from '../services/institutionsService';
import { fetchAccountTypes } from '../services/accountTypesService';
import { CatalogService } from '../services/catalogService';
import { AccountsTable } from './AccountsTable';
import { BankImportPanel } from './BankImportPanel';
import { CreateTransactionModal } from './CreateTransactionModal';
import { BatchTransactionModal } from './BatchTransactionModal';

vi.mock('../services/accountsService', () => ({
  fetchAccounts: vi.fn(),
  updateAccount: vi.fn(),
  createAccount: vi.fn(),
  deleteAccount: vi.fn(),
}));
vi.mock('../services/institutionsService', () => ({ fetchInstitutions: vi.fn() }));
vi.mock('../services/accountTypesService', () => ({ fetchAccountTypes: vi.fn() }));

const activeAccount: Account = {
  id: 1, name: 'Active account', currency: 'EUR', accountTypeId: 1,
  lastBalanceReal: 100, lastBalanceAvailable: 90, isActive: true,
};
const closedAccount: Account = {
  id: 2, name: 'Closed account', currency: 'EUR', accountTypeId: 1,
  lastBalanceReal: 50, lastBalanceAvailable: 40, isActive: false,
};

function mockCatalogs(accounts: Account[]) {
  vi.spyOn(CatalogService, 'fetchAccounts').mockResolvedValue(accounts);
  vi.spyOn(CatalogService, 'fetchCategories').mockResolvedValue([{ id: 1, name: 'Internal transfers', code: 'INT' }]);
  vi.spyOn(CatalogService, 'fetchStatuses').mockResolvedValue([{ id: 1, code: 'BOOKED', description: '' }]);
  vi.spyOn(CatalogService, 'fetchTypes').mockResolvedValue([]);
  vi.spyOn(CatalogService, 'fetchTags').mockResolvedValue([]);
  vi.spyOn(CatalogService, 'fetchMerchants').mockResolvedValue([]);
  vi.spyOn(CatalogService, 'fetchTaxTypes').mockResolvedValue([]);
}

beforeEach(() => {
  vi.mocked(fetchInstitutions).mockResolvedValue([]);
  vi.mocked(fetchAccountTypes).mockResolvedValue([]);
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.resetAllMocks();
});

describe('account activity', () => {
  it('deactivates and reactivates without losing access to account history', async () => {
    let accounts = [{ ...activeAccount }, { ...closedAccount }];
    vi.mocked(fetchAccounts).mockImplementation(async () => accounts.map(account => ({ ...account })));
    vi.mocked(updateAccount).mockImplementation(async (_token, id, update) => {
      accounts = accounts.map(account => account.id === id ? { ...account, ...update } : account);
      return accounts.find(account => account.id === id)!;
    });
    const onViewTransactions = vi.fn();
    render(<AccountsTable token="test" onViewTransactions={onViewTransactions} />);

    const activeToggle = await screen.findByRole('checkbox', { name: 'Desactivar Active account' });
    const summary = document.querySelector('.at-summary-stats')!;
    expect(within(summary as HTMLElement).getByText('1')).toBeTruthy();
    expect(within(summary as HTMLElement).getByText('100,00 €')).toBeTruthy();
    expect(within(summary as HTMLElement).getByText('90,00 €')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Closed account' }));
    expect(onViewTransactions).toHaveBeenCalledWith(2);

    fireEvent.click(activeToggle);
    await screen.findByRole('checkbox', { name: 'Reactivar Active account' });
    expect(vi.mocked(updateAccount)).toHaveBeenCalledWith('test', 1, expect.objectContaining({ isActive: false }));
    expect(within(summary as HTMLElement).getByText('0')).toBeTruthy();

    fireEvent.click(screen.getByRole('checkbox', { name: 'Reactivar Closed account' }));
    await screen.findByRole('checkbox', { name: 'Desactivar Closed account' });
    expect(vi.mocked(updateAccount)).toHaveBeenCalledWith('test', 2, expect.objectContaining({ isActive: true }));
    expect(within(summary as HTMLElement).getByText('50,00 €')).toBeTruthy();
    expect(within(summary as HTMLElement).getByText('40,00 €')).toBeTruthy();
  });

  it('excludes closed accounts from new transactions but allows legacy accounts', async () => {
    mockCatalogs([activeAccount, closedAccount, { ...activeAccount, id: 3, name: 'Legacy account', isActive: null }]);
    render(<CreateTransactionModal accessToken="test" onClose={() => {}} onSuccess={() => {}} />);

    fireEvent.click(await screen.findByRole('button', { name: /Cuenta Origen/ }));
    expect(screen.getByRole('button', { name: /Active account/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Legacy account/ })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Closed account/ })).toBeNull();
  });

  it('excludes closed accounts from batch transactions', async () => {
    mockCatalogs([activeAccount, closedAccount]);
    render(<BatchTransactionModal accessToken="test" onClose={() => {}} />);

    fireEvent.click(screen.getByRole('button', { name: /Selecciona cuenta/ }));
    expect(await screen.findByText('Active account')).toBeTruthy();
    expect(screen.queryByText('Closed account')).toBeNull();
  });

  it('only offers active accounts for CSV imports', async () => {
    vi.mocked(fetchAccounts).mockResolvedValue([closedAccount, activeAccount]);
    render(<BankImportPanel token="test" />);

    const select = await screen.findByLabelText('Cuenta destino') as HTMLSelectElement;
    await waitFor(() => expect(select.value).toBe('1'));
    expect(within(select).getByRole('option', { name: /Active account/ })).toBeTruthy();
    expect(within(select).queryByRole('option', { name: /Closed account/ })).toBeNull();
  });
});