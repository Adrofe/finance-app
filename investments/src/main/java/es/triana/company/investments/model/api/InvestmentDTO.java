package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class InvestmentDTO {

    private Long id;

    @NotNull
    @Positive
    private Long tenantId;

    @NotNull
    @Positive
    private Long typeId;

    private String typeCode;
    private String typeName;

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotNull
    @Positive
    private Long instrumentId;

    private String instrumentSymbol;
    private String instrumentName;

    @Positive
    private Long platformId;

    private String platformCode;
    private String platformName;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal investedAmount;

    @DecimalMin(value = "0.00")
    private BigDecimal currentValueManual;

    @DecimalMin(value = "0.00")
    private BigDecimal currentValueCalculated;

    @DecimalMin(value = "0.00")
    private BigDecimal quantity;

    private LocalDate openedAt;

    @Size(max = 1000)
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
