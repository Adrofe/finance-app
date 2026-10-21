package es.triana.company.investments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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

import es.triana.company.investments.client.MarketPriceClient;
import es.triana.company.investments.model.api.HistoricalPriceUpdateRequestDTO;
import es.triana.company.investments.model.api.PriceQuotePreviewDTO;
import es.triana.company.investments.model.api.PriceUpdateRequestDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentPrice;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentPriceRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;

@ExtendWith(MockitoExtension.class)
class PriceRefreshServiceTest {

    @Mock private InvestmentInstrumentRepository instrumentRepository;
    @Mock private InvestmentPriceRepository priceRepository;
    @Mock private InvestmentRepository investmentRepository;
    @Mock private InvestmentTypeCatalogRepository typeCatalogRepository;
    @Mock private MarketPriceClient marketPriceClient;
    @Mock private EurConversionService eurConversionService;

    @Test
    void updateHistoricalPriceDoesNotChangeCurrentPriceForOlderDate() {
        Long instrumentId = 4L;
        LocalDate editedDate = LocalDate.of(2026, 10, 10);
        InvestmentInstrument instrument = instrument(instrumentId);
        InvestmentPrice edited = price(20L, editedDate.atTime(12, 0), "262");
        InvestmentPrice latest = price(21L, editedDate.plusDays(1).atTime(12, 0), "9.12");
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(instrument));
        when(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
                instrumentId, editedDate.atStartOfDay(), editedDate.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(edited));
        when(priceRepository.findFirstByInstrumentIdOrderByAsOfDescIdDesc(instrumentId)).thenReturn(Optional.of(latest));

        PriceRefreshService service = service();
        var result = service.updateHistoricalPrice(new HistoricalPriceUpdateRequestDTO(
                instrumentId, editedDate, new BigDecimal("8.74")));

