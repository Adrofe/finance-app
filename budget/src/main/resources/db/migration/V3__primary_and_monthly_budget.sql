ALTER TABLE budget.budget_plans
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN period VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    ADD COLUMN plan_start_date DATE,
    ADD COLUMN plan_end_date DATE;

CREATE UNIQUE INDEX idx_budget_plans_primary_tenant
    ON budget.budget_plans (tenant_id) WHERE is_primary = TRUE;

ALTER TABLE budget.budget_snapshot_lines ADD COLUMN month_start DATE;
CREATE INDEX idx_budget_snapshot_lines_month ON budget.budget_snapshot_lines (budget_snapshot_id, month_start);