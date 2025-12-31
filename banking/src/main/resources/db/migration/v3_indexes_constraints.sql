CREATE INDEX IF NOT EXISTS ix_accounts_tenant ON banking.accounts(tenant_id);
CREATE INDEX IF NOT EXISTS ix_transactions_account ON banking.transactions(source_account_id);
CREATE INDEX IF NOT EXISTS ix_transactions_tenant_date ON banking.transactions(tenant_id, booking_date);
