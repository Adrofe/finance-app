package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ExchangeRateUpsertRequestDTO(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "fromCurrency must be a 3-letter ISO code")
        String fromCurrency,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "toCurrency must be a 3-letter ISO code")
        String toCurrency,

        @NotNull
        @DecimalMin(value = "0.0000000001", message = "rate must be greater than 0")
        BigDecimal rate,

        @NotNull
        LocalDate asOf,

        String source
) {
}
