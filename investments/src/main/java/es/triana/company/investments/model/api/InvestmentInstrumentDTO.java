package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentInstrumentDTO {

    private Long id;

    @NotNull(message = "typeId is required")
    private Long typeId;

    @NotBlank(message = "code is required")
    @Size(max = 100, message = "code max length is 100")
    private String code;

    @NotBlank(message = "symbol is required")
    @Size(max = 50, message = "symbol max length is 50")
    private String symbol;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name max length is 150")
    private String name;

    @Size(max = 80, message = "market max length is 80")
    private String market;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be exactly 3 letters")
    private String currency;

    @DecimalMin(value = "0", message = "lastPrice must be >= 0")
    private BigDecimal lastPrice;

    @Size(max = 50, message = "lastPriceSource max length is 50")
    private String lastPriceSource;

    private LocalDateTime lastPriceAt;
}
