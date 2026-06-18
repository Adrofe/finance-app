ALTER TABLE investments.investment_instruments
    ADD COLUMN IF NOT EXISTS country_code VARCHAR(2),
    ADD COLUMN IF NOT EXISTS region VARCHAR(80),
    ADD COLUMN IF NOT EXISTS sector VARCHAR(100),
    ADD COLUMN IF NOT EXISTS industry VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_inv_instr_country_code
    ON investments.investment_instruments (country_code);

CREATE INDEX IF NOT EXISTS idx_inv_instr_region
    ON investments.investment_instruments (region);

CREATE INDEX IF NOT EXISTS idx_inv_instr_sector
    ON investments.investment_instruments (sector);

CREATE INDEX IF NOT EXISTS idx_inv_instr_industry
    ON investments.investment_instruments (industry);
