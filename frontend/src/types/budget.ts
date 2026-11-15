export type BudgetLineType = 'EXPENSE' | 'INCOME';

export type BudgetPeriod = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL' | 'CUSTOM';
export type BudgetLinePeriod = 'MONTHLY' | 'QUARTERLY' | 'SEMIANNUAL' | 'ANNUAL';

export type BudgetPlanLineRequest = {
  categoryId?: number;
  categoryCode: string;
  categoryName: string;
  budgetAmount: number;
  linePeriod?: BudgetLinePeriod | null;
  lineType: BudgetLineType;
};

export type BudgetPlanRequest = {
  id?: number;
  name: string;
  description?: string;
  period: BudgetPeriod;
  startDate?: string;
  endDate?: string;
  currency: string;
  lines: BudgetPlanLineRequest[];
};

export type BudgetPlanLineDTO = {
  id: number;
  categoryId?: number;
  categoryCode: string;
  categoryName: string;
  budgetAmount: number;
  linePeriod?: BudgetLinePeriod | null;
  lineType: BudgetLineType;
};

export type BudgetPlanDTO = {
  id: number;
  name: string;
  description?: string;
  period: BudgetPeriod;
  startDate?: string;
  endDate?: string;
  currency: string;
  active: boolean;
  primary: boolean;
  lines: BudgetPlanLineDTO[];
};

export type BudgetSnapshotLineDTO = {
  id: number;
  categoryId?: number;
  categoryCode: string;
  categoryName: string;
  budgetAmount: number;
  linePeriod?: BudgetLinePeriod | null;
  linePeriodStart?: string | null;
  linePeriodEnd?: string | null;
  lineObservedUntil?: string | null;
  spentAmount: number;
  variance: number;
  compliant: boolean;
  lineType: BudgetLineType;
  transactionCount: number;
};

export type BudgetMonthDTO = {
  monthStart: string;
  totalBudget: number;
  totalSpent: number;
  variance: number;
  compliant: boolean;
  totalExpectedIncome: number;
  totalIncome: number;
  lines: BudgetSnapshotLineDTO[];
};

export type BudgetSnapshotDTO = {
  id: number;
  budgetPlanId: number;
  periodStart: string;
  periodEnd: string;
  totalBudget: number;
  totalSpent: number;
  variance: number;
  compliant: boolean;
  generatedAt: string;
  totalExpectedIncome: number;
  totalIncome: number;
  incomeVariance: number;
  netBalance: number;
  lines: BudgetSnapshotLineDTO[];
  months: BudgetMonthDTO[];
  periodicLines?: BudgetSnapshotLineDTO[];
};
