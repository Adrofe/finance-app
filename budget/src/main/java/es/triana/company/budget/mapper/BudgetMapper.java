package es.triana.company.budget.mapper;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import es.triana.company.budget.model.api.BudgetPlanDTO;
import es.triana.company.budget.model.api.BudgetPlanLineDTO;
import es.triana.company.budget.model.api.BudgetSnapshotDTO;
import es.triana.company.budget.model.api.BudgetMonthDTO;
import es.triana.company.budget.model.api.BudgetSnapshotLineDTO;
import es.triana.company.budget.model.db.BudgetPlan;
import es.triana.company.budget.model.db.BudgetPlanLine;
import es.triana.company.budget.model.db.BudgetSnapshot;
import es.triana.company.budget.model.db.BudgetSnapshotLine;
import es.triana.company.budget.model.BudgetLineType;

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
                .lines(snapshot.getLines().stream().filter(line -> line.getMonthStart() == null)
                        .map(this::toSnapshotLineDto)
                        .toList())
                .months(toMonths(snapshot.getLines()))
                .build();
    }

    private List<BudgetMonthDTO> toMonths(List<BudgetSnapshotLine> lines) {
        Map<LocalDate, List<BudgetSnapshotLine>> grouped = new TreeMap<>();
        lines.stream().filter(line -> line.getMonthStart() != null).forEach(line ->
                grouped.computeIfAbsent(line.getMonthStart(), key -> new java.util.ArrayList<>()).add(line));
        return grouped.entrySet().stream().map(entry -> {
            List<BudgetSnapshotLine> monthLines = entry.getValue();
            BigDecimal budget = sum(monthLines, BudgetLineType.EXPENSE, true);
            BigDecimal spent = sum(monthLines, BudgetLineType.EXPENSE, false);
            return BudgetMonthDTO.builder()
                    .monthStart(entry.getKey()).totalBudget(budget).totalSpent(spent)
                    .variance(budget.subtract(spent)).compliant(spent.compareTo(budget) <= 0)
                    .totalExpectedIncome(sum(monthLines, BudgetLineType.INCOME, true))
                    .totalIncome(sum(monthLines, BudgetLineType.INCOME, false))
                    .lines(monthLines.stream().map(this::toSnapshotLineDto).toList()).build();
        }).toList();
    }

    private BigDecimal sum(List<BudgetSnapshotLine> lines, BudgetLineType type, boolean budget) {
        return lines.stream().filter(line -> line.getLineType() == type)
                .map(line -> budget ? line.getBudgetAmount() : line.getSpentAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
