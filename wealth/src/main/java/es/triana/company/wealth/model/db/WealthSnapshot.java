package es.triana.company.wealth.model.db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "snapshots", schema = "wealth")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalValue;

    @Column(name = "cash_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal cashValue;

    @Column(name = "funds_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal fundsValue;

    @Column(name = "etfs_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal etfsValue;

    @Column(name = "crypto_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal cryptoValue;

    @Column(name = "stocks_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal stocksValue;

    @Column(name = "bonds_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal bondsValue;

    @Column(name = "real_estate_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal realEstateValue;

    @Column(name = "other_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal otherValue;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<WealthSnapshotItem> items = new ArrayList<>();
}
