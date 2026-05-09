package es.triana.company.budget.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.budget.model.db.BudgetPlan;

public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {
    List<BudgetPlan> findAllByTenantIdOrderByNameAsc(Long tenantId);
    Optional<BudgetPlan> findByIdAndTenantId(Long id, Long tenantId);
}
