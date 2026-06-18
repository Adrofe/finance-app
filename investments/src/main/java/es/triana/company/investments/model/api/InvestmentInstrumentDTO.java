package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentInstrumentDTO {

    private Long id;

    @NotNull(message = "typeId is required")
    private Long typeId;

    @NotBlank(message = "code is required")
    @Size(max = 100, message = "code max length is 100")
    private String code;

    @NotBlank(message = "symbol is required")
    @Size(max = 50, message = "symbol max length is 50")
    private String symbol;

    @NotBlank(message = "name is required")
    @Size(max = 150, message = "name max length is 150")
    private String name;

    @Size(max = 80, message = "market max length is 80")
    private String market;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be exactly 3 letters")
    private String currency;

    @DecimalMin(value = "0", message = "lastPrice must be >= 0")
    private BigDecimal lastPrice;

    @Size(max = 50, message = "lastPriceSource max length is 50")
    private String lastPriceSource;

    private LocalDateTime lastPriceAt;

    @Size(max = 500, message = "scraperUrl max length is 500")
    private String scraperUrl;

    @Size(max = 500, message = "finectUrl max length is 500")
    private String finectUrl;

    private Long countryId;

    @Size(min = 2, max = 2, message = "countryCode must be exactly 2 letters")
    private String countryCode;

    @Size(max = 120, message = "countryName max length is 120")
    private String countryName;

    private Long regionId;

    @Size(max = 80, message = "region max length is 80")
    private String regionCode;

    @Size(max = 120, message = "regionName max length is 120")
    private String regionName;

    private Long sectorId;

    @Size(max = 100, message = "sector max length is 100")
    private String sectorCode;

    @Size(max = 140, message = "sectorName max length is 140")
    private String sectorName;

    private Long industryId;

    @Size(max = 120, message = "industry max length is 120")
    private String industryCode;

    @Size(max = 180, message = "industryName max length is 180")
    private String industryName;
}
