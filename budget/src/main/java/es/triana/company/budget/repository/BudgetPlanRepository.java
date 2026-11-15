package es.triana.company.budget.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import es.triana.company.budget.model.db.BudgetPlan;

public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {
    List<BudgetPlan> findAllByTenantIdOrderByNameAsc(Long tenantId);
    Optional<BudgetPlan> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from BudgetPlan plan where plan.id = :planId and plan.tenantId = :tenantId")
    Optional<BudgetPlan> findByIdAndTenantIdForUpdate(@Param("planId") Long planId, @Param("tenantId") Long tenantId);

    @Modifying(flushAutomatically = true)
    @Query("update BudgetPlan plan set plan.primary = false where plan.tenantId = :tenantId and plan.id <> :planId and plan.primary = true")
    int clearOtherPrimaryPlans(@Param("tenantId") Long tenantId, @Param("planId") Long planId);
}
