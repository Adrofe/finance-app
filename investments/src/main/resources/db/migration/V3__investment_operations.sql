-- Each buy or sell operation for an investment position
create table if not exists investments.investment_operations (
    id                  bigserial primary key,
    investment_id       bigint not null,
    tenant_id           bigint not null,
    type                varchar(4) not null,              -- BUY | SELL
    operation_date      date not null,
    quantity            numeric(28,10) not null,
    unit_price          numeric(18,10) not null,          -- in operation currency
    fees                numeric(18,4) not null default 0,
    total_amount        numeric(18,4) not null,           -- quantity * unit_price + fees (BUY) or - fees (SELL)
    currency            varchar(3) not null,
    -- Denormalised ECB rate at operation_date so fiscal records survive even if
    -- exchange_rates history is purged later. 1 if currency already EUR.
    eur_exchange_rate   numeric(18,10) not null default 1,
    total_amount_eur    numeric(18,4) not null,           -- total_amount / eur_exchange_rate
    notes               varchar(500),
    created_at          timestamp not null,
    updated_at          timestamp not null,
    constraint fk_operations_investment foreign key (investment_id)
        references investments.investments(id),
    constraint chk_operations_type check (type in ('BUY','SELL')),
    constraint chk_operations_quantity check (quantity > 0),
    constraint chk_operations_unit_price check (unit_price > 0)
);

create index if not exists idx_operations_investment   on investments.investment_operations(investment_id);
create index if not exists idx_operations_tenant       on investments.investment_operations(tenant_id);
create index if not exists idx_operations_date         on investments.investment_operations(operation_date);
create index if not exists idx_operations_tenant_type  on investments.investment_operations(tenant_id, type);

-- FIFO matching: maps each sell lot to one or more buy lots that cover it
create table if not exists investments.operation_fifo_lots (
    id                  bigserial primary key,
    sell_operation_id   bigint not null,
    buy_operation_id    bigint not null,
    quantity            numeric(28,10) not null,          -- fraction of the buy lot consumed by this sell
    buy_unit_price_eur  numeric(18,10) not null,
    sell_unit_price_eur numeric(18,10) not null,
    gain_loss_eur       numeric(18,4) not null,           -- (sell - buy) * quantity; negative = loss
    constraint fk_fifo_sell foreign key (sell_operation_id)
        references investments.investment_operations(id),
    constraint fk_fifo_buy  foreign key (buy_operation_id)
        references investments.investment_operations(id),
    constraint uq_fifo_sell_buy unique (sell_operation_id, buy_operation_id)
);

create index if not exists idx_fifo_sell on investments.operation_fifo_lots(sell_operation_id);
create index if not exists idx_fifo_buy  on investments.operation_fifo_lots(buy_operation_id);
