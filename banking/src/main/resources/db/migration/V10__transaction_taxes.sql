-- ─── Tax Types Catalog ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS banking.tax_types (
  id          BIGSERIAL PRIMARY KEY,
  code        VARCHAR(32) NOT NULL UNIQUE,
  name        VARCHAR(128) NOT NULL,
  description TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO banking.tax_types (code, name, description) VALUES
  ('DIVIDEND_WITHHOLDING', 'Retención dividendos',   'Retención fiscal aplicada sobre dividendos de valores mobiliarios'),
  ('INTEREST_TAX',         'Retención intereses',    'Retención fiscal sobre intereses de cuentas de ahorro y depósitos'),
  ('CRYPTO_GAIN_TAX',      'Retención criptomonedas','Retención o autoliquidación sobre ganancias de criptoactivos'),
  ('CAPITAL_GAIN_TAX',     'Retención plusvalías',   'Retención fiscal sobre ganancias de transmisión de activos'),
  ('OTHER_WITHHOLDING',    'Otra retención',         'Retención fiscal de naturaleza no especificada')
ON CONFLICT (code) DO NOTHING;

-- ─── Transaction Taxes ────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS banking.transaction_taxes (
  id             BIGSERIAL PRIMARY KEY,
  tenant_id      BIGINT NOT NULL,
  transaction_id BIGINT NOT NULL UNIQUE REFERENCES banking.transactions(id) ON DELETE CASCADE,
  gross_amount   NUMERIC(18,2) NOT NULL,
  tax_amount     NUMERIC(18,2) NOT NULL,
  tax_type_id    BIGINT NOT NULL REFERENCES banking.tax_types(id),
  notes          TEXT,
  created_at     TIMESTAMPTZ DEFAULT NOW(),
  updated_at     TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tx_taxes_tenant    ON banking.transaction_taxes (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tx_taxes_tx        ON banking.transaction_taxes (transaction_id);
CREATE INDEX IF NOT EXISTS idx_tx_taxes_type      ON banking.transaction_taxes (tax_type_id);
