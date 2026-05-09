package es.triana.company.budget.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

import es.triana.company.budget.model.BudgetLineType;
import es.triana.company.budget.model.db.BudgetPlanLine;
import es.triana.company.budget.model.db.BudgetSnapshot;
import es.triana.company.budget.model.db.BudgetSnapshotLine;
import java.time.LocalDateTime;

/**
 * Calculates snapshot lines and totals for budget snapshots.
 * Separates transaction accumulation from variance calculation.
 */
@Component
public class BudgetSnapshotCalculator {

    /**
     * Holds transaction accumulators separated by line type.
     */
    public static class TransactionAccumulators {
        public final Map<Long, Accumulator> expenseByLineId;
        public final Map<Long, Accumulator> incomeByLineId;

        public TransactionAccumulators(Map<Long, Accumulator> expenseByLineId, Map<Long, Accumulator> incomeByLineId) {
            this.expenseByLineId = expenseByLineId;
            this.incomeByLineId = incomeByLineId;
        }
    }

    /**
     * Initializes empty accumulators for each budget line.
     */
    public TransactionAccumulators initializeAccumulators(List<BudgetPlanLine> planLines) {
        Map<Long, Accumulator> expenseByLineId = new java.util.HashMap<>();
        Map<Long, Accumulator> incomeByLineId = new java.util.HashMap<>();

        for (BudgetPlanLine line : planLines) {
            if (line.getLineType() == BudgetLineType.INCOME) {
                incomeByLineId.put(line.getId(), new Accumulator());
            } else {
                expenseByLineId.put(line.getId(), new Accumulator());
            }
        }

        return new TransactionAccumulators(expenseByLineId, incomeByLineId);
    }

    /**
     * Builds expense snapshot lines and calculates expense totals.
     */
    public SnapshotCalculationResult calculateExpenseLines(List<BudgetPlanLine> expenseLines, Map<Long, Accumulator> expenseByLineId, BudgetSnapshot snapshot, LocalDateTime now) {

        List<BudgetSnapshotLine> lines = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;

        for (BudgetPlanLine planLine : expenseLines) {
            Accumulator acc = expenseByLineId.getOrDefault(planLine.getId(), new Accumulator());
            BigDecimal spentAmount = acc.spent;
            BigDecimal budgetAmount = planLine.getBudgetAmount();
            BigDecimal variance = budgetAmount.subtract(spentAmount);
            boolean compliant = spentAmount.compareTo(budgetAmount) <= 0;

            totalBudget = totalBudget.add(budgetAmount);
            totalSpent = totalSpent.add(spentAmount);

            lines.add(buildSnapshotLine(snapshot, planLine, budgetAmount, spentAmount, variance, acc.count, compliant, BudgetLineType.EXPENSE, now));
        }

        return new SnapshotCalculationResult(lines, totalBudget, totalSpent, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Builds income snapshot lines and calculates income totals.
     * For income, compliant means received is greater than or equal to expected.
     */
    public SnapshotCalculationResult calculateIncomeLines(List<BudgetPlanLine> incomeLines, Map<Long, Accumulator> incomeByLineId, BudgetSnapshot snapshot, LocalDateTime now) {

        List<BudgetSnapshotLine> lines = new ArrayList<>();
        BigDecimal totalExpectedIncome = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        for (BudgetPlanLine planLine : incomeLines) {
            Accumulator acc = incomeByLineId.getOrDefault(planLine.getId(), new Accumulator());
            BigDecimal actualIncome = acc.spent;
            BigDecimal expectedIncome = planLine.getBudgetAmount();
            BigDecimal variance = actualIncome.subtract(expectedIncome);
            boolean compliant = actualIncome.compareTo(expectedIncome) >= 0; // income compliant = received >= expected

            totalExpectedIncome = totalExpectedIncome.add(expectedIncome);
            totalIncome = totalIncome.add(actualIncome);

            lines.add(buildSnapshotLine(snapshot, planLine, expectedIncome, actualIncome, variance, acc.count, compliant, BudgetLineType.INCOME, now));
        }

        return new SnapshotCalculationResult(lines, BigDecimal.ZERO, BigDecimal.ZERO, totalExpectedIncome, totalIncome);
    }

    /**
     * Builds a single snapshot line with all calculated fields.
     */
    private BudgetSnapshotLine buildSnapshotLine(
            BudgetSnapshot snapshot,
            BudgetPlanLine planLine,
            BigDecimal budgetAmount,
            BigDecimal spentAmount,
            BigDecimal variance,
            long transactionCount,
            boolean compliant,
            BudgetLineType lineType,
            LocalDateTime now) {

        return BudgetSnapshotLine.builder()
                .snapshot(snapshot)
                .categoryId(planLine.getCategoryId())
                .categoryCode(planLine.getCategoryCode())
                .categoryName(planLine.getCategoryName())
                .budgetAmount(budgetAmount)
                .spentAmount(spentAmount)
                .variance(variance)
                .transactionCount(transactionCount)
                .compliant(compliant)
                .lineType(lineType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Result of a snapshot line calculation.
     */
    public static class SnapshotCalculationResult {
        public final List<BudgetSnapshotLine> lines;
        public final BigDecimal totalBudget;
        public final BigDecimal totalSpent;
        public final BigDecimal totalExpectedIncome;
        public final BigDecimal totalIncome;

        public SnapshotCalculationResult(List<BudgetSnapshotLine> lines, BigDecimal totalBudget, BigDecimal totalSpent, BigDecimal totalExpectedIncome, BigDecimal totalIncome) {
            this.lines = lines;
            this.totalBudget = totalBudget;
            this.totalSpent = totalSpent;
            this.totalExpectedIncome = totalExpectedIncome;
            this.totalIncome = totalIncome;
        }
    }

    /**
     * Simple accumulator for totals and transaction count per line.
     */
    public static class Accumulator {
        public BigDecimal spent = BigDecimal.ZERO;
        public long count = 0;

        public void add(BigDecimal amount) {
            this.spent = this.spent.add(amount);
            this.count += 1;
        }
    }
}
