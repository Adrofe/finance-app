package es.triana.company.investments.model.api;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentTypeSummaryDTO {
    private Long typeId;
    private String typeCode;
    private String typeName;
    private long count;
    private BigDecimal investedAmount;
    private BigDecimal currentValue;
    private BigDecimal pnl;
}
