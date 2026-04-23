export type DashboardSummary = {
  totalIncome: number;
  totalExpenses: number;
  net: number;
  savingsRate: number | null;
  transactionCount: number;
};

export type SpendingByCategory = {
  categoryId: number;
  categoryCode: string;
  categoryName: string;
  /** Negative value (e.g. –420.00) */
  total: number;
  /** 0–100, null when totalExpenses is zero */
  percentage: number | null;
  transactionCount: number;
};

export type TimeSeriesPoint = {
  /** "YYYY-MM" or "YYYY-MM-DD" */
  period: string;
  income: number;
  expenses: number;
  net: number;
};

export type DashboardPreset =
  | 'this-month'
  | 'last-month'
  | '3-months'
  | '6-months'
  | 'this-year'
  | 'custom';
