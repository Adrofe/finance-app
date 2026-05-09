-- Add line_type to plan lines and snapshot lines (EXPENSE | INCOME)
ALTER TABLE budget.budget_plan_lines
    ADD COLUMN IF NOT EXISTS line_type VARCHAR(10) NOT NULL DEFAULT 'EXPENSE';

ALTER TABLE budget.budget_snapshot_lines
    ADD COLUMN IF NOT EXISTS line_type VARCHAR(10) NOT NULL DEFAULT 'EXPENSE';

-- Add income and net balance fields to snapshots
ALTER TABLE budget.budget_snapshots
    ADD COLUMN IF NOT EXISTS total_expected_income DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_income           DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS income_variance        DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS net_balance            DECIMAL(18,2) NOT NULL DEFAULT 0;
