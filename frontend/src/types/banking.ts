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
  linkedTransactionId?: number;
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

export type AppTab = 'banking' | 'investments' | 'wealth';
export type BankingSubTab = 'dashboard' | 'accounts' | 'transactions' | 'budget' | 'import';

export type BankFormat = 'INTERNAL' | 'SANTANDER' | 'BBVA' | 'ING' | 'IMAGIN';

export type ImportError = {
  rowNumber: number;
  field: string;
  message: string;
  rawValue: string;
};

export type CsvImportResult = {
  totalRows: number;
  successCount: number;
  failedCount: number;
  skippedCount: number;
  errors: ImportError[];
};
