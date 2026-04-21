export type Transaction = {
  id?: number;
  sourceAccountId?: number;
  destinationAccountId?: number;
  bookingDate?: string;
  valueDate?: string;
  amount?: number;
  currency?: string;
  description?: string;
  merchantId?: number;
  merchantName?: string;
  categoryId?: number;
  tagIds?: number[];
  externalId?: string;
  statusId?: number;
  typeId?: number;
};

export type CreateTransactionRequest = {
  sourceAccountId?: number;
  destinationAccountId?: number;
  bookingDate: string;
  valueDate?: string;
  amount: number;
  currency?: string;
  description?: string;
  merchantId?: number;
  categoryId?: number;
  tagIds?: number[];
  statusId?: number;
  typeId?: number;
};

export type AppTab = 'banking' | 'insights';
export type BankingSubTab = 'dashboard' | 'accounts' | 'transactions' | 'tags' | 'budgets';
