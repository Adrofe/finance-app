package es.triana.company.budget.model.db;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import es.triana.company.budget.model.BudgetLineType;
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
@Table(name = "budget_snapshot_lines", schema = "budget")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSnapshotLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_snapshot_id", nullable = false)
    private BudgetSnapshot snapshot;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "category_code", nullable = false, length = 64)
    private String categoryCode;

    @Column(name = "category_name", nullable = false, length = 160)
    private String categoryName;

    @Column(name = "budget_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "spent_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal spentAmount;

    @Column(name = "variance", nullable = false, precision = 18, scale = 2)
    private BigDecimal variance;

    @Column(name = "transaction_count", nullable = false)
    private Long transactionCount;

    @Column(nullable = false)
    private Boolean compliant;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false, length = 10)
    @Builder.Default
    private BudgetLineType lineType = BudgetLineType.EXPENSE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
