package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateDTO(
        Long id,
        String fromCurrency,
        String toCurrency,
        BigDecimal rate,
        String source,
        LocalDate asOf
) {
}
