CREATE TABLE IF NOT EXISTS investments.investment_market_regimes (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(140) NOT NULL
);

ALTER TABLE investments.investment_instrument_exposures
    ADD COLUMN IF NOT EXISTS market_regime_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_instr_exposure_market_regime') THEN
        ALTER TABLE investments.investment_instrument_exposures
            ADD CONSTRAINT fk_instr_exposure_market_regime
            FOREIGN KEY (market_regime_id) REFERENCES investments.investment_market_regimes(id);
    END IF;
END $$;

ALTER TABLE investments.investment_instrument_exposures
    DROP CONSTRAINT IF EXISTS ck_instr_exposure_dimension;

ALTER TABLE investments.investment_instrument_exposures
    ADD CONSTRAINT ck_instr_exposure_dimension
    CHECK (dimension IN ('COUNTRY', 'REGION', 'SECTOR', 'INDUSTRY', 'MARKET_REGIME'));

CREATE INDEX IF NOT EXISTS idx_instr_exposure_market_regime
    ON investments.investment_instrument_exposures (market_regime_id);