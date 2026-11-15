package es.triana.company.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import es.triana.company.budget.mapper.BudgetMapper;
import es.triana.company.budget.model.api.BudgetPlanDTO;
import es.triana.company.budget.model.api.BudgetMonthDTO;
import es.triana.company.budget.model.api.BankingCategoryDTO;
import es.triana.company.budget.model.api.BankingTransactionDTO;
import es.triana.company.budget.model.BudgetLineType;
import es.triana.company.budget.model.BudgetPeriod;
import es.triana.company.budget.model.db.BudgetPlan;
import es.triana.company.budget.model.db.BudgetPlanLine;
import es.triana.company.budget.repository.BudgetPlanRepository;
import es.triana.company.budget.repository.BudgetSnapshotRepository;
import es.triana.company.budget.service.client.BankingApiClient;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {
    @Mock private BudgetPlanRepository plans;
    @Mock private BudgetSnapshotRepository snapshots;
    @Mock private BankingApiClient banking;
    @Mock private BudgetMapper mapper;
    @Mock private BudgetTransactionMatcher matcher;
    @Mock private BudgetSnapshotCalculator calculator;
    @InjectMocks private BudgetService service;

    @Test
    void marksPlanPrimaryWithinItsTenant() {
        BudgetPlan plan = BudgetPlan.builder().id(5L).tenantId(7L).primary(false).build();
        BudgetPlanDTO result = BudgetPlanDTO.builder().id(5L).primary(true).build();
        when(plans.findByIdAndTenantId(5L, 7L)).thenReturn(Optional.of(plan));
        when(plans.save(plan)).thenReturn(plan);
        when(mapper.toPlanDto(plan)).thenReturn(result);

        assertEquals(result, service.setPrimaryPlan(7L, 5L));
        assertEquals(true, plan.getPrimary());
        verify(plans).clearOtherPrimaryPlans(7L, 5L);
    }

    @Test
    void doesNotChangeOtherPlansWhenRequestedPlanIsNotInTenant() {
        when(plans.findByIdAndTenantId(5L, 8L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.setPrimaryPlan(8L, 5L));
        verify(plans, never()).clearOtherPrimaryPlans(8L, 5L);
    }

    @Test
    void calculatesEachMonthAndPreservesMonthsWithoutTransactions() {
        BudgetService realService = new BudgetService(plans, snapshots, banking, new BudgetMapper(), new BudgetTransactionMatcher(), new BudgetSnapshotCalculator());
        BudgetPlanLine expense = BudgetPlanLine.builder().id(1L).categoryId(10L).categoryCode("FOOD").categoryName("Food").budgetAmount(new BigDecimal("100.00")).lineType(BudgetLineType.EXPENSE).build();
        BudgetPlanLine income = BudgetPlanLine.builder().id(2L).categoryId(20L).categoryCode("INC").categoryName("Income").budgetAmount(new BigDecimal("200.00")).lineType(BudgetLineType.INCOME).build();
        BudgetPlan plan = BudgetPlan.builder().id(5L).tenantId(7L).currency("EUR").period(BudgetPeriod.MONTHLY).lines(List.of(expense, income)).build();
        LocalDate start = LocalDate.of(2025, 12, 1);
        LocalDate end = LocalDate.of(2026, 2, 15);
        when(plans.findByIdAndTenantIdForUpdate(5L, 7L)).thenReturn(Optional.of(plan));
        when(banking.getCategories("token")).thenReturn(List.of(
                BankingCategoryDTO.builder().id(10L).code("FOOD").name("Food").build(),
                BankingCategoryDTO.builder().id(20L).code("INC").name("Income").build()));
        when(banking.getTransactions("token", start, end)).thenReturn(List.of(
                BankingTransactionDTO.builder().categoryId(10L).bookingDate(start.atStartOfDay()).amount(-120.0).currency("EUR").build(),
                BankingTransactionDTO.builder().categoryId(10L).bookingDate(LocalDate.of(2026, 1, 10).atStartOfDay()).amount(-50.0).currency("EUR").build(),
                BankingTransactionDTO.builder().categoryId(20L).bookingDate(LocalDate.of(2026, 1, 10).atStartOfDay()).amount(250.0).currency("EUR").build()));
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = realService.refreshSnapshot(7L, "token", 5L, start, end);
        assertEquals(0, result.getTotalBudget().compareTo(new BigDecimal("300.00")));
        assertEquals(0, result.getTotalSpent().compareTo(new BigDecimal("170.00")));
        assertEquals(0, result.getTotalExpectedIncome().compareTo(new BigDecimal("600.00")));
        List<BudgetMonthDTO> months = result.getMonths();
        assertEquals(3, months.size());
        assertEquals(false, months.get(0).getCompliant());
        assertEquals(true, months.get(1).getCompliant());
        assertEquals(0, months.get(2).getTotalSpent().compareTo(BigDecimal.ZERO));
        assertEquals(2, result.getLines().size());
    }

    @Test
    void allocatesQuarterlyAndAnnualAmountsWithoutLosingCents() {
        BudgetPlan plan = BudgetPlan.builder().period(BudgetPeriod.QUARTERLY).build();
        BigDecimal amount = new BigDecimal("100.00");
        assertEquals(new BigDecimal("33.33"), service.monthlyBudget(plan, amount, YearMonth.of(2026, 1)));
        assertEquals(new BigDecimal("33.34"), service.monthlyBudget(plan, amount, YearMonth.of(2026, 3)));
        plan.setPeriod(BudgetPeriod.ANNUAL);
        assertEquals(new BigDecimal("8.33"), service.monthlyBudget(plan, amount, YearMonth.of(2026, 1)));
        assertEquals(new BigDecimal("8.37"), service.monthlyBudget(plan, amount, YearMonth.of(2026, 12)));
    }

    @Test
    void limitsCustomBudgetsToTheirDatesAndSplitsTheAmountAcrossMonths() {
        BudgetService realService = new BudgetService(plans, snapshots, banking, new BudgetMapper(), new BudgetTransactionMatcher(), new BudgetSnapshotCalculator());
        BudgetPlanLine expense = BudgetPlanLine.builder().id(1L).categoryId(10L).categoryCode("FOOD").categoryName("Food").budgetAmount(new BigDecimal("100.00")).lineType(BudgetLineType.EXPENSE).build();
        BudgetPlan plan = BudgetPlan.builder().id(5L).tenantId(7L).period(BudgetPeriod.CUSTOM).startDate(LocalDate.of(2026, 1, 15)).endDate(LocalDate.of(2026, 3, 2)).lines(List.of(expense)).build();
        when(plans.findByIdAndTenantIdForUpdate(5L, 7L)).thenReturn(Optional.of(plan));
        when(banking.getCategories("token")).thenReturn(List.of(BankingCategoryDTO.builder().id(10L).code("FOOD").name("Food").build()));
        when(banking.getTransactions("token", plan.getStartDate(), plan.getEndDate())).thenReturn(List.of());
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = realService.refreshSnapshot(7L, "token", 5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        assertEquals(plan.getStartDate(), result.getPeriodStart());
        assertEquals(plan.getEndDate(), result.getPeriodEnd());
        assertEquals(0, result.getTotalBudget().compareTo(new BigDecimal("100.00")));
        assertEquals(0, result.getMonths().get(0).getTotalBudget().compareTo(new BigDecimal("33.33")));
        assertEquals(0, result.getMonths().get(2).getTotalBudget().compareTo(new BigDecimal("33.34")));
        assertThrows(ResponseStatusException.class, () -> realService.refreshSnapshot(7L, "token", 5L, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1)));
    }

    @Test
    void refusesToMixCurrenciesInMatchedBudgetLines() {
        BudgetService realService = new BudgetService(plans, snapshots, banking, new BudgetMapper(), new BudgetTransactionMatcher(), new BudgetSnapshotCalculator());
        BudgetPlanLine expense = BudgetPlanLine.builder().id(1L).categoryId(10L).categoryCode("FOOD").categoryName("Food").budgetAmount(new BigDecimal("100.00")).lineType(BudgetLineType.EXPENSE).build();
        BudgetPlan plan = BudgetPlan.builder().id(5L).tenantId(7L).currency("EUR").period(BudgetPeriod.MONTHLY).lines(List.of(expense)).build();
        LocalDate date = LocalDate.of(2026, 1, 1);
        when(plans.findByIdAndTenantIdForUpdate(5L, 7L)).thenReturn(Optional.of(plan));
        when(banking.getCategories("token")).thenReturn(List.of(BankingCategoryDTO.builder().id(10L).code("FOOD").name("Food").build()));
        when(banking.getTransactions("token", date, date)).thenReturn(List.of(BankingTransactionDTO.builder().categoryId(10L).bookingDate(date.atStartOfDay()).amount(-10.0).currency("USD").build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> realService.refreshSnapshot(7L, "token", 5L, date, date));
        assertEquals(422, error.getStatusCode().value());
        verify(snapshots, never()).save(any());
    }
}