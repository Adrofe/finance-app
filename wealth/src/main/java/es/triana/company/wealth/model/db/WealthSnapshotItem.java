package es.triana.company.wealth.model.db;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "snapshot_items", schema = "wealth")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthSnapshotItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private WealthSnapshot snapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private WealthAssetType assetType;

    @Column(name = "asset_subtype")
    private String assetSubtype;

    @Column(name = "source_system")
    private String sourceSystem;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "quantity", precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", precision = 18, scale = 6)
    private BigDecimal unitPrice;

    @Column(name = "value_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal valueAmount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
