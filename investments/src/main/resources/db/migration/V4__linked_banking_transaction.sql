ALTER TABLE investments.investment_operations
    ADD COLUMN IF NOT EXISTS linked_account_id BIGINT,
    ADD COLUMN IF NOT EXISTS linked_transaction_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_investment_operations_linked_account
    ON investments.investment_operations(linked_account_id)
    WHERE linked_account_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_investment_operations_linked_transaction
    ON investments.investment_operations(linked_transaction_id)
    WHERE linked_transaction_id IS NOT NULL;