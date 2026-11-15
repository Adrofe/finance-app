package es.triana.company.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
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
import es.triana.company.budget.model.BudgetLinePeriod;
import es.triana.company.budget.model.BudgetPeriod;
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
        if (request.getActive() != null || plan.getActive() == null) {
            plan.setActive(request.getActive() == null || request.getActive());
        }
        BudgetPeriod period = request.getPeriod() != null ? request.getPeriod() : (plan.getPeriod() != null ? plan.getPeriod() : BudgetPeriod.MONTHLY);
        if (period == BudgetPeriod.CUSTOM && (request.getStartDate() == null || request.getEndDate() == null || request.getEndDate().isBefore(request.getStartDate()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid start and end date are required for a custom budget");
        }
        plan.setPeriod(period);
        plan.setStartDate(period == BudgetPeriod.CUSTOM ? request.getStartDate() : null);
        plan.setEndDate(period == BudgetPeriod.CUSTOM ? request.getEndDate() : null);

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
    public BudgetPlanDTO setPrimaryPlan(Long tenantId, Long id) {
        validateTenantId(tenantId);
        BudgetPlan plan = planRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget plan not found"));
        planRepository.clearOtherPrimaryPlans(tenantId, id);
        plan.setPrimary(true);
        plan.setUpdatedAt(LocalDateTime.now());
        return budgetMapper.toPlanDto(planRepository.save(plan));
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
        BudgetPlan plan = planRepository.findByIdAndTenantIdForUpdate(planId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget plan not found"));

        LocalDate requestedStart = startDate != null ? startDate : YearMonth.now().atDay(1);
        LocalDate requestedEnd = endDate != null ? endDate : LocalDate.now();
        if (requestedEnd.isBefore(requestedStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must not be before start date");
        }
        if (plan.getPeriod() == BudgetPeriod.CUSTOM && (plan.getStartDate() == null || plan.getEndDate() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom budget dates are missing");
        }
        LocalDate periodStart = plan.getPeriod() == BudgetPeriod.CUSTOM && requestedStart.isBefore(plan.getStartDate()) ? plan.getStartDate() : requestedStart;
        LocalDate periodEnd = plan.getPeriod() == BudgetPeriod.CUSTOM && requestedEnd.isAfter(plan.getEndDate()) ? plan.getEndDate() : requestedEnd;
        if (periodEnd.isBefore(periodStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected dates do not overlap the budget period");
        }
        LocalDateTime now = LocalDateTime.now();

        Map<Long, BankingCategoryDTO> categoriesById = loadCategoriesMap(bearerToken);
        LocalDate lookbackStart = plan.getLines().stream().filter(this::isLongPeriod)
            .map(line -> linePeriodStart(line.getLinePeriod(), periodStart))
            .min(LocalDate::compareTo).orElse(periodStart);
        if (plan.getPeriod() == BudgetPeriod.CUSTOM && lookbackStart.isBefore(plan.getStartDate())) {
            lookbackStart = plan.getStartDate();
        }
        LocalDate transactionStart = lookbackStart;
        List<BankingTransactionDTO> allTransactions = bankingApiClient.getTransactions(bearerToken, transactionStart, periodEnd).stream()
            .filter(transaction -> transaction.getBookingDate() != null
            && !transaction.getBookingDate().toLocalDate().isBefore(transactionStart)
                && !transaction.getBookingDate().toLocalDate().isAfter(periodEnd))
            .toList();
        List<BankingTransactionDTO> transactions = allTransactions.stream()
            .filter(transaction -> !transaction.getBookingDate().toLocalDate().isBefore(periodStart)).toList();

        List<BudgetPlanLine> expenseLines = filterExpenseLines(plan.getLines());
        List<BudgetPlanLine> incomeLines = filterIncomeLines(plan.getLines());
        List<BudgetPlanLine> monthlyExpenseLines = expenseLines.stream().filter(line -> !isLongPeriod(line)).toList();
        List<BudgetPlanLine> monthlyIncomeLines = incomeLines.stream().filter(line -> !isLongPeriod(line)).toList();

        TransactionAccumulators accumulators = snapshotCalculator.initializeAccumulators(plan.getLines());
        processTransactions(transactions, categoriesById, expenseLines, incomeLines, accumulators, plan.getCurrency());

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

        Map<Long, BigDecimal> totalBudgets = new HashMap<>();
        List<BudgetSnapshotLine> monthlyLines = new ArrayList<>();
        for (YearMonth month = YearMonth.from(periodStart); !month.isAfter(YearMonth.from(periodEnd)); month = month.plusMonths(1)) {
            YearMonth currentMonth = month;
            Map<Long, BigDecimal> monthBudgets = new HashMap<>();
            for (BudgetPlanLine line : plan.getLines()) {
                BigDecimal amount = isLongPeriod(line) ? BigDecimal.ZERO : line.getLinePeriod() == BudgetLinePeriod.MONTHLY
                    ? line.getBudgetAmount() : monthlyBudget(plan, line.getBudgetAmount(), month);
                monthBudgets.put(line.getId(), amount);
                totalBudgets.merge(line.getId(), amount, BigDecimal::add);
            }
            TransactionAccumulators monthAccumulators = snapshotCalculator.initializeAccumulators(plan.getLines());
            processTransactions(transactions.stream().filter(transaction -> YearMonth.from(transaction.getBookingDate()).equals(currentMonth)).toList(),
                    categoriesById, expenseLines, incomeLines, monthAccumulators, plan.getCurrency());
            var monthlyExpenses = snapshotCalculator.calculateExpenseLines(expenseLines, monthAccumulators.expenseByLineId, snapshot, now, monthBudgets);
            var monthlyIncome = snapshotCalculator.calculateIncomeLines(incomeLines, monthAccumulators.incomeByLineId, snapshot, now, monthBudgets);
            monthlyLines.addAll(monthlyExpenses.lines);
            monthlyLines.addAll(monthlyIncome.lines);
            monthlyExpenses.lines.forEach(line -> line.setMonthStart(currentMonth.atDay(1)));
            monthlyIncome.lines.forEach(line -> line.setMonthStart(currentMonth.atDay(1)));
        }

        var expenseResult = snapshotCalculator.calculateExpenseLines(monthlyExpenseLines, accumulators.expenseByLineId, snapshot, now, totalBudgets);
        var incomeResult = snapshotCalculator.calculateIncomeLines(monthlyIncomeLines, accumulators.incomeByLineId, snapshot, now, totalBudgets);

        List<BudgetSnapshotLine> allLines = new ArrayList<>();
        allLines.addAll(expenseResult.lines);
        allLines.addAll(incomeResult.lines);
        allLines.addAll(monthlyLines);
        allLines.addAll(calculatePeriodicLines(plan, snapshot, allTransactions, categoriesById, expenseLines, incomeLines, periodStart, periodEnd, now));

        // Update snapshot totals
        updateSnapshotTotals(snapshot, expenseResult, incomeResult);
        snapshot.getLines().clear();
        snapshot.getLines().addAll(allLines);

        BudgetSnapshot saved = snapshotRepository.save(snapshot);
        return budgetMapper.toSnapshotDto(saved);
    }

    private boolean isLongPeriod(BudgetPlanLine line) {
        return line.getLinePeriod() != null && line.getLinePeriod() != BudgetLinePeriod.MONTHLY;
    }

    private int periodMonths(BudgetLinePeriod period) {
        return switch (period) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case SEMIANNUAL -> 6;
            case ANNUAL -> 12;
        };
    }

    private LocalDate linePeriodStart(BudgetLinePeriod period, LocalDate date) {
        int months = periodMonths(period);
        return LocalDate.of(date.getYear(), ((date.getMonthValue() - 1) / months) * months + 1, 1);
    }

    private List<BudgetSnapshotLine> calculatePeriodicLines(BudgetPlan plan, BudgetSnapshot snapshot,
            List<BankingTransactionDTO> transactions, Map<Long, BankingCategoryDTO> categories,
            List<BudgetPlanLine> expenseLines, List<BudgetPlanLine> incomeLines,
            LocalDate start, LocalDate end, LocalDateTime now) {
        List<BudgetSnapshotLine> results = new ArrayList<>();
        for (BudgetPlanLine line : plan.getLines()) {
            if (!isLongPeriod(line)) continue;
            int months = periodMonths(line.getLinePeriod());
            for (LocalDate boundary = linePeriodStart(line.getLinePeriod(), start); !boundary.isAfter(end); boundary = boundary.plusMonths(months)) {
                LocalDate naturalEnd = boundary.plusMonths(months).minusDays(1);
                LocalDate observedStart = plan.getPeriod() == BudgetPeriod.CUSTOM && boundary.isBefore(plan.getStartDate()) ? plan.getStartDate() : boundary;
                LocalDate observedEnd = naturalEnd.isAfter(end) ? end : naturalEnd;
                List<BudgetPlanLine> candidates = line.getLineType() == BudgetLineType.INCOME ? incomeLines : expenseLines;
                List<BankingTransactionDTO> matching = transactions.stream().filter(transaction -> {
                    LocalDate date = transaction.getBookingDate().toLocalDate();
                    if (date.isBefore(observedStart) || date.isAfter(observedEnd) || transaction.getAmount() == null
                            || (line.getLineType() == BudgetLineType.INCOME ? transaction.getAmount() <= 0 : transaction.getAmount() >= 0)) return false;
                    BankingCategoryDTO category = categories.get(transaction.getCategoryId());
                    if (category == null) return false;
                    BudgetPlanLine matched = transactionMatcher.findMatchingLine(candidates, category);
                    return matched != null && matched.getId().equals(line.getId());
                }).toList();
                TransactionAccumulators periodAccumulators = snapshotCalculator.initializeAccumulators(List.of(line));
                processTransactions(matching, categories, line.getLineType() == BudgetLineType.INCOME ? List.of() : List.of(line),
                        line.getLineType() == BudgetLineType.INCOME ? List.of(line) : List.of(), periodAccumulators, plan.getCurrency());
                Map<Long, BigDecimal> budgets = Map.of(line.getId(), line.getBudgetAmount());
                var calculation = line.getLineType() == BudgetLineType.INCOME
                        ? snapshotCalculator.calculateIncomeLines(List.of(line), periodAccumulators.incomeByLineId, snapshot, now, budgets)
                        : snapshotCalculator.calculateExpenseLines(List.of(line), periodAccumulators.expenseByLineId, snapshot, now, budgets);
                BudgetSnapshotLine result = calculation.lines.get(0);
                result.setLinePeriodStart(observedStart);
                result.setLinePeriodEnd(naturalEnd);
                result.setLineObservedUntil(observedEnd);
                results.add(result);
            }
        }
        return results;
    }

    BigDecimal monthlyBudget(BudgetPlan plan, BigDecimal amount, YearMonth month) {
        BudgetPeriod period = plan.getPeriod() != null ? plan.getPeriod() : BudgetPeriod.MONTHLY;
        if (period == BudgetPeriod.MONTHLY) return amount;
        long months;
        long index;
        if (period == BudgetPeriod.QUARTERLY) {
            months = 3;
            index = (month.getMonthValue() - 1) % 3;
        } else if (period == BudgetPeriod.ANNUAL) {
            months = 12;
            index = month.getMonthValue() - 1;
        } else {
            YearMonth first = YearMonth.from(plan.getStartDate());
            YearMonth last = YearMonth.from(plan.getEndDate());
            if (month.isBefore(first) || month.isAfter(last)) return BigDecimal.ZERO;
            months = ChronoUnit.MONTHS.between(first, last) + 1;
            index = ChronoUnit.MONTHS.between(first, month);
        }
        BigDecimal base = amount.divide(BigDecimal.valueOf(months), 2, RoundingMode.DOWN);
        return index == months - 1 ? amount.subtract(base.multiply(BigDecimal.valueOf(months - 1))) : base;
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

    private void processTransactions(List<BankingTransactionDTO> transactions, Map<Long, BankingCategoryDTO> categoriesById, List<BudgetPlanLine> expenseLines, List<BudgetPlanLine> incomeLines, TransactionAccumulators accumulators, String currency) {
        for (BankingTransactionDTO transaction : transactions) {
            if (transaction.getAmount() == null || transaction.getCategoryId() == null) {
                continue;
            }

            BankingCategoryDTO txCategory = categoriesById.get(transaction.getCategoryId());
            if (txCategory == null) {
                continue;
            }

            if (transaction.getAmount() < 0) {
                accumulateExpense(transaction, expenseLines, accumulators.expenseByLineId, txCategory, currency);
            } else if (transaction.getAmount() > 0) {
                accumulateIncome(transaction, incomeLines, accumulators.incomeByLineId, txCategory, currency);
            }
        }
    }

    private void accumulateExpense(BankingTransactionDTO transaction, List<BudgetPlanLine> expenseLines,Map<Long, Accumulator> expenseByLineId, BankingCategoryDTO txCategory, String currency) {
        BudgetPlanLine matched = transactionMatcher.findMatchingLine(expenseLines, txCategory);
        if (matched != null) {
            validateTransactionCurrency(transaction, currency);
            expenseByLineId.computeIfAbsent(matched.getId(), k -> new Accumulator())
                    .add(BigDecimal.valueOf(transaction.getAmount()).abs());
        }
    }

    private void accumulateIncome(BankingTransactionDTO transaction, List<BudgetPlanLine> incomeLines, Map<Long, Accumulator> incomeByLineId, BankingCategoryDTO txCategory, String currency) {
        BudgetPlanLine matched = transactionMatcher.findMatchingLine(incomeLines, txCategory);
        if (matched != null) {
            validateTransactionCurrency(transaction, currency);
            incomeByLineId.computeIfAbsent(matched.getId(), k -> new Accumulator())
                    .add(BigDecimal.valueOf(transaction.getAmount()));
        }
    }

    private void validateTransactionCurrency(BankingTransactionDTO transaction, String currency) {
        if (transaction.getCurrency() == null || !transaction.getCurrency().equalsIgnoreCase(currency)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Budget transactions must use the plan currency; automatic conversion is not available");
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
            return planRepository.findByIdAndTenantIdForUpdate(request.getId(), tenantId)
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
                .linePeriod(lineRequest.getLinePeriod())
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
