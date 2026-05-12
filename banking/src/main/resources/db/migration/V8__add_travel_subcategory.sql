-- Add Travel subcategory under Transportation (flights, ferries, cruises, long-distance trains)
INSERT INTO banking.categories (name, parent_id, code)
VALUES ('Travel', (SELECT id FROM banking.categories WHERE code = 'TRANSP'), 'TRANSP.TRAVEL')
ON CONFLICT (code) DO NOTHING;
