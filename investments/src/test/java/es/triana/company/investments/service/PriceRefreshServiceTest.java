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

    private PriceRefreshService service() {
        return new PriceRefreshService(
                instrumentRepository, priceRepository, investmentRepository, typeCatalogRepository, marketPriceClient);
    }

    private InvestmentInstrument instrument(Long id) {
        return InvestmentInstrument.builder().id(id).currency("EUR").lastPrice(new BigDecimal("262")).build();
    }

    private InvestmentPrice price(Long id, LocalDateTime asOf, String value) {
        return InvestmentPrice.builder()
                .id(id).instrumentId(4L).asOf(asOf).price(new BigDecimal(value)).currency("EUR").build();
    }
}