package es.triana.company.investments.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.model.db.ExchangeRate;
import es.triana.company.investments.repository.ExchangeRateRepository;

@Service
public class ExchangeRateRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeRateRefreshService.class);

    private final EcbExchangeRateClient ecbClient;
    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateRefreshService(EcbExchangeRateClient ecbClient,
                                      ExchangeRateRepository exchangeRateRepository) {
        this.ecbClient = ecbClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * On startup: if the exchange_rates table is empty, load the last 90 days
     * so historical valuations are available immediately.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        if (exchangeRateRepository.count() == 0) {
            LOG.info("Exchange rates table is empty — loading last 90 days from ECB on startup");
            refreshLast90DaysRates();
        } else {
            LOG.debug("Exchange rates already present — skipping historical load on startup");
        }
    }

    /**
     * Daily refresh: runs Monday–Friday at 16:30 CET (15:30 UTC).
     * The ECB publishes reference rates around 16:00 CET on business days.
     * Configurable via INVESTMENTS_EXCHANGE_RATES_REFRESH_CRON env var.
     */
    @Scheduled(cron = "${investments.exchange-rates.refresh-cron:0 30 15 * * MON-FRI}")
    @Transactional
    public void scheduledDailyRefresh() {
        LOG.info("Exchange rate refresh started (scheduled)");
        List<ExchangeRate> rates = ecbClient.fetchDailyRates();
        int saved = upsertRates(rates);
        LOG.info("Exchange rate refresh finished — {} rates upserted", saved);
    }

    /**
     * On-demand refresh of today's rates. Callable from controller or on startup.
     */
    @Transactional
    public int refreshTodayRates() {
        LOG.info("Exchange rate refresh started (on-demand, today)");
        List<ExchangeRate> rates = ecbClient.fetchDailyRates();
        int saved = upsertRates(rates);
        LOG.info("Exchange rate refresh finished — {} rates upserted", saved);
        return saved;
    }

    /**
     * Loads the last 90 days of historical rates. Useful to populate the DB on
     * first startup so that historical position valuations are immediately available.
     */
    @Transactional
    public int refreshLast90DaysRates() {
        LOG.info("Exchange rate historical refresh started (last 90 days)");
        List<ExchangeRate> rates = ecbClient.fetchLast90DaysRates();
        int saved = upsertRates(rates);
        LOG.info("Exchange rate historical refresh finished — {} rates upserted", saved);
        return saved;
    }

    /**
     * Upserts a list of exchange rates. If a record already exists for the same
     * (from_currency, to_currency, as_of), it is updated; otherwise it is inserted.
     */
    private int upsertRates(List<ExchangeRate> rates) {
        int count = 0;
        for (ExchangeRate incoming : rates) {
            try {
                exchangeRateRepository
                        .findByFromCurrencyAndToCurrencyAndAsOf(
                                incoming.getFromCurrency(),
                                incoming.getToCurrency(),
                                incoming.getAsOf())
                        .ifPresentOrElse(
                                existing -> {
                                    existing.setRate(incoming.getRate());
                                    existing.setSource(incoming.getSource());
                                    exchangeRateRepository.save(existing);
                                },
                                () -> exchangeRateRepository.save(incoming));
                count++;
            } catch (Exception e) {
                LOG.warn("Could not upsert exchange rate {}/{} on {}: {}",
                        incoming.getFromCurrency(), incoming.getToCurrency(),
                        incoming.getAsOf(), e.getMessage());
            }
        }
        return count;
    }
}
