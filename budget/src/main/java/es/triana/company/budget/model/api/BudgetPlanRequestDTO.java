package es.triana.company.budget.model.api;

import java.util.List;
import java.time.LocalDate;
import es.triana.company.budget.model.BudgetPeriod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPlanRequestDTO {

    private Long id;

    @NotBlank
    @Size(max = 160)
    private String name;

    @Size(max = 500)
    private String description;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    private Boolean active;
    private BudgetPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;

    @NotEmpty
    @Valid
    private List<BudgetPlanLineRequestDTO> lines;
}
