package es.triana.company.investments.model.api;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExposureRefreshResultDTO {

    private int updatedInstruments;
    private int updatedExposures;
    private int skippedNoData;
    private List<Long> instrumentIds;
    private String mode;
    private List<ExposureSuggestionDTO> suggestedRegions;
    private List<ExposureSuggestionDTO> suggestedCountries;
    private List<ExposureSuggestionDTO> suggestedMarketRegimes;
}