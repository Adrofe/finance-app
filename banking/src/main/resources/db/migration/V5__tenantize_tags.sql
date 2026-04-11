ALTER TABLE banking.tags
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE banking.tags
SET tenant_id = 1
WHERE tenant_id IS NULL;

ALTER TABLE banking.tags
  ALTER COLUMN tenant_id SET NOT NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.table_constraints
    WHERE constraint_schema = 'banking'
      AND table_name = 'tags'
      AND constraint_name = 'tags_name_key'
  ) THEN
    ALTER TABLE banking.tags DROP CONSTRAINT tags_name_key;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints
    WHERE constraint_schema = 'banking'
      AND table_name = 'tags'
      AND constraint_name = 'tags_tenant_name_key'
  ) THEN
    ALTER TABLE banking.tags
      ADD CONSTRAINT tags_tenant_name_key UNIQUE (tenant_id, name);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_tags_tenant
  ON banking.tags(tenant_id);

CREATE INDEX IF NOT EXISTS ix_transaction_tags_tag_transaction
  ON banking.transaction_tags(tag_id, transaction_id);