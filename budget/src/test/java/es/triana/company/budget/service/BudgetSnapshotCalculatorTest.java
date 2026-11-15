package es.triana.company.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import es.triana.company.budget.model.BudgetLineType;
import es.triana.company.budget.model.db.BudgetPlanLine;
import es.triana.company.budget.model.db.BudgetSnapshot;

class BudgetSnapshotCalculatorTest {

    @Test
    void scalesExpenseAndIncomeBudgetsForSelectedMonths() {
        BudgetSnapshotCalculator calculator = new BudgetSnapshotCalculator();
        BudgetPlanLine expense = BudgetPlanLine.builder().id(1L).budgetAmount(new BigDecimal("250.00")).lineType(BudgetLineType.EXPENSE).build();
        BudgetPlanLine income = BudgetPlanLine.builder().id(2L).budgetAmount(new BigDecimal("500.00")).lineType(BudgetLineType.INCOME).build();
        BudgetSnapshot snapshot = new BudgetSnapshot();
        LocalDateTime now = LocalDateTime.now();

        var expenseResult = calculator.calculateExpenseLines(List.of(expense), Map.of(), snapshot, now, Map.of(1L, new BigDecimal("1500.00")));
        var incomeResult = calculator.calculateIncomeLines(List.of(income), Map.of(), snapshot, now, Map.of(2L, new BigDecimal("3000.00")));

        assertEquals(new BigDecimal("1500.00"), expenseResult.totalBudget);
        assertEquals(new BigDecimal("3000.00"), incomeResult.totalExpectedIncome);
    }
}