package es.triana.company.banking.model.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionTaxRequest {

    @NotNull(message = "grossAmount is required")
    @Positive(message = "grossAmount must be positive")
    private BigDecimal grossAmount;

    @NotNull(message = "taxAmount is required")
    private BigDecimal taxAmount;

    @NotNull(message = "taxTypeId is required")
    private Long taxTypeId;

    private String notes;
}
