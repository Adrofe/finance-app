package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Category;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.CategoryRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.mapper.TransactionMapper;

class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Spy
    private TransactionMapper transactionMapper = new TransactionMapper();

    @Mock
    private AccountsService accountsService;

    @InjectMocks
    private TransactionService transactionService;

    private static final Long TENANT_ID = 1L;
    private static final Long OTHER_TENANT_ID = 2L;
    private static final Long SOURCE_ACCOUNT_ID = 10L;
    private static final Long DESTINATION_ACCOUNT_ID = 20L;
    private static final Long TRANSACTION_ID = 100L;
    private static final Long CATEGORY_ID = 5L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // =========================================================================
    // Helper builders
    // =========================================================================

    private TransactionDTO buildValidDto() {
        return TransactionDTO.builder()
                .sourceAccountId(SOURCE_ACCOUNT_ID)
                .destinationAccountId(DESTINATION_ACCOUNT_ID)
                .bookingDate(LocalDateTime.of(2026, 1, 15, 0, 0))
                .valueDate(LocalDateTime.of(2026, 1, 15, 0, 0))
                .amount(250.0)
                .currency("EUR")
                .description("Test transaction")
                .externalId("EXT-001")
                .statusId(1L)
                .typeId(1L)
                .categoryId(CATEGORY_ID)
                .merchantId(3L)
                .build();
    }

    private Category buildCategory(Long id) {
        return Category.builder()
                .id(id)
                .name("Test Category")
                .code("TEST")
                .isFixed(Boolean.TRUE)
                .build();
    }

    private Account buildAccount(Long id, Long tenantId) {
        return Account.builder()
                .id(id)
                .tenantId(tenantId)
                .name("Test Account")
                .lastBalanceReal(1000.0)
                .lastBalanceAvailable(950.0)
                .build();
    }

    private Transaction buildTransaction(Long id, Long tenantId) {
        return Transaction.builder()
                .id(id)
                .tenantId(tenantId)
                .sourceAccountId(SOURCE_ACCOUNT_ID)
                .destinationAccountId(DESTINATION_ACCOUNT_ID)
                .bookingDate(LocalDate.of(2026, 1, 15))
                .valueDate(LocalDate.of(2026, 1, 15))
                .amount(BigDecimal.valueOf(250.0))
                .currency("EUR")
                .descriptionRaw("Test transaction")
                .externalTxId("EXT-001")
                .statusId(1L)
                .transactionType(1L)
                .category(buildCategory(CATEGORY_ID))
                .merchantId(3L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void stubAccountLookup(Long accountId, Account account) {
        when(accountsRepository.findById(accountId)).thenReturn(Optional.of(account));
    }

    private void stubBothAccounts() {
        stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
        stubAccountLookup(DESTINATION_ACCOUNT_ID, buildAccount(DESTINATION_ACCOUNT_ID, TENANT_ID));
    }

    private void stubCategoryLookup(Long categoryId) {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(buildCategory(categoryId)));
    }

    // =========================================================================
    // createTransaction
    // =========================================================================
    @Nested
    @DisplayName("createTransaction")
    class CreateTransaction {

        @Test
        @DisplayName("should create transaction with valid data")
        void createValidTransaction() {
            TransactionDTO dto = buildValidDto();
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            TransactionDTO result = transactionService.createTransaction(dto, TENANT_ID);

            assertNotNull(result);
            assertEquals(250.0, result.getAmount());
            assertEquals("EUR", result.getCurrency());
            verify(transactionRepository).save(any(Transaction.class));
        }

        @Test
        @DisplayName("should create transaction without destination account (single-account tx)")
        void createWithoutDestination() {
            TransactionDTO dto = buildValidDto();
            dto.setDestinationAccountId(null);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setDestinationAccountId(null);

            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            TransactionDTO result = transactionService.createTransaction(dto, TENANT_ID);

            assertNotNull(result);
            assertNull(result.getDestinationAccountId());
        }

        @Test
        @DisplayName("should normalize currency to uppercase 3-letter ISO code")
        void normalizeCurrencyToUppercase() {
            TransactionDTO dto = buildValidDto();
            dto.setCurrency("eur");
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);

            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            transactionService.createTransaction(dto, TENANT_ID);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertEquals("EUR", captor.getValue().getCurrency());
        }

        // --- Validation: null payload ---
        @Test
        @DisplayName("should reject null payload")
        void rejectNullPayload() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(null, TENANT_ID));
            assertEquals("Transaction payload is required", ex.getMessage());
        }

        // --- Validation: null tenant (BR-004, FR-009) ---
        @Test
        @DisplayName("should reject null tenantId [BR-004, FR-009]")
        void rejectNullTenantId() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(buildValidDto(), null));
            assertEquals("Tenant id is required", ex.getMessage());
        }

        // --- Validation: tenant mismatch (BR-004, BR-007) ---
        @Test
        @DisplayName("should reject when DTO tenantId does not match authenticated tenant [BR-004]")
        void rejectTenantMismatch() {
            TransactionDTO dto = buildValidDto();
            dto.setTenantId(OTHER_TENANT_ID);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Transaction tenant does not match authenticated tenant", ex.getMessage());
        }

        // --- Validation: missing source account ---
        @Test
        @DisplayName("should reject null source account id")
        void rejectNullSourceAccount() {
            TransactionDTO dto = buildValidDto();
            dto.setSourceAccountId(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Source account id is required", ex.getMessage());
        }

        // --- Validation: missing booking date ---
        @Test
        @DisplayName("should reject null booking date")
        void rejectNullBookingDate() {
            TransactionDTO dto = buildValidDto();
            dto.setBookingDate(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Booking date is required", ex.getMessage());
        }

        // --- Validation: zero amount ---
        @Test
        @DisplayName("should reject zero amount")
        void rejectZeroAmount() {
            TransactionDTO dto = buildValidDto();
            dto.setAmount(0.0);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Amount must be non-zero", ex.getMessage());
        }

        // --- Validation: negative amount is allowed (expenses are negative) ---
        @Test
        @DisplayName("should accept negative amount for expenses")
        void acceptNegativeAmount() {
            TransactionDTO dto = buildValidDto();
            dto.setAmount(-50.0);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setAmount(BigDecimal.valueOf(-50.0));

            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            TransactionDTO result = transactionService.createTransaction(dto, TENANT_ID);
            assertNotNull(result);
            assertEquals(-50.0, result.getAmount());
        }

        // --- Validation: null amount ---
        @Test
        @DisplayName("should reject null amount")
        void rejectNullAmount() {
            TransactionDTO dto = buildValidDto();
            dto.setAmount(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Amount must be provided", ex.getMessage());
        }

        // --- Validation: invalid currency format ---
        @Test
        @DisplayName("should reject invalid currency format (not 3-letter ISO)")
        void rejectInvalidCurrency() {
            TransactionDTO dto = buildValidDto();
            dto.setCurrency("EURO");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Currency must be a 3-letter uppercase ISO code", ex.getMessage());
        }

        @Test
        @DisplayName("should reject null currency")
        void rejectNullCurrency() {
            TransactionDTO dto = buildValidDto();
            dto.setCurrency(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Currency must be a 3-letter uppercase ISO code", ex.getMessage());
        }

        @Test
        @DisplayName("should reject numeric currency")
        void rejectNumericCurrency() {
            TransactionDTO dto = buildValidDto();
            dto.setCurrency("123");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertEquals("Currency must be a 3-letter uppercase ISO code", ex.getMessage());
        }

        // --- Validation: source account not found ---
        @Test
        @DisplayName("should reject when source account does not exist")
        void rejectNonExistentSourceAccount() {
            TransactionDTO dto = buildValidDto();
            when(accountsRepository.findById(SOURCE_ACCOUNT_ID)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertTrue(ex.getMessage().contains("Source account not found"));
        }

        // --- Validation: source account belongs to different tenant (BR-004) ---
        @Test
        @DisplayName("should reject source account from different tenant [BR-004]")
        void rejectSourceAccountOtherTenant() {
            TransactionDTO dto = buildValidDto();
            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, OTHER_TENANT_ID));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertTrue(ex.getMessage().contains("Source account not found"));
        }

        // --- Validation: destination account not found ---
        @Test
        @DisplayName("should reject when destination account does not exist")
        void rejectNonExistentDestinationAccount() {
            TransactionDTO dto = buildValidDto();
            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
            when(accountsRepository.findById(DESTINATION_ACCOUNT_ID)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertTrue(ex.getMessage().contains("Destination account not found"));
        }

        // --- Validation: destination account belongs to different tenant (BR-004) ---
        @Test
        @DisplayName("should reject destination account from different tenant [BR-004]")
        void rejectDestinationAccountOtherTenant() {
            TransactionDTO dto = buildValidDto();
            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
            stubAccountLookup(DESTINATION_ACCOUNT_ID, buildAccount(DESTINATION_ACCOUNT_ID, OTHER_TENANT_ID));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertTrue(ex.getMessage().contains("Destination account not found"));
        }

        // --- Idempotency: duplicate external_tx_id (DDT §6) ---
        @Test
        @DisplayName("should reject duplicate external_tx_id within same tenant [idempotency]")
        void rejectDuplicateExternalId() {
            TransactionDTO dto = buildValidDto();
            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.createTransaction(dto, TENANT_ID));
            assertTrue(ex.getMessage().contains("already exists"));
        }

        // --- Idempotency: same external_tx_id for different tenants is allowed ---
        @Test
        @DisplayName("should allow same external_tx_id for different tenants [FR-009]")
        void allowSameExternalIdDifferentTenant() {
            TransactionDTO dto = buildValidDto();
            dto.setTenantId(null);
            Transaction saved = buildTransaction(TRANSACTION_ID, OTHER_TENANT_ID);

            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, OTHER_TENANT_ID));
            stubAccountLookup(DESTINATION_ACCOUNT_ID, buildAccount(DESTINATION_ACCOUNT_ID, OTHER_TENANT_ID));
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(OTHER_TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            TransactionDTO result = transactionService.createTransaction(dto, OTHER_TENANT_ID);
            assertNotNull(result);
        }

        // --- Null external id should be accepted (no idempotency check) ---
        @Test
        @DisplayName("should accept null externalId without idempotency check")
        void acceptNullExternalId() {
            TransactionDTO dto = buildValidDto();
            dto.setExternalId(null);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setExternalTxId(null);

            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            TransactionDTO result = transactionService.createTransaction(dto, TENANT_ID);
            assertNotNull(result);
            verify(transactionRepository, never()).existsByTenantIdAndExternalTxId(anyLong(), anyString());
        }

        // --- DTO tenantId matching authenticated tenant is valid ---
        @Test
        @DisplayName("should accept when DTO tenantId matches authenticated tenant")
        void acceptMatchingTenantId() {
            TransactionDTO dto = buildValidDto();
            dto.setTenantId(TENANT_ID);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);

            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            TransactionDTO result = transactionService.createTransaction(dto, TENANT_ID);
            assertNotNull(result);
        }

        // --- Default statusId when not provided ---
        @Test
        @DisplayName("should default statusId to 1 when not provided [BR-005]")
        void defaultStatusId() {
            TransactionDTO dto = buildValidDto();
            dto.setStatusId(null);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);

            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            transactionService.createTransaction(dto, TENANT_ID);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertEquals(1L, captor.getValue().getStatusId());
        }

        @Test
        @DisplayName("should update balance for simple transaction (no destination)")
        void updateBalanceForSimpleTransaction() {
            TransactionDTO dto = buildValidDto();
            dto.setDestinationAccountId(null);
            dto.setAmount(100.0);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setDestinationAccountId(null);
            saved.setAmount(BigDecimal.valueOf(100.0));

            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            transactionService.createTransaction(dto, TENANT_ID);

            // Should call updateAccountBalance once for source account with the amount directly
            verify(accountsService).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(100.0))
            );
            verify(accountsService, times(1)).updateAccountBalance(anyLong(), anyLong(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("should update balance for simple transaction with negative amount (expense)")
        void updateBalanceForExpense() {
            TransactionDTO dto = buildValidDto();
            dto.setDestinationAccountId(null);
            dto.setAmount(-50.0);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setDestinationAccountId(null);
            saved.setAmount(BigDecimal.valueOf(-50.0));

            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            transactionService.createTransaction(dto, TENANT_ID);

            // Should subtract from balance (negative amount)
            verify(accountsService).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(-50.0))
            );
        }

        @Test
        @DisplayName("should update balance for both accounts in transfer")
        void updateBalanceForTransfer() {
            TransactionDTO dto = buildValidDto();
            dto.setAmount(200.0);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setAmount(BigDecimal.valueOf(200.0));

            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            transactionService.createTransaction(dto, TENANT_ID);

            // Should subtract from source account
            verify(accountsService).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(-200.0))
            );
            // Should add to destination account
            verify(accountsService).updateAccountBalance(
                eq(DESTINATION_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(200.0))
            );
            // Should be called exactly twice
            verify(accountsService, times(2)).updateAccountBalance(anyLong(), anyLong(), any(BigDecimal.class));
        }
    }

    // =========================================================================
    // getTransactionById
    // =========================================================================
    @Nested
    @DisplayName("getTransactionById")
    class GetTransactionById {

        @Test
        @DisplayName("should return transaction scoped to tenant [BR-004]")
        void getByIdSuccess() {
            Transaction tx = buildTransaction(TRANSACTION_ID, TENANT_ID);
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(tx));

            TransactionDTO result = transactionService.getTransactionById(TRANSACTION_ID, TENANT_ID);

            assertNotNull(result);
            assertEquals(250.0, result.getAmount());
            verify(transactionRepository).findByIdAndTenantId(TRANSACTION_ID, TENANT_ID);
        }

        @Test
        @DisplayName("should throw when transaction not found")
        void getByIdNotFound() {
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionById(TRANSACTION_ID, TENANT_ID));
            assertTrue(ex.getMessage().contains("Transaction not found"));
        }

        @Test
        @DisplayName("should reject null transactionId")
        void rejectNullTransactionId() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionById(null, TENANT_ID));
            assertEquals("Transaction id is required", ex.getMessage());
        }

        @Test
        @DisplayName("should reject null tenantId [BR-004]")
        void rejectNullTenantId() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionById(TRANSACTION_ID, null));
            assertEquals("Tenant id is required", ex.getMessage());
        }

        @Test
        @DisplayName("should not return transaction from different tenant [BR-004, BR-007]")
        void isolateBetweenTenants() {
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionById(TRANSACTION_ID, TENANT_ID));
        }
    }

    // =========================================================================
    // getTransactionsByAccount
    // =========================================================================
    @Nested
    @DisplayName("getTransactionsByAccount")
    class GetTransactionsByAccount {

        @Test
        @DisplayName("should return transactions for account (source or destination) [FR-005]")
        void getByAccountSuccess() {
            Account account = buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID);
            Transaction tx = buildTransaction(TRANSACTION_ID, TENANT_ID);

            stubAccountLookup(SOURCE_ACCOUNT_ID, account);
            when(transactionRepository.findAllByTenantIdAndAccountId(TENANT_ID, SOURCE_ACCOUNT_ID))
                    .thenReturn(List.of(tx));

            List<TransactionDTO> result = transactionService.getTransactionsByAccount(SOURCE_ACCOUNT_ID, TENANT_ID);

            assertEquals(1, result.size());
            assertEquals(250.0, result.get(0).getAmount());
        }

        @Test
        @DisplayName("should reject account from different tenant [BR-004]")
        void rejectAccountOtherTenant() {
            Account account = buildAccount(SOURCE_ACCOUNT_ID, OTHER_TENANT_ID);
            stubAccountLookup(SOURCE_ACCOUNT_ID, account);

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
        }

        @Test
        @DisplayName("should reject non-existent account")
        void rejectNonExistentAccount() {
            when(accountsRepository.findById(SOURCE_ACCOUNT_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
        }

        @Test
        @DisplayName("should return empty list when no transactions exist for account")
        void returnEmptyForNoTransactions() {
            Account account = buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID);
            stubAccountLookup(SOURCE_ACCOUNT_ID, account);
            when(transactionRepository.findAllByTenantIdAndAccountId(TENANT_ID, SOURCE_ACCOUNT_ID))
                    .thenReturn(Collections.emptyList());

            List<TransactionDTO> result = transactionService.getTransactionsByAccount(SOURCE_ACCOUNT_ID, TENANT_ID);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject null accountId")
        void rejectNullAccountId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByAccount(null, TENANT_ID));
        }
    }

    // =========================================================================
    // getTransactionsByTenant
    // =========================================================================
    @Nested
    @DisplayName("getTransactionsByTenant")
    class GetTransactionsByTenant {

        @Test
        @DisplayName("should return all transactions for tenant [FR-009]")
        void getByTenantSuccess() {
            Transaction tx1 = buildTransaction(100L, TENANT_ID);
            Transaction tx2 = buildTransaction(101L, TENANT_ID);

            when(transactionRepository.findAllByTenantIdOrderByBookingDateDescIdDesc(TENANT_ID))
                    .thenReturn(List.of(tx1, tx2));

            List<TransactionDTO> result = transactionService.getTransactionsByTenant(TENANT_ID);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty list when tenant has no transactions")
        void returnEmptyForTenantNoTransactions() {
            when(transactionRepository.findAllByTenantIdOrderByBookingDateDescIdDesc(TENANT_ID))
                    .thenReturn(Collections.emptyList());

            List<TransactionDTO> result = transactionService.getTransactionsByTenant(TENANT_ID);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject null tenantId [BR-004]")
        void rejectNullTenantId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByTenant(null));
        }
    }

    // =========================================================================
    // getTransactionsByDateRange
    // =========================================================================
    @Nested
    @DisplayName("getTransactionsByDateRange [FR-005]")
    class GetTransactionsByDateRange {

        private final LocalDate START = LocalDate.of(2026, 1, 1);
        private final LocalDate END = LocalDate.of(2026, 1, 31);

        @Test
        @DisplayName("should return transactions within date range")
        void getByDateRangeSuccess() {
            Transaction tx = buildTransaction(TRANSACTION_ID, TENANT_ID);
            when(transactionRepository
                    .findAllByTenantIdAndBookingDateBetweenOrderByBookingDateDescIdDesc(TENANT_ID, START, END))
                    .thenReturn(List.of(tx));

            List<TransactionDTO> result = transactionService.getTransactionsByDateRange(START, END, TENANT_ID);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("should accept same start and end date")
        void acceptSameStartEnd() {
            when(transactionRepository
                    .findAllByTenantIdAndBookingDateBetweenOrderByBookingDateDescIdDesc(TENANT_ID, START, START))
                    .thenReturn(Collections.emptyList());

            List<TransactionDTO> result = transactionService.getTransactionsByDateRange(START, START, TENANT_ID);
            assertNotNull(result);
        }

        @Test
        @DisplayName("should reject end date before start date")
        void rejectEndBeforeStart() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByDateRange(END, START, TENANT_ID));
            assertTrue(ex.getMessage().contains("End date must be greater than or equal to start date"));
        }

        @Test
        @DisplayName("should reject null start date")
        void rejectNullStartDate() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByDateRange(null, END, TENANT_ID));
        }

        @Test
        @DisplayName("should reject null end date")
        void rejectNullEndDate() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByDateRange(START, null, TENANT_ID));
        }

        @Test
        @DisplayName("should reject null tenantId")
        void rejectNullTenant() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByDateRange(START, END, null));
        }
    }

    // =========================================================================
    // updateTransaction
    // =========================================================================
    @Nested
    @DisplayName("updateTransaction")
    class UpdateTransaction {

        @Test
        @DisplayName("should update transaction with valid data")
        void updateSuccess() {
            TransactionDTO dto = buildValidDto();
            dto.setAmount(500.0);
            Transaction existing = buildTransaction(TRANSACTION_ID, TENANT_ID);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setAmount(BigDecimal.valueOf(500.0));

            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(existing));
            stubBothAccounts();
                stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxIdAndIdNot(TENANT_ID, "EXT-001", TRANSACTION_ID))
                    .thenReturn(false);
            when(transactionRepository.save(existing)).thenReturn(saved);

            TransactionDTO result = transactionService.updateTransaction(TRANSACTION_ID, dto, TENANT_ID);

            assertNotNull(result);
            assertEquals(500.0, result.getAmount());
            verify(transactionRepository).save(existing);
        }


        @Test
        @DisplayName("should revert old balance and apply new balance on update")
        void updateBalanceOnTransactionUpdate() {
            TransactionDTO dto = buildValidDto();
            dto.setAmount(300.0);
            dto.setDestinationAccountId(null);
            
            Transaction existing = buildTransaction(TRANSACTION_ID, TENANT_ID);
            existing.setAmount(BigDecimal.valueOf(100.0));
            existing.setDestinationAccountId(null);
            
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setAmount(BigDecimal.valueOf(300.0));
            saved.setDestinationAccountId(null);

            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(existing));
            stubAccountLookup(SOURCE_ACCOUNT_ID, buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID));
                stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxIdAndIdNot(TENANT_ID, "EXT-001", TRANSACTION_ID))
                    .thenReturn(false);
            when(transactionRepository.save(existing)).thenReturn(saved);

            transactionService.updateTransaction(TRANSACTION_ID, dto, TENANT_ID);

            // Should revert old balance: -100
            verify(accountsService).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(-100.0))
            );
            // Should apply new balance: +300
            verify(accountsService).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(300.0))
            );
            verify(accountsService, times(2)).updateAccountBalance(anyLong(), anyLong(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("should update balances when converting simple tx to transfer")
        void updateBalanceWhenConvertingToTransfer() {
            TransactionDTO dto = buildValidDto();
            dto.setDestinationAccountId(DESTINATION_ACCOUNT_ID);
            dto.setAmount(150.0);
            
            Transaction existing = buildTransaction(TRANSACTION_ID, TENANT_ID);
            existing.setAmount(BigDecimal.valueOf(150.0));
            existing.setDestinationAccountId(null); // Was simple transaction
            
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);
            saved.setAmount(BigDecimal.valueOf(150.0));
            saved.setDestinationAccountId(DESTINATION_ACCOUNT_ID); // Now transfer

            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(existing));
            stubBothAccounts();
                stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxIdAndIdNot(TENANT_ID, "EXT-001", TRANSACTION_ID))
                    .thenReturn(false);
            when(transactionRepository.save(existing)).thenReturn(saved);

            transactionService.updateTransaction(TRANSACTION_ID, dto, TENANT_ID);

                // The source account receives the same delta twice:
                // once to revert the original simple transaction, and once to apply the new transfer.
                verify(accountsService, times(2)).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(-150.0))
            );
            verify(accountsService).updateAccountBalance(
                eq(DESTINATION_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(150.0))
            );
            verify(accountsService, times(3)).updateAccountBalance(anyLong(), anyLong(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("should reject update when transaction not found [BR-004]")
        void rejectNotFound() {
            TransactionDTO dto = buildValidDto();
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.updateTransaction(TRANSACTION_ID, dto, TENANT_ID));
        }

        @Test
        @DisplayName("should reject update with duplicate externalId from another transaction [idempotency]")
        void rejectDuplicateExternalIdOnUpdate() {
            TransactionDTO dto = buildValidDto();
            Transaction existing = buildTransaction(TRANSACTION_ID, TENANT_ID);

            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(existing));
            stubBothAccounts();
                stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxIdAndIdNot(TENANT_ID, "EXT-001", TRANSACTION_ID))
                    .thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> transactionService.updateTransaction(TRANSACTION_ID, dto, TENANT_ID));
            assertTrue(ex.getMessage().contains("already exists"));
        }

        @Test
        @DisplayName("should allow update keeping same externalId [idempotency]")
        void allowSameExternalId() {
            TransactionDTO dto = buildValidDto();
            Transaction existing = buildTransaction(TRANSACTION_ID, TENANT_ID);

            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(existing));
            stubBothAccounts();
                stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxIdAndIdNot(TENANT_ID, "EXT-001", TRANSACTION_ID))
                    .thenReturn(false);
            when(transactionRepository.save(existing)).thenReturn(existing);

            TransactionDTO result = transactionService.updateTransaction(TRANSACTION_ID, dto, TENANT_ID);
            assertNotNull(result);
        }

        @Test
        @DisplayName("should revert balance when deleting simple transaction")
        void revertBalanceOnDeleteSimpleTransaction() {
            Transaction tx = buildTransaction(TRANSACTION_ID, TENANT_ID);
            tx.setAmount(BigDecimal.valueOf(75.0));
            tx.setDestinationAccountId(null);
            
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(tx));

            transactionService.deleteTransaction(TRANSACTION_ID, TENANT_ID);

            // Should revert: subtract the amount
            verify(accountsService).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(-75.0))
            );
            verify(transactionRepository).delete(tx);
        }

        @Test
        @DisplayName("should revert balance for both accounts when deleting transfer")
        void revertBalanceOnDeleteTransfer() {
            Transaction tx = buildTransaction(TRANSACTION_ID, TENANT_ID);
            tx.setAmount(BigDecimal.valueOf(500.0));
            
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(tx));

            transactionService.deleteTransaction(TRANSACTION_ID, TENANT_ID);

            // Should revert: add back to source (was subtracted)
            verify(accountsService).updateAccountBalance(
                eq(SOURCE_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(500.0))
            );
            // Should revert: subtract from destination (was added)
            verify(accountsService).updateAccountBalance(
                eq(DESTINATION_ACCOUNT_ID), 
                eq(TENANT_ID), 
                eq(BigDecimal.valueOf(-500.0))
            );
            verify(accountsService, times(2)).updateAccountBalance(anyLong(), anyLong(), any(BigDecimal.class));
            verify(transactionRepository).delete(tx);
        }

        @Test
        @DisplayName("should re-validate source account on update")
        void revalidateSourceOnUpdate() {
            TransactionDTO dto = buildValidDto();
            Transaction existing = buildTransaction(TRANSACTION_ID, TENANT_ID);

            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(existing));
            when(accountsRepository.findById(SOURCE_ACCOUNT_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.updateTransaction(TRANSACTION_ID, dto, TENANT_ID));
        }

        @Test
        @DisplayName("should reject null transactionId")
        void rejectNullTransactionId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.updateTransaction(null, buildValidDto(), TENANT_ID));
        }

        @Test
        @DisplayName("should reject null tenantId")
        void rejectNullTenantId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.updateTransaction(TRANSACTION_ID, buildValidDto(), null));
        }
    }

    // =========================================================================
    // deleteTransaction
    // =========================================================================
    @Nested
    @DisplayName("deleteTransaction")
    class DeleteTransaction {

        @Test
        @DisplayName("should delete transaction belonging to tenant")
        void deleteSuccess() {
            Transaction tx = buildTransaction(TRANSACTION_ID, TENANT_ID);
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(tx));

            transactionService.deleteTransaction(TRANSACTION_ID, TENANT_ID);

            verify(transactionRepository).delete(tx);
        }

        @Test
        @DisplayName("should reject delete when transaction not found")
        void rejectNotFound() {
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.deleteTransaction(TRANSACTION_ID, TENANT_ID));
        }

        @Test
        @DisplayName("should not delete transaction from different tenant [BR-004]")
        void isolateDelete() {
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.deleteTransaction(TRANSACTION_ID, TENANT_ID));
            verify(transactionRepository, never()).delete(any(Transaction.class));
        }

        @Test
        @DisplayName("should reject null transactionId")
        void rejectNullTransactionId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.deleteTransaction(null, TENANT_ID));
        }

        @Test
        @DisplayName("should reject null tenantId")
        void rejectNullTenantId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.deleteTransaction(TRANSACTION_ID, null));
        }
    }

    // =========================================================================
    // getAccountBalance
    // =========================================================================
    @Nested
    @DisplayName("getAccountBalance")
    class GetAccountBalance {

        @Test
        @DisplayName("should return lastBalanceReal when available")
        void returnBalanceReal() {
            Account account = buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID);
            account.setLastBalanceReal(1500.0);
            account.setLastBalanceAvailable(1200.0);
            stubAccountLookup(SOURCE_ACCOUNT_ID, account);

            Double balance = transactionService.getAccountBalance(SOURCE_ACCOUNT_ID, TENANT_ID);

            assertEquals(1500.0, balance);
        }

        @Test
        @DisplayName("should fall back to lastBalanceAvailable when real is null")
        void fallbackToBalanceAvailable() {
            Account account = buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID);
            account.setLastBalanceReal(null);
            account.setLastBalanceAvailable(800.0);
            stubAccountLookup(SOURCE_ACCOUNT_ID, account);

            Double balance = transactionService.getAccountBalance(SOURCE_ACCOUNT_ID, TENANT_ID);

            assertEquals(800.0, balance);
        }

        @Test
        @DisplayName("should return 0.0 when both balances are null")
        void returnZeroWhenBothNull() {
            Account account = buildAccount(SOURCE_ACCOUNT_ID, TENANT_ID);
            account.setLastBalanceReal(null);
            account.setLastBalanceAvailable(null);
            stubAccountLookup(SOURCE_ACCOUNT_ID, account);

            Double balance = transactionService.getAccountBalance(SOURCE_ACCOUNT_ID, TENANT_ID);

            assertEquals(0.0, balance);
        }

        @Test
        @DisplayName("should throw when account not found")
        void throwWhenAccountNotFound() {
            when(accountsRepository.findById(SOURCE_ACCOUNT_ID)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class,
                    () -> transactionService.getAccountBalance(SOURCE_ACCOUNT_ID, TENANT_ID));
        }

        @Test
        @DisplayName("should throw when account belongs to different tenant [BR-004]")
        void throwWhenAccountOtherTenant() {
            Account account = buildAccount(SOURCE_ACCOUNT_ID, OTHER_TENANT_ID);
            stubAccountLookup(SOURCE_ACCOUNT_ID, account);

            assertThrows(NoSuchElementException.class,
                    () -> transactionService.getAccountBalance(SOURCE_ACCOUNT_ID, TENANT_ID));
        }

        @Test
        @DisplayName("should reject null accountId")
        void rejectNullAccountId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getAccountBalance(null, TENANT_ID));
        }

        @Test
        @DisplayName("should reject null tenantId")
        void rejectNullTenantId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getAccountBalance(SOURCE_ACCOUNT_ID, null));
        }
    }

    // =========================================================================
    // getTenantBalance
    // =========================================================================
    @Nested
    @DisplayName("getTenantBalance")
    class GetTenantBalance {

        @Test
        @DisplayName("should aggregate balances of all tenant accounts")
        void aggregateBalance() {
            Account acc1 = buildAccount(10L, TENANT_ID);
            acc1.setLastBalanceReal(1000.0);
            Account acc2 = buildAccount(11L, TENANT_ID);
            acc2.setLastBalanceReal(2000.0);

            when(accountsRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(acc1, acc2));

            Double balance = transactionService.getTenantBalance(TENANT_ID);

            assertEquals(3000.0, balance);
        }

        @Test
        @DisplayName("should use lastBalanceAvailable when real is null")
        void mixedBalances() {
            Account acc1 = buildAccount(10L, TENANT_ID);
            acc1.setLastBalanceReal(null);
            acc1.setLastBalanceAvailable(500.0);
            Account acc2 = buildAccount(11L, TENANT_ID);
            acc2.setLastBalanceReal(1500.0);

            when(accountsRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(acc1, acc2));

            Double balance = transactionService.getTenantBalance(TENANT_ID);

            assertEquals(2000.0, balance);
        }

        @Test
        @DisplayName("should return 0.0 for accounts with no balances")
        void zeroBalancesAccounts() {
            Account acc = buildAccount(10L, TENANT_ID);
            acc.setLastBalanceReal(null);
            acc.setLastBalanceAvailable(null);

            when(accountsRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(acc));

            Double balance = transactionService.getTenantBalance(TENANT_ID);

            assertEquals(0.0, balance);
        }

        @Test
        @DisplayName("should throw when no accounts found for tenant")
        void throwWhenNoAccounts() {
            when(accountsRepository.findByTenantId(TENANT_ID)).thenReturn(Collections.emptyList());

            assertThrows(NoSuchElementException.class,
                    () -> transactionService.getTenantBalance(TENANT_ID));
        }

        @Test
        @DisplayName("should throw when tenantId is null")
        void throwWhenNullTenantId() {
            assertThrows(NoSuchElementException.class,
                    () -> transactionService.getTenantBalance(null));
        }
    }

    // =========================================================================
    // getTransactionsByCategory (FR-005, BR-008)
    // =========================================================================
    @Nested
    @DisplayName("getTransactionsByCategory [FR-005, BR-008]")
    class GetTransactionsByCategory {

        private final Long CATEGORY_ID = 5L;

        @Test
        @DisplayName("should return transactions for tenant and category")
        void getByCategorySuccess() {
            Transaction tx = buildTransaction(TRANSACTION_ID, TENANT_ID);
            tx.setCategory(buildCategory(CATEGORY_ID));

            when(transactionRepository.findAllByTenantIdAndCategory_IdOrderByBookingDateDescIdDesc(TENANT_ID, CATEGORY_ID))
                    .thenReturn(List.of(tx));

            List<TransactionDTO> result = transactionService.getTransactionsByCategory(CATEGORY_ID, TENANT_ID);

            assertEquals(1, result.size());
            assertEquals(CATEGORY_ID, result.get(0).getCategoryId());
        }

        @Test
        @DisplayName("should return empty list when no transactions for category")
        void returnEmptyForNoTransactions() {
            when(transactionRepository.findAllByTenantIdAndCategory_IdOrderByBookingDateDescIdDesc(TENANT_ID, CATEGORY_ID))
                    .thenReturn(Collections.emptyList());

            List<TransactionDTO> result = transactionService.getTransactionsByCategory(CATEGORY_ID, TENANT_ID);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should reject null categoryId")
        void rejectNullCategoryId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByCategory(null, TENANT_ID));
        }

        @Test
        @DisplayName("should reject null tenantId [BR-004]")
        void rejectNullTenantId() {
            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.getTransactionsByCategory(CATEGORY_ID, null));
        }
    }

    // =========================================================================
    // Cross-cutting: Multi-tenant isolation (BR-004, BR-007, FR-009)
    // =========================================================================
    @Nested
    @DisplayName("Multi-tenant isolation [BR-004, BR-007, FR-009]")
    class MultiTenantIsolation {

        @Test
        @DisplayName("all read operations use tenant-scoped queries")
        void readOperationsAreTenantScoped() {
            // getTransactionById uses findByIdAndTenantId
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.of(buildTransaction(TRANSACTION_ID, TENANT_ID)));
            transactionService.getTransactionById(TRANSACTION_ID, TENANT_ID);
            verify(transactionRepository).findByIdAndTenantId(TRANSACTION_ID, TENANT_ID);

            // getTransactionsByTenant uses findAllByTenantIdOrderByBookingDateDescIdDesc
            when(transactionRepository.findAllByTenantIdOrderByBookingDateDescIdDesc(TENANT_ID))
                    .thenReturn(Collections.emptyList());
            transactionService.getTransactionsByTenant(TENANT_ID);
            verify(transactionRepository).findAllByTenantIdOrderByBookingDateDescIdDesc(TENANT_ID);
        }

        @Test
        @DisplayName("create enforces tenantId on the persisted entity")
        void createEnforcesTenant() {
            TransactionDTO dto = buildValidDto();
            dto.setTenantId(null);
            Transaction saved = buildTransaction(TRANSACTION_ID, TENANT_ID);

            stubBothAccounts();
            stubCategoryLookup(CATEGORY_ID);
            when(transactionRepository.existsByTenantIdAndExternalTxId(TENANT_ID, "EXT-001")).thenReturn(false);
            when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);

            transactionService.createTransaction(dto, TENANT_ID);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            assertEquals(TENANT_ID, captor.getValue().getTenantId());
        }

        @Test
        @DisplayName("delete uses tenant-scoped lookup before deleting")
        void deleteTenantScoped() {
            when(transactionRepository.findByIdAndTenantId(TRANSACTION_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> transactionService.deleteTransaction(TRANSACTION_ID, TENANT_ID));
            verify(transactionRepository, never()).deleteById(anyLong());
        }
    }
}
