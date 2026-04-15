export type Transaction = {
  id?: number;
  externalId?: string;
  bookingDate?: string;
  amount?: number;
  description?: string;
  merchantName?: string;
};

export type AppTab = 'banking' | 'insights';
export type BankingSubTab = 'dashboard' | 'accounts' | 'transactions' | 'tags' | 'budgets';
