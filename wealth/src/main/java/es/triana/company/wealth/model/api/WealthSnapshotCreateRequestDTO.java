package es.triana.company.wealth.model.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthSnapshotCreateRequestDTO {

    private LocalDate snapshotDate;
    private String currency;
    private String notes;
    
    @Valid
    @NotEmpty
    private List<WealthSnapshotItemInputDTO> items;
}