        assertThat(edited.getPrice()).isEqualByComparingTo("8.74");
        assertThat(edited.getSource()).isEqualTo("MANUAL_UI");
        assertThat(result.getRecalculatedPositions()).isZero();
        verify(priceRepository).save(edited);
        verify(instrumentRepository, never()).save(instrument);
        verify(investmentRepository, never()).findByInstrumentId(instrumentId);
    }

    @Test
    void updateHistoricalPriceRecalculatesPositionsWhenEditingLatestDate() {
        Long instrumentId = 4L;
        LocalDate latestDate = LocalDate.of(2026, 10, 20);
        InvestmentInstrument instrument = instrument(instrumentId);
        InvestmentPrice latest = price(20L, latestDate.atTime(12, 0), "262");
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(instrument));
        when(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
                instrumentId, latestDate.atStartOfDay(), latestDate.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(latest));
        when(priceRepository.findFirstByInstrumentIdOrderByAsOfDescIdDesc(instrumentId)).thenReturn(Optional.of(latest));
        when(investmentRepository.findByInstrumentId(instrumentId)).thenReturn(List.of());

        PriceRefreshService service = service();
        var result = service.updateHistoricalPrice(new HistoricalPriceUpdateRequestDTO(
                instrumentId, latestDate, new BigDecimal("8.74")));

        assertThat(instrument.getLastPrice()).isEqualByComparingTo("8.74");
        assertThat(instrument.getLastPriceSource()).isEqualTo("MANUAL_UI");
        assertThat(result.getRecalculatedPositions()).isZero();
        verify(instrumentRepository).save(instrument);
        verify(investmentRepository).findByInstrumentId(instrumentId);
    }

    @Test
    void previewInstrumentPriceReturnsQuoteWithoutPersistingIt() {
        Long instrumentId = 4L;
        InvestmentInstrument instrument = instrument(instrumentId);
        MarketPriceClient.MarketQuote quote = new MarketPriceClient.MarketQuote(
                new BigDecimal("8.74"), "FINECT", "EUR");
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(instrument));
        when(marketPriceClient.fetchLatestQuote(instrument)).thenReturn(Optional.of(quote));

        PriceQuotePreviewDTO result = service().previewInstrumentPrice(instrumentId);

        assertThat(result.price()).isEqualByComparingTo("8.74");
        assertThat(result.source()).isEqualTo("FINECT");
        assertThat(result.currency()).isEqualTo("EUR");
        verify(priceRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(instrumentRepository, never()).save(instrument);
    }

    @Test
    void batchUpdateEditsSeveralDaysAndKeepsDuplicateRowsInSync() {
        Long instrumentId = 4L;
        LocalDate d1 = LocalDate.of(2026, 10, 3);
        LocalDate d2 = LocalDate.of(2026, 10, 4);
        InvestmentInstrument instrument = instrument(instrumentId);
        InvestmentPrice d1First = price(1L, d1.atTime(9, 0), "3.85");
        InvestmentPrice d1Last = price(2L, d1.atTime(9, 0), "3.85");
        InvestmentPrice d2Only = price(3L, d2.atTime(12, 0), "3.85");
        InvestmentPrice latest = price(4L, d2.plusDays(5).atTime(12, 0), "9");
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(instrument));
        when(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
                instrumentId, d1.atStartOfDay(), d1.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(d1First, d1Last));
        when(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
                instrumentId, d2.atStartOfDay(), d2.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(d2Only));
        when(priceRepository.findFirstByInstrumentIdOrderByAsOfDescIdDesc(instrumentId)).thenReturn(Optional.of(latest));

        service().updateHistoricalPrices(new es.triana.company.investments.model.api.HistoricalPriceBatchUpdateRequestDTO(
                instrumentId, null, List.of(
                        new es.triana.company.investments.model.api.HistoricalPriceBatchUpdateRequestDTO.Item(d1, new BigDecimal("365")),
                        new es.triana.company.investments.model.api.HistoricalPriceBatchUpdateRequestDTO.Item(d2, new BigDecimal("366")))));

        assertThat(d1First.getPrice()).isEqualByComparingTo("365");
        assertThat(d1Last.getPrice()).isEqualByComparingTo("365");
        assertThat(d2Only.getPrice()).isEqualByComparingTo("366");
    }

    @Test
    void historicalPriceEnteredInEurIsStoredInInstrumentCurrency() {
        Long instrumentId = 4L;
        LocalDate date = LocalDate.of(2026, 10, 10);
        InvestmentInstrument instrument = InvestmentInstrument.builder().id(instrumentId).currency("USD").build();
        InvestmentPrice edited = InvestmentPrice.builder()
                .id(20L).instrumentId(instrumentId).asOf(date.atTime(12, 0)).price(new BigDecimal("3.85")).currency("USD").build();
        InvestmentPrice latest = InvestmentPrice.builder()
                .id(21L).instrumentId(instrumentId).asOf(date.plusDays(1).atTime(12, 0)).price(BigDecimal.ONE).currency("USD").build();
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(instrument));
        when(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
                instrumentId, date.atStartOfDay(), date.plusDays(1).atStartOfDay().minusNanos(1)))
                .thenReturn(List.of(edited));
        when(priceRepository.findFirstByInstrumentIdOrderByAsOfDescIdDesc(instrumentId)).thenReturn(Optional.of(latest));
        when(eurConversionService.fromEur(new BigDecimal("340"), "USD", date)).thenReturn(new BigDecimal("367.2"));

        service().updateHistoricalPrice(new HistoricalPriceUpdateRequestDTO(
                instrumentId, date, new BigDecimal("340"), "EUR"));

        assertThat(edited.getPrice()).isEqualByComparingTo("367.2");
        assertThat(edited.getCurrency()).isEqualTo("USD");
    }

    @Test
    void updatingUsdPriceStoresPositionValueInEur() {
        Long instrumentId = 4L;
        InvestmentInstrument instrument = InvestmentInstrument.builder().id(instrumentId).currency("USD").build();
        Investment position = Investment.builder().quantity(new BigDecimal("2")).currency("USD").build();
        when(instrumentRepository.findById(instrumentId)).thenReturn(Optional.of(instrument));
        when(investmentRepository.findByInstrumentId(instrumentId)).thenReturn(List.of(position));
        when(eurConversionService.toEur(new BigDecimal("797.78"), "USD")).thenReturn(new BigDecimal("700"));

        service().refreshPricesOnDemand(List.of(PriceUpdateRequestDTO.builder()
                .instrumentId(instrumentId).price(new BigDecimal("398.89")).currency("USD").build()));

        assertThat(position.getCurrentValueCalculated()).isEqualByComparingTo("700.00");
        assertThat(position.getCurrency()).isEqualTo("EUR");
    }

    private PriceRefreshService service() {
        return new PriceRefreshService(
                instrumentRepository, priceRepository, investmentRepository, typeCatalogRepository,
                marketPriceClient, eurConversionService);
    }

    private InvestmentInstrument instrument(Long id) {
        return InvestmentInstrument.builder().id(id).currency("EUR").lastPrice(new BigDecimal("262")).build();
    }

    private InvestmentPrice price(Long id, LocalDateTime asOf, String value) {
        return InvestmentPrice.builder()
                .id(id).instrumentId(4L).asOf(asOf).price(new BigDecimal(value)).currency("EUR").build();
    }
}