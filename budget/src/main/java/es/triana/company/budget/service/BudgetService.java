package es.triana.company.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import es.triana.company.budget.mapper.BudgetMapper;
import es.triana.company.budget.model.BudgetLineType;
import es.triana.company.budget.model.api.BankingCategoryDTO;
import es.triana.company.budget.model.api.BankingTransactionDTO;
import es.triana.company.budget.model.api.BudgetPlanDTO;
import es.triana.company.budget.model.api.BudgetPlanLineDTO;
import es.triana.company.budget.model.api.BudgetPlanLineRequestDTO;
import es.triana.company.budget.model.api.BudgetPlanRequestDTO;
import es.triana.company.budget.model.api.BudgetSnapshotDTO;
import es.triana.company.budget.model.api.BudgetSnapshotLineDTO;
import es.triana.company.budget.model.db.BudgetPlan;
import es.triana.company.budget.model.db.BudgetPlanLine;
import es.triana.company.budget.model.db.BudgetSnapshot;
import es.triana.company.budget.model.db.BudgetSnapshotLine;
import es.triana.company.budget.repository.BudgetPlanRepository;
import es.triana.company.budget.repository.BudgetSnapshotRepository;
import es.triana.company.budget.service.client.BankingApiClient;

@Service
public class BudgetService {

    private final BudgetPlanRepository planRepository;
    private final BudgetSnapshotRepository snapshotRepository;
    private final BankingApiClient bankingApiClient;
    private final BudgetMapper budgetMapper;

    public BudgetService(BudgetPlanRepository planRepository, BudgetSnapshotRepository snapshotRepository, BankingApiClient bankingApiClient, BudgetMapper budgetMapper) {
        this.planRepository = planRepository;
        this.snapshotRepository = snapshotRepository;
        this.bankingApiClient = bankingApiClient;
        this.budgetMapper = budgetMapper;
    }

