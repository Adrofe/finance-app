CREATE TABLE IF NOT EXISTS investments.investment_country_aliases (
    id BIGSERIAL PRIMARY KEY,
    source_name VARCHAR(180) NOT NULL,
    normalized_source_name VARCHAR(220) NOT NULL UNIQUE,
    country_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_country_alias_country
        FOREIGN KEY (country_id) REFERENCES investments.investment_countries(id)
);

CREATE TABLE IF NOT EXISTS investments.investment_region_aliases (
    id BIGSERIAL PRIMARY KEY,
    source_name VARCHAR(180) NOT NULL,
    normalized_source_name VARCHAR(220) NOT NULL UNIQUE,
    region_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_region_alias_region
        FOREIGN KEY (region_id) REFERENCES investments.investment_regions(id)
);

CREATE INDEX IF NOT EXISTS idx_country_alias_country_id
    ON investments.investment_country_aliases (country_id);

CREATE INDEX IF NOT EXISTS idx_region_alias_region_id
    ON investments.investment_region_aliases (region_id);