create schema if not exists investments;

create table if not exists investments.investment_types (
    id bigserial primary key,
    code varchar(32) not null unique,
    name varchar(120) not null
);

create table if not exists investments.investment_platforms (
    id bigserial primary key,
    code varchar(32) not null unique,
    name varchar(120) not null
);

create table if not exists investments.investment_instruments (
    id bigserial primary key,
    type_id bigint not null,
    symbol varchar(50) not null,
    name varchar(150) not null,
    market varchar(80),
    currency varchar(3) not null,
    last_price numeric(18,10),
    last_price_source varchar(50),
    last_price_at timestamp,
    constraint fk_instruments_type foreign key (type_id) references investments.investment_types(id),
    constraint uq_instruments_type_symbol_market unique (type_id, symbol, market)
);

create table if not exists investments.prices (
    id bigserial primary key,
    instrument_id bigint not null,
    price numeric(18,10) not null,
    source varchar(50),
    as_of timestamp not null,
    currency varchar(3),
    constraint fk_prices_instrument foreign key (instrument_id) references investments.investment_instruments(id)
);

create table if not exists investments.investments (
    id bigserial primary key,
    tenant_id bigint not null,
    type_id bigint not null,
    name varchar(150) not null,
    instrument_id bigint not null,
    platform_id bigint,
    currency varchar(3) not null,
    invested_amount numeric(18,2) not null,
    current_value_manual numeric(18,2),
    current_value_calculated numeric(18,2),
    quantity numeric(28,10),
    opened_at date,
    notes varchar(1000),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_investments_type foreign key (type_id) references investments.investment_types(id),
    constraint fk_investments_instrument foreign key (instrument_id) references investments.investment_instruments(id),
    constraint fk_investments_platform foreign key (platform_id) references investments.investment_platforms(id)
);

create index if not exists idx_investments_tenant_id on investments.investments(tenant_id);
create index if not exists idx_investments_tenant_type on investments.investments(tenant_id, type_id);
create index if not exists idx_investments_tenant_instrument on investments.investments(tenant_id, instrument_id);
create index if not exists idx_investments_tenant_platform on investments.investments(tenant_id, platform_id);
create index if not exists idx_instruments_type on investments.investment_instruments(type_id);
create index if not exists idx_prices_instrument on investments.prices(instrument_id);
create index if not exists idx_prices_as_of on investments.prices(as_of);

insert into investments.investment_types (code, name)
values
    ('FUND', 'Fondo'),
    ('ETF', 'ETF'),
    ('CRYPTO', 'Cripto'),
    ('STOCK', 'Accion')
on conflict (code) do nothing;

insert into investments.investment_platforms (code, name)
values
    ('BROKER', 'Broker'),
    ('BANK', 'Banco'),
    ('EXCHANGE', 'Exchange'),
    ('WALLET', 'Wallet')
on conflict (code) do nothing;

insert into investments.investment_instruments (type_id, symbol, name, market, currency)
select t.id, x.symbol, x.name, x.market, x.currency
from (values
    ('ETF', 'VWCE', 'Vanguard FTSE All-World UCITS ETF', 'XETRA', 'EUR'),
    ('STOCK', 'AAPL', 'Apple Inc.', 'NASDAQ', 'USD'),
    ('CRYPTO', 'BTC', 'Bitcoin', 'CRYPTO', 'USD'),
    ('FUND', 'AMUNDI-MSCI-WORLD', 'Amundi MSCI World', 'FUND', 'EUR')
) as x(type_code, symbol, name, market, currency)
join investments.investment_types t on t.code = x.type_code
on conflict (type_id, symbol, market) do nothing;
