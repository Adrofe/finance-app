package es.triana.company.banking.model.api;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SpendingByCategoryDTO {

    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    /** Sum of amounts (negative value, e.g. -420.00) */
    private BigDecimal total;
    /** Absolute percentage of total expenses for this category (0–100) */
    private Double percentage;
    private Long transactionCount;
}
