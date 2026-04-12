package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.exception.TransactionValidationException;
import es.triana.company.banking.service.mapper.TransactionMapper;

class CSVExporterServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private CSVExporterService csvExporterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldRejectNullTenantId() {
        TransactionValidationException exception = assertThrows(TransactionValidationException.class,
                () -> csvExporterService.exportTransactions(null, null));

        assertEquals("Tenant id is required", exception.getMessage());
    }

    @Test
    void shouldExportAllTransactionsForTenant() {
        // Arrange
        Transaction tx1 = Transaction.builder()
                .id(1L)
                .tenantId(TENANT_ID)
                .bookingDate(LocalDate.of(2025, 1, 15))
                .valueDate(LocalDate.of(2025, 1, 15))
                .amount(java.math.BigDecimal.valueOf(100.00))
                .currency("EUR")
                .descriptionRaw("Payment A")
                .externalTxId("EXT-001")
                .build();

        Transaction tx2 = Transaction.builder()
                .id(2L)
                .tenantId(TENANT_ID)
                .bookingDate(LocalDate.of(2025, 1, 16))
                .valueDate(LocalDate.of(2025, 1, 16))
                .amount(java.math.BigDecimal.valueOf(50.00))
                .currency("EUR")
                .descriptionRaw("Payment B")
                .externalTxId("EXT-002")
                .build();

        List<Transaction> transactions = Arrays.asList(tx1, tx2);

        TransactionDTO dto1 = TransactionDTO.builder()
                .sourceAccountId(ACCOUNT_ID)
                .bookingDate(LocalDateTime.of(2025, 1, 15, 0, 0))
                .amount(100.0)
                .currency("EUR")
                .description("Payment A")
                .externalId("EXT-001")
                .build();

        TransactionDTO dto2 = TransactionDTO.builder()
                .sourceAccountId(ACCOUNT_ID)
                .bookingDate(LocalDateTime.of(2025, 1, 16, 0, 0))
                .amount(50.0)
                .currency("EUR")
                .description("Payment B")
                .externalId("EXT-002")
                .build();

        when(transactionRepository.findAllForExport(TENANT_ID, null, null, null))
                .thenReturn(transactions);
        when(transactionMapper.toDto(tx1)).thenReturn(dto1);
        when(transactionMapper.toDto(tx2)).thenReturn(dto2);

        // Act
        byte[] csvContent = csvExporterService.exportTransactions(TENANT_ID, null);

        // Assert
        assertNotNull(csvContent);
        assertTrue(csvContent.length > 0);

        String csvString = new String(csvContent, StandardCharsets.UTF_8);
        assertTrue(csvString.contains("source_account_id,booking_date,value_date,amount,currency,merchant,merchant_id,description"));
        assertTrue(csvString.contains("Payment A"));
        assertTrue(csvString.contains("Payment B"));
        assertTrue(csvString.contains("100"));
        assertTrue(csvString.contains("50"));
        assertTrue(csvString.contains("EXT-001"));
        assertTrue(csvString.contains("EXT-002"));
    }

    @Test
    void shouldExportTransactionsFilteredByAccount() {
        // Arrange
        Transaction tx1 = Transaction.builder()
                .id(1L)
                .tenantId(TENANT_ID)
                .bookingDate(LocalDate.of(2025, 1, 15))
                .amount(java.math.BigDecimal.valueOf(100.00))
                .currency("EUR")
                .descriptionRaw("Account specific payment")
                .build();

        List<Transaction> transactions = Arrays.asList(tx1);

        TransactionDTO dto1 = TransactionDTO.builder()
                .sourceAccountId(ACCOUNT_ID)
                .bookingDate(LocalDateTime.of(2025, 1, 15, 0, 0))
                .amount(100.0)
                .currency("EUR")
                .description("Account specific payment")
                .build();

        when(transactionRepository.findAllForExport(TENANT_ID, ACCOUNT_ID, null, null))
                .thenReturn(transactions);
        when(transactionMapper.toDto(tx1)).thenReturn(dto1);

        // Act
        byte[] csvContent = csvExporterService.exportTransactions(TENANT_ID, ACCOUNT_ID);

        // Assert
        assertNotNull(csvContent);
        assertTrue(csvContent.length > 0);

        String csvString = new String(csvContent, StandardCharsets.UTF_8);
        assertTrue(csvString.contains("Account specific payment"));
    }

    @Test
    void shouldExportEmptyCSVWhenNoTransactions() {
        // Arrange
        when(transactionRepository.findAllForExport(TENANT_ID, null, null, null))
                .thenReturn(Arrays.asList());

        // Act
        byte[] csvContent = csvExporterService.exportTransactions(TENANT_ID, null);

        // Assert
        assertNotNull(csvContent);
        String csvString = new String(csvContent, StandardCharsets.UTF_8);
        // Should contain header but no data rows
        assertTrue(csvString.contains("source_account_id,booking_date,value_date,amount,currency,merchant,merchant_id,description"));
    }

    @Test
    void shouldFormatTagIdsWithSemicolonSeparator() {
        // Arrange
        Transaction tx = Transaction.builder()
                .id(1L)
                .tenantId(TENANT_ID)
                .bookingDate(LocalDate.of(2025, 1, 15))
                .amount(java.math.BigDecimal.valueOf(100.00))
                .currency("EUR")
                .descriptionRaw("Payment with tags")
                .build();

        TransactionDTO dto = TransactionDTO.builder()
                .sourceAccountId(ACCOUNT_ID)
                .bookingDate(LocalDateTime.of(2025, 1, 15, 0, 0))
                .amount(100.0)
                .currency("EUR")
                .description("Payment with tags")
                .tagIds(Arrays.asList(1L, 2L, 3L))
                .build();

        when(transactionRepository.findAllForExport(TENANT_ID, null, null, null))
                .thenReturn(Arrays.asList(tx));
        when(transactionMapper.toDto(tx)).thenReturn(dto);

        // Act
        byte[] csvContent = csvExporterService.exportTransactions(TENANT_ID, null);

        // Assert
        String csvString = new String(csvContent, StandardCharsets.UTF_8);
        assertTrue(csvString.contains("1;2;3")); // Tags should be semicolon-separated
    }

    @Test
    void shouldExportTransactionsFilteredByDateRange() {
        Transaction tx1 = Transaction.builder()
                .id(1L)
                .tenantId(TENANT_ID)
                .bookingDate(LocalDate.of(2025, 1, 10))
                .amount(java.math.BigDecimal.valueOf(40.00))
                .currency("EUR")
                .descriptionRaw("Inside range")
                .build();

        Transaction tx2 = Transaction.builder()
                .id(2L)
                .tenantId(TENANT_ID)
                .bookingDate(LocalDate.of(2025, 2, 10))
                .amount(java.math.BigDecimal.valueOf(60.00))
                .currency("EUR")
                .descriptionRaw("Outside range")
                .build();

        TransactionDTO dto1 = TransactionDTO.builder()
                .sourceAccountId(ACCOUNT_ID)
                .bookingDate(LocalDateTime.of(2025, 1, 10, 0, 0))
                .amount(40.0)
                .currency("EUR")
                .description("Inside range")
                .build();

        when(transactionRepository.findAllForExport(
                TENANT_ID,
                null,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31)))
                        .thenReturn(Arrays.asList(tx1));
        when(transactionMapper.toDto(tx1)).thenReturn(dto1);

        byte[] csvContent = csvExporterService.exportTransactions(TENANT_ID, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        String csvString = new String(csvContent, StandardCharsets.UTF_8);
        assertTrue(csvString.contains("Inside range"));
        assertTrue(!csvString.contains("Outside range"));
    }

    @Test
    void shouldRejectInvalidDateRange() {
        TransactionValidationException exception = assertThrows(TransactionValidationException.class,
                () -> csvExporterService.exportTransactions(
                        TENANT_ID,
                        null,
                        LocalDate.of(2025, 2, 1),
                        LocalDate.of(2025, 1, 1)));

        assertEquals("startDate must be before or equal to endDate", exception.getMessage());
    }
}
