package es.triana.company.investments.model.api;

import java.math.BigDecimal;

import es.triana.company.investments.model.db.InvestmentInstrumentExposure.Dimension;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentInstrumentExposureDTO {

    private Long id;

    @NotNull(message = "instrumentId is required")
    private Long instrumentId;

    @NotNull(message = "dimension is required")
    private Dimension dimension;

    private Long countryId;
    private Long regionId;
    private Long sectorId;
    private Long industryId;

    private String bucketCode;
    private String bucketName;

    @NotNull(message = "weightPct is required")
    @DecimalMin(value = "0", message = "weightPct must be >= 0")
    private BigDecimal weightPct;
}
