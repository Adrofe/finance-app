-- Add nightlife and drinks subcategory to split it from restaurants
INSERT INTO banking.categories (name, parent_id, code)
VALUES ('Bar and Drinks', (SELECT id FROM banking.categories WHERE code = 'FOOD'), 'FOOD.BAR')
ON CONFLICT (code) DO NOTHING;
