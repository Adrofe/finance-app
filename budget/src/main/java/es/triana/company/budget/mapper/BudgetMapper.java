package es.triana.company.budget.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import es.triana.company.budget.model.api.BudgetPlanDTO;
import es.triana.company.budget.model.api.BudgetPlanLineDTO;
import es.triana.company.budget.model.api.BudgetSnapshotDTO;
import es.triana.company.budget.model.api.BudgetSnapshotLineDTO;
import es.triana.company.budget.model.db.BudgetPlan;
import es.triana.company.budget.model.db.BudgetPlanLine;
import es.triana.company.budget.model.db.BudgetSnapshot;
import es.triana.company.budget.model.db.BudgetSnapshotLine;

@Component
public class BudgetMapper {

    public BudgetPlanDTO toPlanDto(BudgetPlan plan) {
        return BudgetPlanDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .currency(plan.getCurrency())
                .active(plan.getActive())
                .lines(plan.getLines().stream()
                        .map(this::toPlanLineDto)
                        .toList())
                .build();
    }

    public BudgetPlanLineDTO toPlanLineDto(BudgetPlanLine line) {
        return BudgetPlanLineDTO.builder()
                .id(line.getId())
                .categoryId(line.getCategoryId())
                .categoryCode(line.getCategoryCode())
                .categoryName(line.getCategoryName())
                .budgetAmount(line.getBudgetAmount())
                .lineType(line.getLineType())
                .build();
    }

    public BudgetSnapshotDTO toSnapshotDto(BudgetSnapshot snapshot) {
        return BudgetSnapshotDTO.builder()
                .id(snapshot.getId())
                .budgetPlanId(snapshot.getPlan() != null ? snapshot.getPlan().getId() : null)
                .periodStart(snapshot.getPeriodStart())
                .periodEnd(snapshot.getPeriodEnd())
                .generatedAt(snapshot.getGeneratedAt())
                .totalBudget(snapshot.getTotalBudget())
                .totalSpent(snapshot.getTotalSpent())
                .variance(snapshot.getVariance())
                .compliant(snapshot.getCompliant())
                .totalExpectedIncome(snapshot.getTotalExpectedIncome())
                .totalIncome(snapshot.getTotalIncome())
                .incomeVariance(snapshot.getIncomeVariance())
                .netBalance(snapshot.getNetBalance())
                .lines(snapshot.getLines().stream()
                        .map(this::toSnapshotLineDto)
                        .toList())
                .build();
    }

    public BudgetSnapshotLineDTO toSnapshotLineDto(BudgetSnapshotLine line) {
        return BudgetSnapshotLineDTO.builder()
                .id(line.getId())
                .categoryId(line.getCategoryId())
                .categoryCode(line.getCategoryCode())
                .categoryName(line.getCategoryName())
                .budgetAmount(line.getBudgetAmount())
                .spentAmount(line.getSpentAmount())
                .variance(line.getVariance())
                .transactionCount(line.getTransactionCount())
                .compliant(line.getCompliant())
                .lineType(line.getLineType())
                .build();
    }
}
