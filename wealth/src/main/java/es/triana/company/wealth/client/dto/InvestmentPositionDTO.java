package es.triana.company.wealth.client.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvestmentPositionDTO {
    private Long id;
    private String typeCode;
    private String typeName;
    private String name;
    private String instrumentSymbol;
    private String platformName;
    private String currency;
    private BigDecimal investedAmount;
    private BigDecimal currentValueManual;
    private BigDecimal currentValueCalculated;
    private BigDecimal quantity;
}
