-- Double-entry transfers: link the two halves (source debit ↔ destination credit)
ALTER TABLE banking.transactions
    ADD COLUMN IF NOT EXISTS linked_transaction_id BIGINT
        REFERENCES banking.transactions(id) ON DELETE SET NULL;
