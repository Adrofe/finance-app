package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PriceQuotePreviewDTO(
        Long instrumentId,
        String symbol,
        BigDecimal price,
        String source,
        String currency,
        LocalDateTime retrievedAt) {
}