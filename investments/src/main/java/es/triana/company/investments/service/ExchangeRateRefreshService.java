package es.triana.company.investments.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.client.EcbExchangeRateClient;
import es.triana.company.investments.model.db.ExchangeRate;
import es.triana.company.investments.repository.ExchangeRateRepository;

@Service
public class ExchangeRateRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangeRateRefreshService.class);
    private static final String BASE_CURRENCY = "EUR";

    private final EcbExchangeRateClient ecbClient;
    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateRefreshService(EcbExchangeRateClient ecbClient,
                                      ExchangeRateRepository exchangeRateRepository) {
        this.ecbClient = ecbClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * On startup: verify business-day coverage for the last 90 days and fill gaps.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(89);

        List<LocalDate> existingDates = exchangeRateRepository.findDistinctAsOfByFromCurrencyBetween(BASE_CURRENCY, fromDate, toDate);

        Set<LocalDate> existingSet = new HashSet<>(existingDates);
        List<LocalDate> missingBusinessDays = new ArrayList<>();

        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            boolean isBusinessDay = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            if (isBusinessDay && !existingSet.contains(cursor)) {
                missingBusinessDays.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        if (missingBusinessDays.isEmpty()) {
            LOG.info("Exchange rates startup check complete — no missing business days in last 90 days");
            return;
        }

        LOG.info("Exchange rates startup check found {} missing business days in last 90 days — backfilling",
                missingBusinessDays.size());

        List<ExchangeRate> rates = ecbClient.fetchLast90DaysRates().stream()
                .filter(rate -> missingBusinessDays.contains(rate.getAsOf()))
                .toList();

        int saved = upsertRates(rates);

        Set<LocalDate> recoveredDays = new HashSet<>(rates.stream().map(ExchangeRate::getAsOf).toList());
        int unresolved = (int) missingBusinessDays.stream().filter(d -> !recoveredDays.contains(d)).count();

        LOG.info("Exchange rates startup backfill finished — {} rates upserted, {} business days unresolved",
                saved, unresolved);
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
     * Loads rates for one specific day from the ECB 90-day feed and upserts them.
     */
    @Transactional
    public int refreshRatesForDate(LocalDate asOf) {
        LOG.info("Exchange rate refresh started (specific day: {})", asOf);
        List<ExchangeRate> rates = ecbClient.fetchRatesForDate(asOf);
        if (rates.isEmpty()) {
            LOG.warn("No ECB rates found for {} (outside feed range or no publication day)", asOf);
            return 0;
        }
        int saved = upsertRates(rates);
        LOG.info("Exchange rate refresh finished for {} — {} rates upserted", asOf, saved);
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
