CREATE SCHEMA IF NOT EXISTS wealth;

CREATE TABLE IF NOT EXISTS wealth.snapshots (
  id                 BIGSERIAL PRIMARY KEY,
  tenant_id          BIGINT NOT NULL,
  snapshot_date      DATE NOT NULL,
  snapshot_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  currency           VARCHAR(3) NOT NULL DEFAULT 'EUR',
  total_value        NUMERIC(18,2) NOT NULL DEFAULT 0,
  cash_value         NUMERIC(18,2) NOT NULL DEFAULT 0,
  funds_value        NUMERIC(18,2) NOT NULL DEFAULT 0,
  etfs_value         NUMERIC(18,2) NOT NULL DEFAULT 0,
  crypto_value       NUMERIC(18,2) NOT NULL DEFAULT 0,
  stocks_value       NUMERIC(18,2) NOT NULL DEFAULT 0,
  bonds_value        NUMERIC(18,2) NOT NULL DEFAULT 0,
  real_estate_value  NUMERIC(18,2) NOT NULL DEFAULT 0,
  other_value        NUMERIC(18,2) NOT NULL DEFAULT 0,
  notes              TEXT,
  created_at         TIMESTAMPTZ DEFAULT NOW(),
  updated_at         TIMESTAMPTZ DEFAULT NOW(),
  CONSTRAINT uq_snapshots_tenant_date UNIQUE (tenant_id, snapshot_date)
);

CREATE INDEX IF NOT EXISTS idx_snapshots_tenant_date
  ON wealth.snapshots(tenant_id, snapshot_date DESC);

CREATE TABLE IF NOT EXISTS wealth.snapshot_items (
  id               BIGSERIAL PRIMARY KEY,
  snapshot_id      BIGINT NOT NULL REFERENCES wealth.snapshots(id) ON DELETE CASCADE,
  asset_type       VARCHAR(32) NOT NULL,
  asset_subtype    VARCHAR(64),
  source_system    VARCHAR(32),
  source_ref       VARCHAR(128),
  label            VARCHAR(128) NOT NULL,
  quantity         NUMERIC(24,8),
  unit_price       NUMERIC(18,6),
  value_amount     NUMERIC(18,2) NOT NULL,
  currency         VARCHAR(3) NOT NULL DEFAULT 'EUR',
  created_at       TIMESTAMPTZ DEFAULT NOW(),
  updated_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_snapshot_items_snapshot
  ON wealth.snapshot_items(snapshot_id);

CREATE INDEX IF NOT EXISTS idx_snapshot_items_type
  ON wealth.snapshot_items(asset_type);
