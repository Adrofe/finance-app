package es.triana.company.budget.model.db;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "budget_snapshots", schema = "budget")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_plan_id", nullable = false)
    private BudgetPlan plan;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "total_budget", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalBudget;

    @Column(name = "total_spent", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalSpent;

    @Column(name = "variance", nullable = false, precision = 18, scale = 2)
    private BigDecimal variance;

    @Column(nullable = false)
    private Boolean compliant;

    @Column(name = "total_expected_income", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalExpectedIncome = BigDecimal.ZERO;

    @Column(name = "total_income", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalIncome = BigDecimal.ZERO;

    @Column(name = "income_variance", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal incomeVariance = BigDecimal.ZERO;

    @Column(name = "net_balance", nullable = false, precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal netBalance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BudgetSnapshotLine> lines = new ArrayList<>();
}
