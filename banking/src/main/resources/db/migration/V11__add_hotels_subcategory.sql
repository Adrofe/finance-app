-- Add a dedicated Travel category, separated from day-to-day Transportation
INSERT INTO banking.categories (name, code, created_at, updated_at)
VALUES ('Travel', 'TRAVEL', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code)
VALUES
	('Flights',           (SELECT id FROM banking.categories WHERE code = 'TRAVEL'), 'TRAVEL.FLIGHT'),
	('Hotels',            (SELECT id FROM banking.categories WHERE code = 'TRAVEL'), 'TRAVEL.HOTEL'),
	('Trains and ferries',(SELECT id FROM banking.categories WHERE code = 'TRAVEL'), 'TRAVEL.TRANSIT'),
	('Car rental',        (SELECT id FROM banking.categories WHERE code = 'TRAVEL'), 'TRAVEL.CAR'),
	('Others',            (SELECT id FROM banking.categories WHERE code = 'TRAVEL'), 'TRAVEL.OTH')
ON CONFLICT (code) DO NOTHING;

-- Booking.com defaults to Hotels when imported or created
INSERT INTO banking.merchants (name, category_id, created_at, updated_at)
VALUES ('Booking.com', (SELECT id FROM banking.categories WHERE code = 'TRAVEL.HOTEL'), NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
