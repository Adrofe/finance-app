package es.triana.company.budget.model.api;

import java.math.BigDecimal;

import es.triana.company.budget.model.BudgetLineType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSnapshotLineDTO {

    private Long id;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private BigDecimal budgetAmount;
    private BigDecimal spentAmount;
    private BigDecimal variance;
    private Long transactionCount;
    private Boolean compliant;
    private BudgetLineType lineType;
}
