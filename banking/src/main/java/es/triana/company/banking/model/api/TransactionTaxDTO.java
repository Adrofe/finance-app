package es.triana.company.banking.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionTaxDTO {

    private Long id;
    private Long transactionId;
    private BigDecimal grossAmount;
    private BigDecimal taxAmount;
    private TaxTypeDTO taxType;
    private String notes;

    // Denormalized from the linked transaction for report views
    private LocalDate bookingDate;
    private String transactionDescription;
    private String currency;
}
