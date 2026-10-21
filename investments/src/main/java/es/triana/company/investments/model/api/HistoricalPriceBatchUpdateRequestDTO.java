package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** {@code currency} is the currency of every price; null means the instrument's own currency. */
public record HistoricalPriceBatchUpdateRequestDTO(
        @NotNull @Positive Long instrumentId,
        @Size(min = 3, max = 3) String currency,
        @NotEmpty List<@Valid Item> prices) {

    public record Item(@NotNull LocalDate date, @NotNull @Positive BigDecimal price) {
    }
}
