package es.triana.company.budget.model.api;

import java.util.List;
import java.time.LocalDate;
import es.triana.company.budget.model.BudgetPeriod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPlanDTO {

    private Long id;
    private String name;
    private String description;
    private String currency;
    private Boolean active;
    private Boolean primary;
    private BudgetPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<BudgetPlanLineDTO> lines;
}
