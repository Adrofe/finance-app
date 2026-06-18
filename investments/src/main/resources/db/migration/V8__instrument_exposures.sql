CREATE TABLE IF NOT EXISTS investments.investment_instrument_exposures (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    dimension VARCHAR(20) NOT NULL,
    country_id BIGINT,
    region_id BIGINT,
    sector_id BIGINT,
    industry_id BIGINT,
    weight_pct NUMERIC(7,4) NOT NULL,
    CONSTRAINT fk_instr_exposure_instrument FOREIGN KEY (instrument_id) REFERENCES investments.investment_instruments(id),
    CONSTRAINT fk_instr_exposure_country FOREIGN KEY (country_id) REFERENCES investments.investment_countries(id),
    CONSTRAINT fk_instr_exposure_region FOREIGN KEY (region_id) REFERENCES investments.investment_regions(id),
    CONSTRAINT fk_instr_exposure_sector FOREIGN KEY (sector_id) REFERENCES investments.investment_sectors(id),
    CONSTRAINT fk_instr_exposure_industry FOREIGN KEY (industry_id) REFERENCES investments.investment_industries(id),
    CONSTRAINT ck_instr_exposure_dimension CHECK (dimension IN ('COUNTRY', 'REGION', 'SECTOR', 'INDUSTRY'))
);

CREATE INDEX IF NOT EXISTS idx_instr_exposure_instrument
    ON investments.investment_instrument_exposures (instrument_id);

CREATE INDEX IF NOT EXISTS idx_instr_exposure_dimension
    ON investments.investment_instrument_exposures (dimension);
