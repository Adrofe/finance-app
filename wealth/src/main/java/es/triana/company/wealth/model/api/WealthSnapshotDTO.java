package es.triana.company.wealth.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthSnapshotDTO {

    private Long id;
    private LocalDate snapshotDate;
    private LocalDateTime snapshotAt;
    private String currency;
    private BigDecimal totalValue;
    private BigDecimal cashValue;
    private BigDecimal fundsValue;
    private BigDecimal etfsValue;
    private BigDecimal cryptoValue;
    private BigDecimal stocksValue;
    private BigDecimal bondsValue;
    private BigDecimal realEstateValue;
    private BigDecimal otherValue;
    private String notes;
    private List<WealthSnapshotItemDTO> items;
}
