ALTER TABLE budget.budget_plan_lines ADD COLUMN line_period VARCHAR(16);
ALTER TABLE budget.budget_snapshot_lines
	ADD COLUMN line_period VARCHAR(16),
	ADD COLUMN line_period_start DATE,
	ADD COLUMN line_period_end DATE,
	ADD COLUMN line_observed_until DATE;