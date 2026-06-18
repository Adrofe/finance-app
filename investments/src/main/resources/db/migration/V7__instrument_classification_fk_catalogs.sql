CREATE TABLE IF NOT EXISTS investments.investment_countries (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(2) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS investments.investment_regions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS investments.investment_sectors (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(140) NOT NULL
);

CREATE TABLE IF NOT EXISTS investments.investment_industries (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL
);

ALTER TABLE investments.investment_instruments
    ADD COLUMN IF NOT EXISTS country_id BIGINT,
    ADD COLUMN IF NOT EXISTS region_id BIGINT,
    ADD COLUMN IF NOT EXISTS sector_id BIGINT,
    ADD COLUMN IF NOT EXISTS industry_id BIGINT;

INSERT INTO investments.investment_countries (code, name)
SELECT DISTINCT UPPER(TRIM(country_code)), UPPER(TRIM(country_code))
FROM investments.investment_instruments
WHERE country_code IS NOT NULL
  AND LENGTH(TRIM(country_code)) = 2
ON CONFLICT (code) DO NOTHING;

INSERT INTO investments.investment_regions (code, name)
SELECT DISTINCT
    UPPER(REGEXP_REPLACE(TRIM(region), '[^A-Za-z0-9]+', '_', 'g')),
    TRIM(region)
FROM investments.investment_instruments
WHERE region IS NOT NULL
  AND TRIM(region) <> ''
ON CONFLICT (code) DO NOTHING;

INSERT INTO investments.investment_sectors (code, name)
SELECT DISTINCT
    UPPER(REGEXP_REPLACE(TRIM(sector), '[^A-Za-z0-9]+', '_', 'g')),
    TRIM(sector)
FROM investments.investment_instruments
WHERE sector IS NOT NULL
  AND TRIM(sector) <> ''
ON CONFLICT (code) DO NOTHING;

INSERT INTO investments.investment_industries (code, name)
SELECT DISTINCT
    UPPER(REGEXP_REPLACE(TRIM(industry), '[^A-Za-z0-9]+', '_', 'g')),
    TRIM(industry)
FROM investments.investment_instruments
WHERE industry IS NOT NULL
  AND TRIM(industry) <> ''
ON CONFLICT (code) DO NOTHING;

UPDATE investments.investment_instruments i
SET country_id = c.id
FROM investments.investment_countries c
WHERE i.country_id IS NULL
  AND i.country_code IS NOT NULL
  AND UPPER(TRIM(i.country_code)) = c.code;

UPDATE investments.investment_instruments i
SET region_id = r.id
FROM investments.investment_regions r
WHERE i.region_id IS NULL
  AND i.region IS NOT NULL
  AND UPPER(REGEXP_REPLACE(TRIM(i.region), '[^A-Za-z0-9]+', '_', 'g')) = r.code;

UPDATE investments.investment_instruments i
SET sector_id = s.id
FROM investments.investment_sectors s
WHERE i.sector_id IS NULL
  AND i.sector IS NOT NULL
  AND UPPER(REGEXP_REPLACE(TRIM(i.sector), '[^A-Za-z0-9]+', '_', 'g')) = s.code;

UPDATE investments.investment_instruments i
SET industry_id = d.id
FROM investments.investment_industries d
WHERE i.industry_id IS NULL
  AND i.industry IS NOT NULL
  AND UPPER(REGEXP_REPLACE(TRIM(i.industry), '[^A-Za-z0-9]+', '_', 'g')) = d.code;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_instr_country_id') THEN
        ALTER TABLE investments.investment_instruments
            ADD CONSTRAINT fk_inv_instr_country_id
            FOREIGN KEY (country_id) REFERENCES investments.investment_countries(id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_instr_region_id') THEN
        ALTER TABLE investments.investment_instruments
            ADD CONSTRAINT fk_inv_instr_region_id
            FOREIGN KEY (region_id) REFERENCES investments.investment_regions(id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_instr_sector_id') THEN
        ALTER TABLE investments.investment_instruments
            ADD CONSTRAINT fk_inv_instr_sector_id
            FOREIGN KEY (sector_id) REFERENCES investments.investment_sectors(id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inv_instr_industry_id') THEN
        ALTER TABLE investments.investment_instruments
            ADD CONSTRAINT fk_inv_instr_industry_id
            FOREIGN KEY (industry_id) REFERENCES investments.investment_industries(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inv_instr_country_id
    ON investments.investment_instruments (country_id);

CREATE INDEX IF NOT EXISTS idx_inv_instr_region_id
    ON investments.investment_instruments (region_id);

CREATE INDEX IF NOT EXISTS idx_inv_instr_sector_id
    ON investments.investment_instruments (sector_id);

CREATE INDEX IF NOT EXISTS idx_inv_instr_industry_id
    ON investments.investment_instruments (industry_id);
