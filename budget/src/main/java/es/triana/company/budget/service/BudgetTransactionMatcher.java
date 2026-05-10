package es.triana.company.budget.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import es.triana.company.budget.model.BudgetLineType;
import es.triana.company.budget.model.api.BankingCategoryDTO;
import es.triana.company.budget.model.api.BankingTransactionDTO;
import es.triana.company.budget.model.db.BudgetPlanLine;

/**
 * Matches banking transactions to budget lines.
 * Supports hierarchical matching by category ID and category code.
 */
@Component
public class BudgetTransactionMatcher {

    /**
     * Finds the budget line that matches a transaction category.
     * It first tries an exact ID match and then falls back to hierarchical code matching.
     */
    public BudgetPlanLine findMatchingLine(List<BudgetPlanLine> planLines, BankingCategoryDTO txCategory) {
        // Attempt 1: exact match by ID
        BudgetPlanLine exactMatch = planLines.stream()
                .filter(line -> line.getCategoryId().equals(txCategory.getId()))
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Attempt 2: hierarchical match by code
        String txCode = txCategory.getCode();
        if (txCode == null || txCode.isBlank()) {
            return null;
        }

        return planLines.stream()
                .filter(line -> matchesCategoryCode(line.getCategoryCode(), txCode))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks whether a plan line code matches a transaction code.
     * Supports exact and hierarchical matches, for example FOOD.GROCERIES matching FOOD.
     */
    private boolean matchesCategoryCode(String lineCode, String txCode) {
        if (lineCode == null) {
            return false;
        }
        return txCode.equals(lineCode) || txCode.startsWith(lineCode + ".");
    }
}
