INSERT INTO banking.transaction_statuses (code, created_at, updated_at) VALUES
 ('BOOKED', NOW(), NOW()),
 ('PENDING', NOW(), NOW()),
 ('CANCELLED', NOW(), NOW()),
 ('REJECTED', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.transaction_types (name, description, created_at, updated_at) VALUES
 ('EXPENSE', 'Transaction representing an expense or debit', NOW(), NOW()),
 ('INCOME', 'Transaction representing an income or credit', NOW(), NOW()),
 ('TRANSFER', 'Transaction representing a transfer between accounts', NOW(), NOW()),
 ('ADJUSTMENT', 'Transaction representing an adjustment or correction', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO banking.merchants (name, created_at, updated_at) VALUES
 ('Amazon', NOW(), NOW()),
 ('Mercadona', NOW(), NOW()),
 ('Carrefour', NOW(), NOW()),
 ('DIA', NOW(), NOW()),
 ('Others', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO banking.categories (name, code, created_at, updated_at) VALUES
 ('Feeding', 'FOOD', NOW(), NOW()),
 ('Purchases', 'PURCH', NOW(), NOW()),
 ('Entertainment', 'ENT', NOW(), NOW()),
 ('Subscriptions', 'SUBSCR', NOW(), NOW()),
 ('Healthcare', 'HEALTH', NOW(), NOW()),
 ('Transportation', 'TRANSP', NOW(), NOW()),
 ('Dwelling', 'DWELL', NOW(), NOW()),
 ('Insurance', 'INSUR', NOW(), NOW()),
 ('Taxes', 'TAXES', NOW(), NOW()),
 ('Commissions', 'COMM', NOW(), NOW()),
 ('Education', 'EDU', NOW(), NOW()),
 ('Donations', 'DON', NOW(), NOW()),
 ('Income', 'INC', NOW(), NOW()),
 ('Savings/Investments', 'SAV', NOW(), NOW()),
 ('Internal transfers', 'INT', NOW(), NOW()),
 ('Adjustments', 'ADJ', NOW(), NOW()),
 ('Others', 'OTH', NOW(), NOW())
ON CONFLICT (code) DO NOTHING;


INSERT INTO banking.categories (name, parent_id, code) VALUES
('Supermarket',(SELECT id FROM banking.categories WHERE code='FOOD'),'FOOD.SUP'),
('Restaurants',(SELECT id FROM banking.categories WHERE code='FOOD'),'FOOD.REST'),
('Food delivery',(SELECT id FROM banking.categories WHERE code='FOOD'),'FOOD.DEL'),
('Others',(SELECT id FROM banking.categories WHERE code='FOOD'),'FOOD.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Clothing', (SELECT id FROM banking.categories WHERE code='PURCH'),'PURCH.CLOTH'),
('Electronics', (SELECT id FROM banking.categories WHERE code='PURCH'),'PURCH.ELEC'),
('Home', (SELECT id FROM banking.categories WHERE code='PURCH'),'PURCH.HOME'),
('Books', (SELECT id FROM banking.categories WHERE code='PURCH'),'PURCH.BOOK'),
('Gifts', (SELECT id FROM banking.categories WHERE code='PURCH'),'PURCH.GIFT'),
('Others', (SELECT id FROM banking.categories WHERE code='PURCH'),'PURCH.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Multimedia',(SELECT id FROM banking.categories WHERE code='ENT'),'ENT.MOV'),
('Concerts',(SELECT id FROM banking.categories WHERE code='ENT'),'ENT.CONC'),
('Music',(SELECT id FROM banking.categories WHERE code='ENT'),'ENT.MUS'),
('Games',(SELECT id FROM banking.categories WHERE code='ENT'),'ENT.GAME'),
('Others',(SELECT id FROM banking.categories WHERE code='ENT'),'ENT.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('PC', (SELECT id FROM banking.categories WHERE code='ENT.GAME'),'ENT.GAME.PC'),
('Mobile',(SELECT id FROM banking.categories WHERE code='ENT.GAME'),'ENT.GAME.MOB'),
('Nintendo Switch',(SELECT id FROM banking.categories WHERE code='ENT.GAME'),'ENT.GAME.SWITCH'),
('Others',(SELECT id FROM banking.categories WHERE code='ENT.GAME'),'ENT.GAME.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Streaming',(SELECT id FROM banking.categories WHERE code='SUBSCR'),'SUBSCR.STREAM'),
('Software',(SELECT id FROM banking.categories WHERE code='SUBSCR'),'SUBSCR.SOFT'),
('Others',(SELECT id FROM banking.categories WHERE code='SUBSCR'),'SUBSCR.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Medical',(SELECT id FROM banking.categories WHERE code='HEALTH'),'HEALTH.MED'),
('Pharmacy',(SELECT id FROM banking.categories WHERE code='HEALTH'),'HEALTH.PHARM'),
('Gym',(SELECT id FROM banking.categories WHERE code='HEALTH'),'HEALTH.GYM'),
('Others',(SELECT id FROM banking.categories WHERE code='HEALTH'),'HEALTH.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Public transport',(SELECT id FROM banking.categories WHERE code='TRANSP'),'TRANSP.PUBLIC'),
('Taxi',(SELECT id FROM banking.categories WHERE code='TRANSP'),'TRANSP.TAXI'),
('Fuel',(SELECT id FROM banking.categories WHERE code='TRANSP'),'TRANSP.FUEL'),
('Vehicle maintenance',(SELECT id FROM banking.categories WHERE code='TRANSP'),'TRANSP.MAINT'),
('Others',(SELECT id FROM banking.categories WHERE code='TRANSP'),'TRANSP.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Rent',(SELECT id FROM banking.categories WHERE code='DWELL'),'DWELL.RENT'),
('Utilities',(SELECT id FROM banking.categories WHERE code='DWELL'),'DWELL.UTIL'),
('Maintenance',(SELECT id FROM banking.categories WHERE code='DWELL'),'DWELL.MAINT'),
('Others',(SELECT id FROM banking.categories WHERE code='DWELL'),'DWELL.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Life insurance',(SELECT id FROM banking.categories WHERE code='INSUR'),'INSUR.LIFE'),
('Vehicle insurance',(SELECT id FROM banking.categories WHERE code='INSUR'),'INSUR.VEH'),
('Home insurance',(SELECT id FROM banking.categories WHERE code='INSUR'),'INSUR.HOME'),
('Others',(SELECT id FROM banking.categories WHERE code='INSUR'),'INSUR.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('IRPF',(SELECT id FROM banking.categories WHERE code='TAXES'),'TAXES.STATE'),
('Rates',(SELECT id FROM banking.categories WHERE code='TAXES'),'TAXES.FED'),
('Others',(SELECT id FROM banking.categories WHERE code='TAXES'),'TAXES.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Bank commissions',(SELECT id FROM banking.categories WHERE code='COMM'),'COMM.BANK'),
('Investment commissions',(SELECT id FROM banking.categories WHERE code='COMM'),'COMM.INVEST'),
('Others',(SELECT id FROM banking.categories WHERE code='COMM'),'COMM.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Courses',(SELECT id FROM banking.categories WHERE code='EDU'),'EDU.COURSE'),
('Books',(SELECT id FROM banking.categories WHERE code='EDU'),'EDU.BOOK'),
('Certifications',(SELECT id FROM banking.categories WHERE code='EDU'),'EDU.CERT'),
('Others',(SELECT id FROM banking.categories WHERE code='EDU'),'EDU.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Charity',(SELECT id FROM banking.categories WHERE code='DON'),'DON.CHAR'),
('Others',(SELECT id FROM banking.categories WHERE code='DON'),'DON.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Salary',(SELECT id FROM banking.categories WHERE code='INC'),'INC.SAL'),
('Bonuses',(SELECT id FROM banking.categories WHERE code='INC'),'INC.BON'),
('Dividends',(SELECT id FROM banking.categories WHERE code='INC'),'INC.DIV'),
('Rent',(SELECT id FROM banking.categories WHERE code='INC'),'INC.RENT'),
('Investments',(SELECT id FROM banking.categories WHERE code='INC'),'INC.INVEST'),
('Second hand sellings',(SELECT id FROM banking.categories WHERE code='INC'),'INC.SELL'),
('Cashbacks',(SELECT id FROM banking.categories WHERE code='INC'),'INC.CASH'),
('Others',(SELECT id FROM banking.categories WHERE code='INC'),'INC.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.categories (name, parent_id, code) VALUES
('Fixed deposits',(SELECT id FROM banking.categories WHERE code='SAV'),'SAV.FIXED'),
('Stocks',(SELECT id FROM banking.categories WHERE code='SAV'),'SAV.STOCKS'),
('Bonds',(SELECT id FROM banking.categories WHERE code='SAV'),'SAV.BONDS'),
('ETFs',(SELECT id FROM banking.categories WHERE code='SAV'),'SAV.ETFS'),
('Funds',(SELECT id FROM banking.categories WHERE code='SAV'),'SAV.FUNDS'),
('Cryptocurrencies',(SELECT id FROM banking.categories WHERE code='SAV'),'SAV.CRYPTO'),
('Others',(SELECT id FROM banking.categories WHERE code='SAV'),'SAV.OTH')
ON CONFLICT (code) DO NOTHING;

INSERT INTO banking.tenants (name, created_at, updated_at) VALUES
 ('Default Tenant', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO banking.account_types (name, description, created_at, updated_at) VALUES
 ('CHECKING', 'Standard checking account', NOW(), NOW()),
 ('SAVINGS', 'Standard savings account', NOW(), NOW()),
 ('INTEREST-BEARING', 'Interest-bearing account', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

INSERT INTO banking.institutions (name, country, website, created_at, updated_at) VALUES
 ('Santander', 'ES', 'https://www.santander.com', NOW(), NOW()),
 ('BBVA', 'ES', 'https://www.bbva.com', NOW(), NOW()),
 ('Imagin', 'ES', 'https://www.caixabank.com', NOW(), NOW()),
 ('ING', 'NL', 'https://www.ing.com', NOW(), NOW()),
 ('Interactive Brokers', 'US', 'https://www.interactivebrokers.com', NOW(), NOW()),
 ('Revolut', 'GB', 'https://www.revolut.com', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;