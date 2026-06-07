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
public class ExposureOverviewBucketDTO {

    private String code;
    private String name;
    private BigDecimal currentValue;
    private BigDecimal sharePct;
}