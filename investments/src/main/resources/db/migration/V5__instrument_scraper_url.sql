alter table investments.investment_instruments
    add column if not exists scraper_url varchar(500);

create index if not exists idx_instruments_scraper_url
    on investments.investment_instruments(scraper_url);
