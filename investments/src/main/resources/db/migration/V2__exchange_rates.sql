create table if not exists investments.exchange_rates (
    id          bigserial primary key,
    from_currency varchar(3) not null,
    to_currency   varchar(3) not null,
    rate          numeric(18, 10) not null,
    source        varchar(50),
    as_of         date not null,
    constraint uq_exchange_rates_pair_date unique (from_currency, to_currency, as_of)
);

create index if not exists idx_exchange_rates_pair_date on investments.exchange_rates(from_currency, to_currency, as_of);
create index if not exists idx_exchange_rates_as_of on investments.exchange_rates(as_of);
