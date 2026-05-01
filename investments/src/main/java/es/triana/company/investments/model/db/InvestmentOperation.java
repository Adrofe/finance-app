package es.triana.company.investments.model.db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
@Table(name = "investment_operations", schema = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investment_id", nullable = false)
    private Long investmentId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** BUY or SELL */
    @Column(name = "type", nullable = false, length = 4)
    private String type;

    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    @Column(name = "quantity", nullable = false, precision = 28, scale = 10)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 10)
    private BigDecimal unitPrice;

    @Column(name = "fees", nullable = false, precision = 18, scale = 4)
    private BigDecimal fees;

    /** quantity × unitPrice + fees (BUY) or - fees (SELL) in original currency */
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** ECB EUR→currency rate on operation_date. Denormalised for fiscal records. */
    @Column(name = "eur_exchange_rate", nullable = false, precision = 18, scale = 10)
    private BigDecimal eurExchangeRate;

    @Column(name = "total_amount_eur", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmountEur;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
