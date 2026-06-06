ALTER TABLE investments.investment_instruments
    ADD COLUMN IF NOT EXISTS finect_url VARCHAR(500);