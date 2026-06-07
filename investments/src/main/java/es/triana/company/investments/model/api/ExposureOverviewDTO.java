package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExposureOverviewDTO {

    private BigDecimal totalCurrentValue;
    private List<String> appliedTypeCodes;
    private List<ExposureOverviewBucketDTO> countries;
    private List<ExposureOverviewBucketDTO> regions;
    private List<ExposureOverviewBucketDTO> sectors;
    private List<ExposureOverviewBucketDTO> industries;
    private List<ExposureOverviewBucketDTO> marketRegimes;
}