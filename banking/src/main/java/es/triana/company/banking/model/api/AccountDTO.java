package es.triana.company.banking.model.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class AccountDTO {
    @Positive(message = "Id must be greater than zero")
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 128, message = "Name must not exceed 128 characters")
    private String name;

    private Double balance;

    @Positive(message = "Institution id must be greater than zero")
    private Long institutionId;

    private String institutionName;

    @Size(max = 34, message = "IBAN must not exceed 34 characters")
    private String iban;

    @NotNull(message = "Account type is required")
    @Positive(message = "Account type id must be greater than zero")
    private Long accountTypeId;

    private String accountTypeName;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
    private String currency;

    private Double lastBalanceReal;
    private LocalDate lastBalanceRealDate;
    private Double lastBalanceAvailable;
    private LocalDate lastBalanceAvailableDate;
    private Boolean isActive;
}
