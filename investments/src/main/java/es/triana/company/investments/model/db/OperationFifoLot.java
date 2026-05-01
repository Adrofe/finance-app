package es.triana.company.investments.model.db;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "operation_fifo_lots", schema = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationFifoLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sell_operation_id", nullable = false)
    private Long sellOperationId;

    @Column(name = "buy_operation_id", nullable = false)
    private Long buyOperationId;

    /** Fraction of the buy lot consumed by this sell */
    @Column(name = "quantity", nullable = false, precision = 28, scale = 10)
    private BigDecimal quantity;

    @Column(name = "buy_unit_price_eur", nullable = false, precision = 18, scale = 10)
    private BigDecimal buyUnitPriceEur;

    @Column(name = "sell_unit_price_eur", nullable = false, precision = 18, scale = 10)
    private BigDecimal sellUnitPriceEur;

    /** (sell - buy) × quantity. Negative means loss. */
    @Column(name = "gain_loss_eur", nullable = false, precision = 18, scale = 4)
    private BigDecimal gainLossEur;
}
