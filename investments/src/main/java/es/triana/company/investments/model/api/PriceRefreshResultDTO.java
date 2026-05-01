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
public class PriceRefreshResultDTO {

    private int updatedInstruments;
    private int recalculatedPositions;
    private List<Long> instrumentIds;
    private String mode;
}
