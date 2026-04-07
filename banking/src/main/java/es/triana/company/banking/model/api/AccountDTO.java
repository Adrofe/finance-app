package es.triana.company.banking.model.api;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class AccountDTO {
    private Long id;
    private String name;
    private Double balance;
    private Long tenantId;
    private Long institutionId;
    private String iban;
    private Long accountTypeId;
    private String accountTypeName;
    private String currency;
    private Double lastBalanceReal;
    private LocalDateTime lastBalanceRealDate;
    private Double lastBalanceAvailable;
    private LocalDateTime lastBalanceAvailableDate;
    private Boolean isActive;
}
