package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceUpdateRequestDTO {

    @NotNull
    @Positive
    private Long instrumentId;

    @NotNull
    @DecimalMin(value = "0.0000000001")
    private BigDecimal price;

    @Size(max = 50)
    private String source;

    @Size(min = 3, max = 3)
    private String currency;

    private LocalDateTime asOf;
}
