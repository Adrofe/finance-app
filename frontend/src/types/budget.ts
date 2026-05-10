export type BudgetLineType = 'EXPENSE' | 'INCOME';

export type BudgetPeriod = 'MONTHLY' | 'QUARTERLY' | 'ANNUAL' | 'CUSTOM';

export type BudgetPlanLineRequest = {
  categoryId?: number;
  categoryCode: string;
  categoryName: string;
  budgetAmount: number;
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
  planId: number;
  categoryId?: number;
  categoryCode: string;
  categoryName: string;
  budgetAmount: number;
  lineType: BudgetLineType;
};

export type BudgetPlanDTO = {
  id: number;
  tenantId: number;
  name: string;
  description?: string;
  period: BudgetPeriod;
  startDate?: string;
  endDate?: string;
  currency: string;
  isActive: boolean;
  lines: BudgetPlanLineDTO[];
};

export type BudgetSnapshotLineDTO = {
  id: number;
  snapshotId: number;
  categoryId?: number;
  categoryCode: string;
  categoryName: string;
  budgetAmount: number;
  spentAmount: number;
  variance: number;
  compliant: boolean;
  lineType: BudgetLineType;
};

export type BudgetSnapshotDTO = {
  id: number;
  planId: number;
  startDate: string;
  endDate: string;
  totalBudget: number;
  totalSpent: number;
  variance: number;
  compliant: boolean;
  computedAt: string;
  totalExpectedIncome: number;
  totalIncome: number;
  incomeVariance: number;
  netBalance: number;
  lines: BudgetSnapshotLineDTO[];
};
