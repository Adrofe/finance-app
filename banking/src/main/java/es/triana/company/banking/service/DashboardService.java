package es.triana.company.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.DashboardSummaryDTO;
import es.triana.company.banking.model.api.SpendingByCategoryDTO;
import es.triana.company.banking.model.api.TimeSeriesPointDTO;
import es.triana.company.banking.repository.TransactionRepository;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;

    public DashboardService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    // ── Summary ────────────────────────────────────────────────────────────────

    public DashboardSummaryDTO getSummary(Long tenantId, LocalDate startDate, LocalDate endDate) {
        BigDecimal income   = nullSafe(transactionRepository.sumIncomeByPeriod(tenantId, startDate, endDate));
        BigDecimal expenses = nullSafe(transactionRepository.sumExpensesByPeriod(tenantId, startDate, endDate));
        Long count          = transactionRepository.countByPeriod(tenantId, startDate, endDate);
        BigDecimal net      = income.add(expenses);

        Double savingsRate = null;
        if (income.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = net
                    .divide(income, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return DashboardSummaryDTO.builder()
                .totalIncome(income)
                .totalExpenses(expenses)
                .net(net)
                .savingsRate(savingsRate)
                .transactionCount(count)
                .build();
    }

    // ── Spending by category ───────────────────────────────────────────────────

    public List<SpendingByCategoryDTO> getSpendingByCategory(
            Long tenantId, LocalDate startDate, LocalDate endDate) {

        List<Object[]> rows = transactionRepository.findSpendingByCategory(tenantId, startDate, endDate);

        // Total expenses (absolute value) to compute percentages
        BigDecimal totalExpenses = rows.stream()
                .map(r -> (BigDecimal) r[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();

        List<SpendingByCategoryDTO> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long       categoryId    = (Long)       row[0];
            String     categoryCode  = (String)     row[1];
            String     categoryName  = (String)     row[2];
            BigDecimal total         = (BigDecimal) row[3];
            Long       txCount       = (Long)       row[4];

            Double percentage = null;
            if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
                percentage = total.abs()
                        .divide(totalExpenses, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            result.add(SpendingByCategoryDTO.builder()
                    .categoryId(categoryId)
                    .categoryCode(categoryCode)
                    .categoryName(categoryName)
                    .total(total)
                    .percentage(percentage)
                    .transactionCount(txCount)
                    .build());
        }
        return result;
    }

    // ── Time series ────────────────────────────────────────────────────────────

    /**
     * @param groupBy "MONTH" (default) or "DAY"
     */
    public List<TimeSeriesPointDTO> getTimeSeries(
            Long tenantId, LocalDate startDate, LocalDate endDate, String groupBy) {

        List<Object[]> rows = transactionRepository.findDateAmountSeries(tenantId, startDate, endDate);

        boolean byDay = "DAY".equalsIgnoreCase(groupBy);

        // TreeMap keeps chronological order automatically
        Map<String, BigDecimal[]> grouped = new TreeMap<>();

        for (Object[] row : rows) {
            LocalDate  date   = (LocalDate)  row[0];
            BigDecimal amount = (BigDecimal) row[1];

            String key = byDay
                    ? date.toString()                                                    // "YYYY-MM-DD"
                    : date.getYear() + "-" + String.format("%02d", date.getMonthValue()); // "YYYY-MM"

            grouped.computeIfAbsent(key, k -> new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO });
            BigDecimal[] vals = grouped.get(key);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                vals[0] = vals[0].add(amount); // income
            } else {
                vals[1] = vals[1].add(amount); // expenses
            }
        }

        List<TimeSeriesPointDTO> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : grouped.entrySet()) {
            BigDecimal income   = entry.getValue()[0];
            BigDecimal expenses = entry.getValue()[1];
            result.add(TimeSeriesPointDTO.builder()
                    .period(entry.getKey())
                    .income(income)
                    .expenses(expenses)
                    .net(income.add(expenses))
                    .build());
        }
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
