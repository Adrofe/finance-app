package es.triana.company.investments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import es.triana.company.investments.model.db.ExchangeRate;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentOperation;
import es.triana.company.investments.model.db.InvestmentPrice;
import es.triana.company.investments.model.db.OperationType;
import es.triana.company.investments.repository.ExchangeRateRepository;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentOperationRepository;
import es.triana.company.investments.repository.InvestmentPriceRepository;

@ExtendWith(MockitoExtension.class)
class InstrumentHistoryServiceTest {

    @Mock private InvestmentInstrumentRepository instrumentRepository;
    @Mock private InvestmentPriceRepository priceRepository;
    @Mock private InvestmentOperationRepository operationRepository;
    @Mock private ExchangeRateRepository exchangeRateRepository;

    @Test
    void getHistoryUsesLatestPricePerDayAndReconstructsTenantPositionInEur() {
        Long instrumentId = 3L;
        Long tenantId = 12L;
        LocalDate dayOne = LocalDate.of(2026, 1, 5);
        LocalDate dayTwo = dayOne.plusDays(1);
        InvestmentInstrument instrument = InvestmentInstrument.builder()
                .id(instrumentId).symbol("ABC").name("Example").currency("USD").build();
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(instrument));
        when(operationRepository.findByInstrumentAndTenantOrderByOperationDateAscIdAsc(instrumentId, tenantId))
                .thenReturn(List.of(
                        operation(1L, dayOne, OperationType.BUY, "10", "100"),
                        operation(2L, dayTwo, OperationType.SELL, "4", "60")));
        when(priceRepository.findFirstByInstrumentIdAndAsOfBeforeOrderByAsOfDescIdDesc(
                instrumentId, dayOne.atStartOfDay())).thenReturn(Optional.empty());
        when(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
                instrumentId, dayOne.atStartOfDay(), dayTwo.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(
                        price(1L, dayOne.atTime(10, 0), "12", "USD"),
                        price(2L, dayOne.atTime(17, 0), "13", "USD"),
                        price(3L, dayTwo.atTime(17, 0), "14", "USD")));
        when(exchangeRateRepository.findFirstByFromCurrencyAndToCurrencyAndAsOfLessThanEqualOrderByAsOfDesc(
                "EUR", "USD", dayOne)).thenReturn(Optional.of(rate(dayOne.minusDays(1), "2")));
        when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndAsOfBetweenOrderByAsOfAsc(
                "EUR", "USD", dayOne, dayTwo)).thenReturn(List.of(rate(dayTwo, "2")));

        InstrumentHistoryService service = new InstrumentHistoryService(
                instrumentRepository, priceRepository, operationRepository, exchangeRateRepository);
        var result = service.getHistory(tenantId, instrumentId, dayOne, dayTwo);

        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0).price()).isEqualByComparingTo("13");
        assertThat(result.points().get(0).quantity()).isEqualByComparingTo("10");
        assertThat(result.points().get(0).investedEur()).isEqualByComparingTo("100.00");
        assertThat(result.points().get(0).valueEur()).isEqualByComparingTo("65.00");
        assertThat(result.points().get(1).quantity()).isEqualByComparingTo("6");
        assertThat(result.points().get(1).investedEur()).isEqualByComparingTo("60.00");
        assertThat(result.points().get(1).valueEur()).isEqualByComparingTo("42.00");
        verify(operationRepository).findByInstrumentAndTenantOrderByOperationDateAscIdAsc(instrumentId, tenantId);
    }

    @Test
    void showsBackdatedSaleOnItsDateUsingTheLatestKnownPrice() {
        Long instrumentId = 3L;
        Long tenantId = 12L;
        LocalDate start = LocalDate.of(2026, 1, 5);
        LocalDate saleDate = start.plusDays(2);
        LocalDate end = start.plusDays(5);
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(InvestmentInstrument.builder()
                .id(instrumentId).symbol("BTC").name("Bitcoin").currency("EUR").build()));
        when(operationRepository.findByInstrumentAndTenantOrderByOperationDateAscIdAsc(instrumentId, tenantId))
                .thenReturn(List.of(operation(1L, start.minusDays(2), OperationType.BUY, "10", "100"),
                        operation(2L, saleDate, OperationType.SELL, "4", "60")));
        when(priceRepository.findFirstByInstrumentIdAndAsOfBeforeOrderByAsOfDescIdDesc(instrumentId, start.atStartOfDay()))
                .thenReturn(Optional.of(price(1L, start.minusDays(1).atTime(10, 0), "12", "EUR")));
        when(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(instrumentId, start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(price(2L, end.atTime(10, 0), "15", "EUR")));

        var points = new InstrumentHistoryService(instrumentRepository, priceRepository, operationRepository, exchangeRateRepository)
                .getHistory(tenantId, instrumentId, start, end).points();
        assertThat(points).hasSize(3);
        assertThat(points.get(0).date()).isEqualTo(start);
        assertThat(points.get(0).valueEur()).isEqualByComparingTo("120.00");
        assertThat(points.get(1).date()).isEqualTo(saleDate);
        assertThat(points.get(1).quantity()).isEqualByComparingTo("6");
        assertThat(points.get(1).valueEur()).isEqualByComparingTo("72.00");
        assertThat(points.get(2).valueEur()).isEqualByComparingTo("90.00");
    }

    private InvestmentOperation operation(Long id, LocalDate date, OperationType type, String quantity, String totalEur) {
        return InvestmentOperation.builder()
                .id(id).operationDate(date).type(type)
                .quantity(new BigDecimal(quantity)).totalAmountEur(new BigDecimal(totalEur)).build();
    }

    private InvestmentPrice price(Long id, LocalDateTime asOf, String value, String currency) {
        return InvestmentPrice.builder()
                .id(id).instrumentId(3L).asOf(asOf)
                .price(new BigDecimal(value)).currency(currency).build();
    }

    private ExchangeRate rate(LocalDate date, String value) {
        return ExchangeRate.builder().fromCurrency("EUR").toCurrency("USD")
                .asOf(date).rate(new BigDecimal(value)).build();
    }
}