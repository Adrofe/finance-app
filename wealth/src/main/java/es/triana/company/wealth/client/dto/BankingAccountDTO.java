package es.triana.company.wealth.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BankingAccountDTO {
    private Long id;
    private String name;
    private Double balance;
    private String institutionName;
    private String iban;
    private String accountTypeName;
    private String currency;
    private Boolean isActive;
}
