package es.triana.company.banking.model.api;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardSummaryDTO {

    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal net;
    /** (net / totalIncome) * 100, rounded to 1 decimal. Null when income is zero. */
    private Double savingsRate;
    private Long transactionCount;
}
