CREATE TABLE IF NOT EXISTS banking.tags (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(64) NOT NULL UNIQUE,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS banking.transaction_statuses (
  id           BIGSERIAL PRIMARY KEY,
  code         VARCHAR(32) NOT NULL UNIQUE,
  description  TEXT,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS banking.transaction_types (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(64) NOT NULL UNIQUE,
  description  TEXT,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS banking.merchants (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(128) NOT NULL UNIQUE,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS banking.categories (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(64) NOT NULL,
  code         VARCHAR(32) NOT NULL UNIQUE,
  parent_id    BIGINT REFERENCES banking.categories(id) ON DELETE SET NULL,
  is_fixed     BOOLEAN DEFAULT FALSE,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS banking.tenants (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(128) NOT NULL UNIQUE,
  created_at   TIMESTAMPTZ DEFAULT NOW(),
  updated_at   TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS banking.account_types (
  id               BIGSERIAL PRIMARY KEY,
  name             VARCHAR(64) NOT NULL UNIQUE,
  description      TEXT,
  created_at       TIMESTAMPTZ DEFAULT NOW(),
  updated_at       TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS banking.institutions (
  id               BIGSERIAL PRIMARY KEY,
  name             VARCHAR(128) NOT NULL UNIQUE,
  country          VARCHAR(2),         -- ISO 3166-1 alpha-2
  website          VARCHAR(256),
  created_at       TIMESTAMPTZ DEFAULT NOW(),
  updated_at       TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS banking.accounts (
  id               BIGSERIAL PRIMARY KEY,
  tenant_id        BIGINT NOT NULL,
  institution_id   BIGINT REFERENCES banking.institutions(id),
  name             VARCHAR(128) NOT NULL,
  iban             VARCHAR(34),
  type             BIGINT REFERENCES banking.account_types(id),
  currency         VARCHAR(3) NOT NULL, -- EUR|USD|...
  last_balance_real NUMERIC(18,2),
  last_balance_real_date DATE,
  last_balance_available NUMERIC(18,2),
  last_balance_available_date DATE,
  is_active       BOOLEAN DEFAULT TRUE,
  created_at       TIMESTAMPTZ DEFAULT NOW(),
  updated_at       TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE (tenant_id, iban)
);

CREATE TABLE IF NOT EXISTS banking.transactions (
  id                   BIGSERIAL PRIMARY KEY,
  tenant_id            BIGINT NOT NULL,
  source_account_id    BIGINT NOT NULL REFERENCES banking.accounts(id),
  destination_account_id BIGINT REFERENCES banking.accounts(id),
  booking_date         DATE NOT NULL,
  value_date           DATE,
  amount               NUMERIC(18,2) NOT NULL,
  currency             VARCHAR(3) NOT NULL,
  description_raw      TEXT,
  merchant_id          BIGINT REFERENCES banking.merchants(id),
  category_id          BIGINT REFERENCES banking.categories(id),
  external_tx_id       VARCHAR(128),
  status_id            BIGINT REFERENCES banking.transaction_statuses(id) DEFAULT 1,
  transaction_type     BIGINT REFERENCES banking.transaction_types(id),
  created_at           TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS banking.transaction_tags (
  transaction_id BIGINT NOT NULL REFERENCES banking.transactions(id) ON DELETE CASCADE,
  tag_id         BIGINT NOT NULL REFERENCES banking.tags(id) ON DELETE CASCADE,
  PRIMARY KEY (transaction_id, tag_id)
);

-- Idempotencia en importaciones
CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_tenant_ext
  ON banking.transactions(tenant_id, external_tx_id)
  WHERE external_tx_id IS NOT NULL;
