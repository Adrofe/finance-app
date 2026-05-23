package es.triana.company.investments.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.model.api.ExchangeRateDTO;
import es.triana.company.investments.model.api.ExchangeRateUpsertRequestDTO;
import es.triana.company.investments.model.db.ExchangeRate;
import es.triana.company.investments.repository.ExchangeRateRepository;

@Service
public class ExchangeRateService {

    private static final String SOURCE_MANUAL = "MANUAL";

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Transactional(readOnly = true)
    public List<ExchangeRateDTO> findHistoricalRates(String fromCurrency, String toCurrency, LocalDate from, LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(180);

        String fromCurr = normalizeCurrency(fromCurrency, "EUR");
        String toCurr = normalizeCurrency(toCurrency, "USD");

        return exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndAsOfBetweenOrderByAsOfAsc(fromCurr, toCurr, fromDate, toDate)
                .stream()
                .sorted(Comparator.comparing(ExchangeRate::getAsOf).reversed())
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ExchangeRateDTO upsertManualRate(ExchangeRateUpsertRequestDTO req) {
        String fromCurr = normalizeCurrency(req.fromCurrency(), "EUR");
        String toCurr = normalizeCurrency(req.toCurrency(), "USD");
        String source = req.source() == null || req.source().isBlank()
                ? SOURCE_MANUAL
                : req.source().trim().toUpperCase(Locale.ROOT);

        ExchangeRate saved = exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndAsOf(fromCurr, toCurr, req.asOf())
                .map(existing -> {
                    existing.setRate(req.rate());
                    existing.setSource(source);
                    return exchangeRateRepository.save(existing);
                })
                .orElseGet(() -> exchangeRateRepository.save(ExchangeRate.builder()
                        .fromCurrency(fromCurr)
                        .toCurrency(toCurr)
                        .rate(req.rate())
                        .source(source)
                        .asOf(req.asOf())
                        .build()));

        return toDto(saved);
    }

    private ExchangeRateDTO toDto(ExchangeRate rate) {
        return new ExchangeRateDTO(
                rate.getId(),
                rate.getFromCurrency(),
                rate.getToCurrency(),
                rate.getRate(),
                rate.getSource(),
                rate.getAsOf());
    }

    private String normalizeCurrency(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
