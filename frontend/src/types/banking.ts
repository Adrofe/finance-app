export type Transaction = {
  id?: number;
  sourceAccountId?: number;
  destinationAccountId?: number;
  bookingDate?: string;
  valueDate?: string;
  amount?: number;
  description?: string;
  merchantName?: string;
  categoryId?: number;
  tagIds?: number[];
  externalId?: string;
  statusId?: number;
  typeId?: number;
};

export type AppTab = 'banking' | 'insights';
export type BankingSubTab = 'dashboard' | 'accounts' | 'transactions' | 'tags' | 'budgets';
