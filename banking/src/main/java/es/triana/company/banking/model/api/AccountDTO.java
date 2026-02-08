package es.triana.company.banking.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccountDTO {
    private Long id;
    private String name;
    private Double balance;
    private Integer tenantId;
}
