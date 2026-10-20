package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HistoricalPriceUpdateRequestDTO(
        @NotNull @Positive Long instrumentId,
        @NotNull LocalDate date,
        @NotNull @Positive BigDecimal price) {
}