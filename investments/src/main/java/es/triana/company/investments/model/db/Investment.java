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
@Table(name = "investments", schema = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "type_id", nullable = false)
    private Long typeId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "platform_id")
    private Long platformId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "invested_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal investedAmount;

    @Column(name = "current_value_manual", precision = 18, scale = 2)
    private BigDecimal currentValueManual;

    @Column(name = "current_value_calculated", precision = 18, scale = 2)
    private BigDecimal currentValueCalculated;

    @Column(name = "quantity", precision = 28, scale = 10)
    private BigDecimal quantity;

    @Column(name = "opened_at")
    private LocalDate openedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
