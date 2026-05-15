-- Add category_id foreign key to merchants table
ALTER TABLE banking.merchants
ADD COLUMN category_id BIGINT,
ADD CONSTRAINT fk_merchants_category FOREIGN KEY (category_id) REFERENCES banking.categories(id) ON DELETE SET NULL;

-- Create index for faster lookups
CREATE INDEX idx_merchants_category_id ON banking.merchants(category_id);
