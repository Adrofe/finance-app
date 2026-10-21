package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code currency} is the currency of {@code price}; null means the instrument's own currency. */
public record HistoricalPriceUpdateRequestDTO(
        @NotNull @Positive Long instrumentId,
        @NotNull LocalDate date,
        @NotNull @Positive BigDecimal price,
        @Size(min = 3, max = 3) String currency) {

    public HistoricalPriceUpdateRequestDTO(Long instrumentId, LocalDate date, BigDecimal price) {
        this(instrumentId, date, price, null);
    }
}