    @Transactional
    public BudgetPlanDTO upsertPlan(Long tenantId, String bearerToken, BudgetPlanRequestDTO request) {
        validateTenantId(tenantId);
        validatePlanRequest(request);

        Map<Long, BankingCategoryDTO> categoriesById = loadCategories(bearerToken);
        LocalDateTime now = LocalDateTime.now();

        BudgetPlan plan = request.getId() != null
                ? planRepository.findByIdAndTenantId(request.getId(), tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget plan not found"))
                : BudgetPlan.builder()
                    .tenantId(tenantId)
                    .createdAt(now)
                    .build();

        plan.setUpdatedAt(now);
        plan.setName(request.getName().trim());
        plan.setDescription(request.getDescription());
        plan.setCurrency(normalizeCurrency(request.getCurrency()));
        plan.setActive(request.getActive() == null || request.getActive());

        plan.getLines().clear();
        Set<String> seenKeys = new HashSet<>();
        for (BudgetPlanLineRequestDTO lineRequest : request.getLines()) {
            BudgetLineType lineType = lineRequest.getLineType() != null ? lineRequest.getLineType() : BudgetLineType.EXPENSE;
            String uniqueKey = lineType + ":" + lineRequest.getCategoryId();
            if (!seenKeys.add(uniqueKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicated category in budget plan for type " + lineType);
            }
            BankingCategoryDTO category = categoriesById.get(lineRequest.getCategoryId());
            if (category == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category id: " + lineRequest.getCategoryId());
            }
            BigDecimal budgetAmount = normalizeAmount(lineRequest.getBudgetAmount());
            BudgetPlanLine line = BudgetPlanLine.builder()
                    .plan(plan)
                    .categoryId(category.getId())
                    .categoryCode(category.getCode())
                    .categoryName(category.getName())
                    .budgetAmount(budgetAmount)
                    .lineType(lineType)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            plan.getLines().add(line);
        }

        BudgetPlan saved = planRepository.save(plan);
        return budgetMapper.toPlanDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BudgetPlanDTO> getPlans(Long tenantId) {
        validateTenantId(tenantId);
        return planRepository.findAllByTenantIdOrderByNameAsc(tenantId)
                .stream()
                .map(budgetMapper::toPlanDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BudgetPlanDTO getPlan(Long tenantId, Long id) {
        validateTenantId(tenantId);
        BudgetPlan plan = planRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget plan not found"));
        return budgetMapper.toPlanDto(plan);
    }

    @Transactional
    public void deletePlan(Long tenantId, Long id) {
        validateTenantId(tenantId);
        BudgetPlan plan = planRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget plan not found"));
        planRepository.delete(plan);
    }

    @Transactional
    public BudgetSnapshotDTO refreshSnapshot(Long tenantId, String bearerToken, Long planId, LocalDate startDate, LocalDate endDate) {
        validateTenantId(tenantId);
        BudgetPlan plan = planRepository.findByIdAndTenantId(planId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget plan not found"));

        LocalDate periodStart = startDate != null ? startDate : YearMonth.now().atDay(1);
        LocalDate periodEnd = endDate != null ? endDate : LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<BankingCategoryDTO> categories = bankingApiClient.getCategories(bearerToken);
        Map<Long, BankingCategoryDTO> categoriesById = categories.stream()
                .collect(LinkedHashMap::new, (map, category) -> map.put(category.getId(), category), Map::putAll);

        List<BankingTransactionDTO> transactions = bankingApiClient.getTransactions(bearerToken, periodStart, periodEnd);

        // Separate plan lines by type
        List<BudgetPlanLine> expenseLines = plan.getLines().stream()
                .filter(l -> l.getLineType() != BudgetLineType.INCOME)
                .toList();
        List<BudgetPlanLine> incomeLines = plan.getLines().stream()
                .filter(l -> l.getLineType() == BudgetLineType.INCOME)
                .toList();

        // Accumulators keyed by plan line id
        Map<Long, Accumulator> expenseByLineId = new HashMap<>();
        Map<Long, Accumulator> incomeByLineId = new HashMap<>();
        for (BudgetPlanLine line : plan.getLines()) {
            if (line.getLineType() == BudgetLineType.INCOME) {
                incomeByLineId.put(line.getId(), new Accumulator());
            } else {
                expenseByLineId.put(line.getId(), new Accumulator());
            }
        }

        for (BankingTransactionDTO transaction : transactions) {
            if (transaction.getAmount() == null || transaction.getCategoryId() == null) continue;
            BankingCategoryDTO txCategory = categoriesById.get(transaction.getCategoryId());
            if (txCategory == null) continue;

            if (transaction.getAmount() < 0) {
                // Expense
                BudgetPlanLine matched = matchLine(expenseLines, txCategory);
                if (matched != null) {
                    expenseByLineId.computeIfAbsent(matched.getId(), k -> new Accumulator())
                            .add(BigDecimal.valueOf(transaction.getAmount()).abs());
                }
            } else if (transaction.getAmount() > 0) {
                // Income
                BudgetPlanLine matched = matchLine(incomeLines, txCategory);
                if (matched != null) {
                    incomeByLineId.computeIfAbsent(matched.getId(), k -> new Accumulator())
                            .add(BigDecimal.valueOf(transaction.getAmount()));
                }
            }
        }

        BudgetSnapshot snapshot = snapshotRepository
                .findByTenantIdAndPlanIdAndPeriodStartAndPeriodEnd(tenantId, planId, periodStart, periodEnd)
                .orElseGet(() -> BudgetSnapshot.builder()
                        .tenantId(tenantId)
                        .plan(plan)
                        .periodStart(periodStart)
                        .periodEnd(periodEnd)
                        .createdAt(now)
                        .build());

        snapshot.setPlan(plan);
        snapshot.setUpdatedAt(now);
        snapshot.setGeneratedAt(now);

        List<BudgetSnapshotLine> snapshotLines = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal totalExpectedIncome = BigDecimal.ZERO;
        BigDecimal totalIncome = BigDecimal.ZERO;

        for (BudgetPlanLine planLine : sortedLines(expenseLines)) {
            Accumulator acc = expenseByLineId.getOrDefault(planLine.getId(), new Accumulator());
            BigDecimal spentAmount = acc.spent;
            BigDecimal budgetAmount = planLine.getBudgetAmount();
            BigDecimal variance = budgetAmount.subtract(spentAmount);
            boolean compliant = spentAmount.compareTo(budgetAmount) <= 0;

            totalBudget = totalBudget.add(budgetAmount);
            totalSpent = totalSpent.add(spentAmount);

            snapshotLines.add(BudgetSnapshotLine.builder()
                    .snapshot(snapshot)
                    .categoryId(planLine.getCategoryId())
                    .categoryCode(planLine.getCategoryCode())
                    .categoryName(planLine.getCategoryName())
                    .budgetAmount(budgetAmount)
                    .spentAmount(spentAmount)
                    .variance(variance)
                    .transactionCount(acc.count)
                    .compliant(compliant)
                    .lineType(BudgetLineType.EXPENSE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        for (BudgetPlanLine planLine : sortedLines(incomeLines)) {
            Accumulator acc = incomeByLineId.getOrDefault(planLine.getId(), new Accumulator());
            BigDecimal actualIncome = acc.spent;  // reuses the same accumulator field
            BigDecimal expectedIncome = planLine.getBudgetAmount();
            BigDecimal variance = actualIncome.subtract(expectedIncome); // positive = more than expected
            boolean compliant = actualIncome.compareTo(expectedIncome) >= 0; // income compliant = received >= expected

            totalExpectedIncome = totalExpectedIncome.add(expectedIncome);
            totalIncome = totalIncome.add(actualIncome);

            snapshotLines.add(BudgetSnapshotLine.builder()
                    .snapshot(snapshot)
                    .categoryId(planLine.getCategoryId())
                    .categoryCode(planLine.getCategoryCode())
                    .categoryName(planLine.getCategoryName())
                    .budgetAmount(expectedIncome)
                    .spentAmount(actualIncome)
                    .variance(variance)
                    .transactionCount(acc.count)
                    .compliant(compliant)
                    .lineType(BudgetLineType.INCOME)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        snapshot.setTotalBudget(totalBudget);
        snapshot.setTotalSpent(totalSpent);
        snapshot.setVariance(totalBudget.subtract(totalSpent));
        snapshot.setCompliant(totalSpent.compareTo(totalBudget) <= 0);
        snapshot.setTotalExpectedIncome(totalExpectedIncome);
        snapshot.setTotalIncome(totalIncome);
        snapshot.setIncomeVariance(totalIncome.subtract(totalExpectedIncome));
        snapshot.setNetBalance(totalIncome.subtract(totalSpent));
        snapshot.getLines().clear();
        snapshot.getLines().addAll(snapshotLines);

        BudgetSnapshot saved = snapshotRepository.save(snapshot);
        return budgetMapper.toSnapshotDto(saved);
    }

    @Transactional(readOnly = true)
    public List<BudgetSnapshotDTO> getSnapshots(Long tenantId, Long planId) {
        validateTenantId(tenantId);
        return snapshotRepository.findAllByTenantIdAndPlanIdOrderByPeriodStartDescGeneratedAtDesc(tenantId, planId)
                .stream()
                .map(budgetMapper::toSnapshotDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BudgetSnapshotDTO getLatestSnapshot(Long tenantId, Long planId) {
        validateTenantId(tenantId);
        BudgetSnapshot snapshot = snapshotRepository.findTopByTenantIdAndPlanIdOrderByPeriodStartDescGeneratedAtDesc(tenantId, planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No budget snapshot found"));
        return budgetMapper.toSnapshotDto(snapshot);
    }

    private Map<Long, BankingCategoryDTO> loadCategories(String bearerToken) {
        return bankingApiClient.getCategories(bearerToken).stream()
                .collect(LinkedHashMap::new, (map, category) -> map.put(category.getId(), category), Map::putAll);
    }

    private void validateTenantId(Long tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant id is required");
        }
    }

    private void validatePlanRequest(BudgetPlanRequestDTO request) {
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one budget line is required");
        }
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? "EUR" : currency.trim().toUpperCase();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        return amount.abs().setScale(2, RoundingMode.HALF_UP);
    }

    private List<BudgetPlanLine> sortedLines(List<BudgetPlanLine> lines) {
        return lines.stream()
                .sorted(Comparator.comparingInt((BudgetPlanLine line) -> line.getCategoryCode() != null ? line.getCategoryCode().length() : 0).reversed())
                .toList();
    }

    private BudgetPlanLine matchLine(List<BudgetPlanLine> lines, BankingCategoryDTO transactionCategory) {
        List<BudgetPlanLine> sorted = sortedLines(lines);
        for (BudgetPlanLine line : sorted) {
            if (line.getCategoryId().equals(transactionCategory.getId())) {
                return line;
            }
        }
        String txCode = transactionCategory.getCode();
        if (txCode == null || txCode.isBlank()) {
            return null;
        }
        for (BudgetPlanLine line : sorted) {
            String lineCode = line.getCategoryCode();
            if (lineCode != null && (txCode.equals(lineCode) || txCode.startsWith(lineCode + "."))) {
                return line;
            }
        }
        return null;
    }

    private static final class Accumulator {
        private BigDecimal spent = BigDecimal.ZERO;
        private long count = 0;

        private void add(BigDecimal amount) {
            this.spent = this.spent.add(amount);
            this.count += 1;
        }
    }
}
