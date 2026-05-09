package es.triana.company.wealth.model.api;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthSnapshotItemDTO {

    private Long id;
    private String type;
    private String subtype;
    private String source;
    private String sourceRef;
    private String label;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal value;
    private String currency;
}
