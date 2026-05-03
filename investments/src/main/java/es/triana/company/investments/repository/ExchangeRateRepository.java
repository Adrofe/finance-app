package es.triana.company.investments.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.investments.model.db.ExchangeRate;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findFirstByFromCurrencyAndToCurrencyOrderByAsOfDesc(
            String fromCurrency, String toCurrency);

    Optional<ExchangeRate> findByFromCurrencyAndToCurrencyAndAsOf(
            String fromCurrency, String toCurrency, LocalDate asOf);

    List<ExchangeRate> findByFromCurrencyAndToCurrencyAndAsOfBetweenOrderByAsOfAsc(
            String fromCurrency, String toCurrency, LocalDate from, LocalDate to);

    @Query("""
            SELECT DISTINCT e.asOf
            FROM ExchangeRate e
            WHERE e.fromCurrency = :fromCurrency
              AND e.asOf BETWEEN :fromDate AND :toDate
            ORDER BY e.asOf ASC
            """)
    List<LocalDate> findDistinctAsOfByFromCurrencyBetween(
            @Param("fromCurrency") String fromCurrency,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
