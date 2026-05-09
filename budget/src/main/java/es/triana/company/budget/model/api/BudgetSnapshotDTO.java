package es.triana.company.budget.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSnapshotDTO {

    private Long id;
    private Long budgetPlanId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDateTime generatedAt;
    private BigDecimal totalBudget;
    private BigDecimal totalSpent;
    private BigDecimal variance;
    private Boolean compliant;
    private BigDecimal totalExpectedIncome;
    private BigDecimal totalIncome;
    private BigDecimal incomeVariance;
    private BigDecimal netBalance;
    private List<BudgetSnapshotLineDTO> lines;
}
