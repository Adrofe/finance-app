package es.triana.company.budget.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.budget.model.db.BudgetSnapshot;

public interface BudgetSnapshotRepository extends JpaRepository<BudgetSnapshot, Long> {
    List<BudgetSnapshot> findAllByTenantIdAndPlanIdOrderByPeriodStartDescGeneratedAtDesc(Long tenantId, Long planId);
    Optional<BudgetSnapshot> findByIdAndTenantId(Long id, Long tenantId);
    Optional<BudgetSnapshot> findByTenantIdAndPlanIdAndPeriodStartAndPeriodEnd(Long tenantId, Long planId, LocalDate periodStart, LocalDate periodEnd);
    Optional<BudgetSnapshot> findTopByTenantIdAndPlanIdOrderByPeriodStartDescGeneratedAtDesc(Long tenantId, Long planId);
}
