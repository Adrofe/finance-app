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
import es.triana.company.budget.service.BudgetSnapshotCalculator.Accumulator;
import es.triana.company.budget.service.BudgetSnapshotCalculator.TransactionAccumulators;
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
    private final BudgetTransactionMatcher transactionMatcher;
    private final BudgetSnapshotCalculator snapshotCalculator;

    public BudgetService(BudgetPlanRepository planRepository, BudgetSnapshotRepository snapshotRepository, BankingApiClient bankingApiClient, BudgetMapper budgetMapper, BudgetTransactionMatcher transactionMatcher, BudgetSnapshotCalculator snapshotCalculator) {
        this.planRepository = planRepository;
        this.snapshotRepository = snapshotRepository;
        this.bankingApiClient = bankingApiClient;
        this.budgetMapper = budgetMapper;
        this.transactionMatcher = transactionMatcher;
        this.snapshotCalculator = snapshotCalculator;
    }

    @Transactional
    public BudgetPlanDTO upsertPlan(Long tenantId, String bearerToken, BudgetPlanRequestDTO request) {
        validateTenantId(tenantId);
        validatePlanRequest(request);

        Map<Long, BankingCategoryDTO> categoriesById = loadCategories(bearerToken);
        LocalDateTime now = LocalDateTime.now();

        BudgetPlan plan = loadOrCreatePlan(tenantId, request, now);

        plan.setUpdatedAt(now);
        plan.setName(request.getName().trim());
        plan.setDescription(request.getDescription());
        plan.setCurrency(normalizeCurrency(request.getCurrency()));
        plan.setActive(request.getActive() == null || request.getActive());

        plan.getLines().clear();
        Set<String> seenKeys = new HashSet<>();
        for (BudgetPlanLineRequestDTO lineRequest : request.getLines()) {
            plan.getLines().add(buildPlanLine(plan, lineRequest, categoriesById, seenKeys, now));
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

        Map<Long, BankingCategoryDTO> categoriesById = loadCategoriesMap(bearerToken);
        List<BankingTransactionDTO> transactions = bankingApiClient.getTransactions(bearerToken, periodStart, periodEnd);

        List<BudgetPlanLine> expenseLines = filterExpenseLines(plan.getLines());
        List<BudgetPlanLine> incomeLines = filterIncomeLines(plan.getLines());

        TransactionAccumulators accumulators = snapshotCalculator.initializeAccumulators(plan.getLines());
        processTransactions(transactions, categoriesById, expenseLines, incomeLines, accumulators);

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

        // Calculate lines and totals
        var expenseResult = snapshotCalculator.calculateExpenseLines(expenseLines, accumulators.expenseByLineId, snapshot, now);
        var incomeResult = snapshotCalculator.calculateIncomeLines(incomeLines, accumulators.incomeByLineId, snapshot, now);

        List<BudgetSnapshotLine> allLines = new ArrayList<>();
        allLines.addAll(expenseResult.lines);
        allLines.addAll(incomeResult.lines);

        // Update snapshot totals
        updateSnapshotTotals(snapshot, expenseResult, incomeResult);
        snapshot.getLines().clear();
        snapshot.getLines().addAll(allLines);

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

    private Map<Long, BankingCategoryDTO> loadCategoriesMap(String bearerToken) {
        return loadCategories(bearerToken);
    }

    private List<BudgetPlanLine> filterExpenseLines(List<BudgetPlanLine> lines) {
        return lines.stream()
                .filter(l -> l.getLineType() != BudgetLineType.INCOME)
                .toList();
    }

    private List<BudgetPlanLine> filterIncomeLines(List<BudgetPlanLine> lines) {
        return lines.stream()
                .filter(l -> l.getLineType() == BudgetLineType.INCOME)
                .toList();
    }

    private void processTransactions(List<BankingTransactionDTO> transactions, Map<Long, BankingCategoryDTO> categoriesById, List<BudgetPlanLine> expenseLines, List<BudgetPlanLine> incomeLines,TransactionAccumulators accumulators) {
        for (BankingTransactionDTO transaction : transactions) {
            if (transaction.getAmount() == null || transaction.getCategoryId() == null) {
                continue;
            }

            BankingCategoryDTO txCategory = categoriesById.get(transaction.getCategoryId());
            if (txCategory == null) {
                continue;
            }

            if (transaction.getAmount() < 0) {
                accumulateExpense(transaction, expenseLines, accumulators.expenseByLineId, txCategory);
            } else if (transaction.getAmount() > 0) {
                accumulateIncome(transaction, incomeLines, accumulators.incomeByLineId, txCategory);
            }
        }
    }

    private void accumulateExpense(BankingTransactionDTO transaction, List<BudgetPlanLine> expenseLines,Map<Long, Accumulator> expenseByLineId, BankingCategoryDTO txCategory) {
        BudgetPlanLine matched = transactionMatcher.findMatchingLine(expenseLines, txCategory);
        if (matched != null) {
            expenseByLineId.computeIfAbsent(matched.getId(), k -> new Accumulator())
                    .add(BigDecimal.valueOf(transaction.getAmount()).abs());
        }
    }

    private void accumulateIncome(BankingTransactionDTO transaction, List<BudgetPlanLine> incomeLines, Map<Long, Accumulator> incomeByLineId, BankingCategoryDTO txCategory) {
        BudgetPlanLine matched = transactionMatcher.findMatchingLine(incomeLines, txCategory);
        if (matched != null) {
            incomeByLineId.computeIfAbsent(matched.getId(), k -> new Accumulator())
                    .add(BigDecimal.valueOf(transaction.getAmount()));
        }
    }

    private void updateSnapshotTotals(BudgetSnapshot snapshot, BudgetSnapshotCalculator.SnapshotCalculationResult expenseResult, BudgetSnapshotCalculator.SnapshotCalculationResult incomeResult) {
        snapshot.setTotalBudget(expenseResult.totalBudget);
        snapshot.setTotalSpent(expenseResult.totalSpent);
        snapshot.setVariance(expenseResult.totalBudget.subtract(expenseResult.totalSpent));
        snapshot.setCompliant(expenseResult.totalSpent.compareTo(expenseResult.totalBudget) <= 0);
        snapshot.setTotalExpectedIncome(incomeResult.totalExpectedIncome);
        snapshot.setTotalIncome(incomeResult.totalIncome);
        snapshot.setIncomeVariance(incomeResult.totalIncome.subtract(incomeResult.totalExpectedIncome));
        snapshot.setNetBalance(incomeResult.totalIncome.subtract(expenseResult.totalSpent));
    }

    private BudgetPlan loadOrCreatePlan(Long tenantId, BudgetPlanRequestDTO request, LocalDateTime now) {
        if (request.getId() != null) {
            return planRepository.findByIdAndTenantId(request.getId(), tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget plan not found"));
        }

        return BudgetPlan.builder()
                .tenantId(tenantId)
                .createdAt(now)
                .build();
    }

    private BudgetPlanLine buildPlanLine(BudgetPlan plan, BudgetPlanLineRequestDTO lineRequest, Map<Long, BankingCategoryDTO> categoriesById, Set<String> seenKeys,LocalDateTime now) {
        BudgetLineType lineType = resolveLineType(lineRequest);
        validateUniqueLineCategory(lineRequest, seenKeys, lineType);
        BankingCategoryDTO category = getCategoryOrThrow(categoriesById, lineRequest.getCategoryId());

        return createPlanLine(plan, lineRequest, category, lineType, now);
    }

    private BudgetPlanLine createPlanLine(BudgetPlan plan, BudgetPlanLineRequestDTO lineRequest, BankingCategoryDTO category, BudgetLineType lineType, LocalDateTime now) {
        return BudgetPlanLine.builder()
                .plan(plan)
                .categoryId(category.getId())
                .categoryCode(category.getCode())
                .categoryName(category.getName())
                .budgetAmount(normalizeAmount(lineRequest.getBudgetAmount()))
                .lineType(lineType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BudgetLineType resolveLineType(BudgetPlanLineRequestDTO lineRequest) {
        return lineRequest.getLineType() != null ? lineRequest.getLineType() : BudgetLineType.EXPENSE;
    }

    private void validateUniqueLineCategory(BudgetPlanLineRequestDTO lineRequest, Set<String> seenKeys, BudgetLineType lineType) {
        String uniqueKey = lineType + ":" + lineRequest.getCategoryId();
        if (!seenKeys.add(uniqueKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicated category in budget plan for type " + lineType);
        }
    }

    private BankingCategoryDTO getCategoryOrThrow(Map<Long, BankingCategoryDTO> categoriesById, Long categoryId) {
        BankingCategoryDTO category = categoriesById.get(categoryId);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category id: " + categoryId);
        }

        return category;
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
}
