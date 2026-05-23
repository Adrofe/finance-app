package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.util.List;

public record TaxSummaryDTO(
        Long tenantId,
        int year,
        BigDecimal totalGainLossEur,
        List<ByInstrument> byInstrument,
        List<ByCurrency> byCurrency) {

    public record ByInstrument(
            Long instrumentId,
            String code,
            String symbol,
            String name,
            BigDecimal gainLossEur) {
    }

    public record ByCurrency(
            String currency,
            BigDecimal gainLossEur) {
    }
}
