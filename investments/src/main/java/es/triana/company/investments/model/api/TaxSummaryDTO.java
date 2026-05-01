package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.util.List;

public record TaxSummaryDTO(
        Long tenantId,
        int year,
        BigDecimal realizedGainLossEur,
        List<ByInstrument> byInstrument,
        List<ByCurrency> byCurrency) {

    public record ByInstrument(
            Long instrumentId,
            String instrumentCode,
            String instrumentSymbol,
            String instrumentName,
            BigDecimal realizedGainLossEur) {
    }

    public record ByCurrency(
            String currency,
            BigDecimal realizedGainLossEur) {
    }
}
