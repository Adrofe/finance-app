package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.repository.ExchangeRateRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class EurConversionService {

    public static final String EUR = "EUR";

    private final ExchangeRateRepository exchangeRateRepository;

    public EurConversionService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /** Converts using the latest stored EUR->currency rate (units of currency per 1 EUR). */
    @Transactional(readOnly = true)
    public BigDecimal toEur(BigDecimal amount, String currency) {
        if (amount == null || currency == null || EUR.equalsIgnoreCase(currency.trim())) {
            return amount;
        }
        String normalized = currency.trim().toUpperCase();
        BigDecimal rate = exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyOrderByAsOfDesc(EUR, normalized)
                .map(r -> r.getRate())
                .filter(r -> r.signum() > 0)
                .orElseThrow(() -> new InvestmentValidationException(
                        "No EUR/" + normalized + " exchange rate available. Populate exchange_rates first."));
        return amount.divide(rate, 10, RoundingMode.HALF_UP);
    }

    /** Converts EUR into {@code currency} with the rate in force on {@code date} (latest earlier rate). */
    @Transactional(readOnly = true)
    public BigDecimal fromEur(BigDecimal eurAmount, String currency, LocalDate date) {
        if (eurAmount == null || currency == null || EUR.equalsIgnoreCase(currency.trim())) {
            return eurAmount;
        }
        String normalized = currency.trim().toUpperCase();
        BigDecimal rate = exchangeRateRepository
                .findFirstByFromCurrencyAndToCurrencyAndAsOfLessThanEqualOrderByAsOfDesc(EUR, normalized, date)
                .or(() -> exchangeRateRepository.findFirstByFromCurrencyAndToCurrencyOrderByAsOfDesc(EUR, normalized))
                .map(r -> r.getRate())
                .filter(r -> r.signum() > 0)
                .orElseThrow(() -> new InvestmentValidationException(
                        "No EUR/" + normalized + " exchange rate available. Populate exchange_rates first."));
        return eurAmount.multiply(rate).setScale(10, RoundingMode.HALF_UP);
    }
}
