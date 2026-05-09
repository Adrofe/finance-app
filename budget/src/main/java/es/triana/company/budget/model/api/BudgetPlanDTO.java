package es.triana.company.budget.model.api;

import java.util.List;

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
    private List<BudgetPlanLineDTO> lines;
}
