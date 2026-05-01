package es.triana.company.investments.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.ExchangeRate;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findFirstByFromCurrencyAndToCurrencyOrderByAsOfDesc(
            String fromCurrency, String toCurrency);

    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndAsOf(
            String fromCurrency, String toCurrency, LocalDate asOf);

    List<ExchangeRate> findByFromCurrencyAndToCurrencyAndAsOfBetweenOrderByAsOfAsc(
            String fromCurrency, String toCurrency, LocalDate from, LocalDate to);
}
