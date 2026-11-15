package es.triana.company.budget.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BudgetMonthDTO {
    private LocalDate monthStart;
    private BigDecimal totalBudget;
    private BigDecimal totalSpent;
    private BigDecimal variance;
    private Boolean compliant;
    private BigDecimal totalExpectedIncome;
    private BigDecimal totalIncome;
    private List<BudgetSnapshotLineDTO> lines;
}