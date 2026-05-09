package es.triana.company.wealth.model.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthSnapshotItemInputDTO {

    @NotBlank
    private String type;
    private String subtype;
    private String source;
    private String sourceRef;

    @NotBlank
    private String label;

    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal quantity;

    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal unitPrice;

    @NotNull
    private BigDecimal value;
    private String currency;
}
