package es.triana.company.budget.model.api;

import java.math.BigDecimal;

import es.triana.company.budget.model.BudgetLineType;
import es.triana.company.budget.model.BudgetLinePeriod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPlanLineRequestDTO {

    @NotNull
    private Long categoryId;

    @NotNull
    @PositiveOrZero
    private BigDecimal budgetAmount;

    private BudgetLinePeriod linePeriod;

    @Builder.Default
    private BudgetLineType lineType = BudgetLineType.EXPENSE;
}
