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
public class TimeSeriesPointDTO {

    /** "YYYY-MM" when groupBy=MONTH, "YYYY-MM-DD" when groupBy=DAY */
    private String period;
    private BigDecimal income;
    private BigDecimal expenses;
    private BigDecimal net;
}
