package es.triana.company.banking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.security.TenantContext;
import es.triana.company.banking.service.TransactionService;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionsControllerTest {

    @InjectMocks
    private TransactionsController transactionsController;

    @Mock
    private TransactionService transactionsService;

    @Mock
    private TenantContext tenantContext;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tenantContext.getCurrentTenantId()).thenReturn(1L);
    }

    @Test
    public void createValidTransaction() {
        TransactionDTO transactionDTO = buildTransactionDto();
        TransactionDTO createdTransaction = buildTransactionDto();
        Long tenantId = 1L;

        when(transactionsService.createTransaction(transactionDTO, tenantId)).thenReturn(createdTransaction);

        ResponseEntity<ApiResponse<TransactionDTO>> response = transactionsController.createTransaction(transactionDTO);

        verify(transactionsService).createTransaction(transactionDTO, tenantId);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(100.0, response.getBody().getData().getAmount());
    }

    @Test
    public void createTransactionWithInexistentAccount() {
        TransactionDTO transactionDTO = buildTransactionDto();
        Long tenantId = 1L;
        when(transactionsService.createTransaction(transactionDTO, tenantId))
            .thenThrow(new IllegalArgumentException("Source account not found with id: 1"));

        ResponseEntity<ApiResponse<TransactionDTO>> response = transactionsController.createTransaction(transactionDTO);

        verify(transactionsService).createTransaction(transactionDTO, tenantId);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Source account not found with id: 1", response.getBody().getMessage());
    }

    @Test
    public void createTransactionInvalidDestinationAccount() {
        TransactionDTO transactionDTO = buildTransactionDto();
        Long tenantId = 1L;
        when(transactionsService.createTransaction(transactionDTO, tenantId))
            .thenThrow(new IllegalArgumentException("Destination account not found with id: 2"));

        ResponseEntity<ApiResponse<TransactionDTO>> response = transactionsController.createTransaction(transactionDTO);

        verify(transactionsService).createTransaction(transactionDTO, tenantId);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Destination account not found with id: 2", response.getBody().getMessage());
    }

    @Test
    public void createTransactionWithInvalidCurrency() {
        TransactionDTO transactionDTO = buildTransactionDto();
        transactionDTO.setCurrency("US");
        Long tenantId = 1L;

        when(transactionsService.createTransaction(transactionDTO, tenantId))
            .thenThrow(new IllegalArgumentException("Currency must be a 3-letter uppercase ISO code"));

        ResponseEntity<ApiResponse<TransactionDTO>> response = transactionsController.createTransaction(transactionDTO);

        verify(transactionsService).createTransaction(transactionDTO, tenantId);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Currency must be a 3-letter uppercase ISO code", response.getBody().getMessage());
    }

    @Test
    public void getTransaction() {
        Long transactionId = 1L;
        TransactionDTO transactionDTO = buildTransactionDto();
        Long tenantId = 1L;

        when(transactionsService.getTransactionById(transactionId, tenantId)).thenReturn(transactionDTO);

        ResponseEntity<ApiResponse<TransactionDTO>> response = transactionsController.getTransactionById(transactionId);

        verify(transactionsService).getTransactionById(transactionId, tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(100.0, response.getBody().getData().getAmount());
    }

    @Test
    public void getTransactionsByAccount() {
        Long accountId = 1L;
        List<TransactionDTO> transactions = List.of(buildTransactionDto(), buildTransactionDto());
        Long tenantId = 1L;

        when(transactionsService.getTransactionsByAccount(accountId, tenantId)).thenReturn(transactions);

        ResponseEntity<ApiResponse<List<TransactionDTO>>> response = transactionsController.getTransactionsByAccount(accountId);

        verify(transactionsService).getTransactionsByAccount(accountId, tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getData().size());
    }

    @Test
    public void getTransactionsByTenant() {
        Long tenantId = 1L;
        List<TransactionDTO> transactions = List.of(buildTransactionDto());
        
        when(transactionsService.getTransactionsByTenant(tenantId)).thenReturn(transactions);

        ResponseEntity<ApiResponse<List<TransactionDTO>>> response = transactionsController.getTransactionsByTenant();

        verify(transactionsService).getTransactionsByTenant(tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    public void getTransactionsByDateRange() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        List<TransactionDTO> transactions = List.of(buildTransactionDto());
        Long tenantId = 1L;

        when(transactionsService.getTransactionsByDateRange(startDate, endDate, tenantId)).thenReturn(transactions);

        ResponseEntity<ApiResponse<List<TransactionDTO>>> response = transactionsController.getTransactionsByDateRange(startDate, endDate);

        verify(transactionsService).getTransactionsByDateRange(startDate, endDate, tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    public void getTransactionsByTag() {
        Long tagId = 7L;
        Long tenantId = 1L;
        List<TransactionDTO> transactions = List.of(buildTransactionDto());

        when(transactionsService.getTransactionsByTag(tagId, tenantId)).thenReturn(transactions);

        ResponseEntity<ApiResponse<List<TransactionDTO>>> response = transactionsController.getTransactionsByTag(tagId);

        verify(transactionsService).getTransactionsByTag(tagId, tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    public void updateTransaction() {
        Long transactionId = 1L;
        TransactionDTO transactionDTO = buildTransactionDto();
        Long tenantId = 1L;

        when(transactionsService.updateTransaction(transactionId, transactionDTO, tenantId)).thenReturn(transactionDTO);

        ResponseEntity<ApiResponse<TransactionDTO>> response = transactionsController.updateTransaction(transactionId, transactionDTO);

        verify(transactionsService).updateTransaction(transactionId, transactionDTO, tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Test transaction", response.getBody().getData().getDescription());
    }

    @Test
    public void updateTransactionWithInexistentAccount() {
        Long transactionId = 1L;
        TransactionDTO transactionDTO = buildTransactionDto();
        Long tenantId = 1L;

        when(transactionsService.updateTransaction(transactionId, transactionDTO, tenantId))
            .thenThrow(new IllegalArgumentException("Source account not found with id: 1"));

        ResponseEntity<ApiResponse<TransactionDTO>> response = transactionsController.updateTransaction(transactionId, transactionDTO);

        verify(transactionsService).updateTransaction(transactionId, transactionDTO, tenantId);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Source account not found with id: 1", response.getBody().getMessage());
    }

    @Test
    public void deleteTransaction() {
        Long transactionId = 1L;
        Long tenantId = 1L;

        ResponseEntity<ApiResponse<Void>> response = transactionsController.deleteTransaction(transactionId);

        verify(transactionsService).deleteTransaction(transactionId, tenantId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    public void deleteNonExistentTransaction() {
        Long transactionId = 999L;
        Long tenantId = 1L;
        doThrow(new NoSuchElementException("Transaction not found with id: 999")).when(transactionsService)
            .deleteTransaction(transactionId, tenantId);

        ResponseEntity<ApiResponse<Void>> response = transactionsController.deleteTransaction(transactionId);

        verify(transactionsService).deleteTransaction(transactionId, tenantId);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Transaction not found with id: 999", response.getBody().getMessage());
    }

    @Test
    public void getAccountBalance() {
        Long accountId = 1L;
        Double balance = 1000.0;
        Long tenantId = 1L;

        when(transactionsService.getAccountBalance(accountId, tenantId)).thenReturn(balance);

        ResponseEntity<ApiResponse<Double>> response = transactionsController.getAccountBalance(accountId);

        verify(transactionsService).getAccountBalance(accountId, tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1000.0, response.getBody().getData());
    }

    @Test
    public void getAccountBalanceForNonExistentAccount() {
        Long accountId = 999L;
        Long tenantId = 1L;
        when(transactionsService.getAccountBalance(accountId, tenantId)).thenThrow(new NoSuchElementException("Account not found with id: 999"));

        ResponseEntity<ApiResponse<Double>> response = transactionsController.getAccountBalance(accountId);

        verify(transactionsService).getAccountBalance(accountId, tenantId);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Account not found with id: 999", response.getBody().getMessage());
    }

    @Test
    public void getTenantBalance() {
        Long tenantId = 1L;
        Double balance = 5000.0;

        when(transactionsService.getTenantBalance(tenantId)).thenReturn(balance);

        ResponseEntity<ApiResponse<Double>> response = transactionsController.getTenantBalance();

        verify(transactionsService).getTenantBalance(tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5000.0, response.getBody().getData());
    }

    @Test
    public void getTenantBalanceForNonExistentTenant() {
        Long tenantId = 999L;
        when(tenantContext.getCurrentTenantId()).thenReturn(tenantId);
        when(transactionsService.getTenantBalance(tenantId)).thenThrow(new NoSuchElementException("Tenant not found with id: 999"));

        ResponseEntity<ApiResponse<Double>> response = transactionsController.getTenantBalance();

        verify(transactionsService).getTenantBalance(tenantId);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Tenant not found with id: 999", response.getBody().getMessage());
    }

    @Test
    public void getTransactionsByCategory() {
        Long categoryId = 1L;
        List<TransactionDTO> transactions = List.of(buildTransactionDto());
        Long tenantId = 1L;

        when(transactionsService.getTransactionsByCategory(categoryId, tenantId)).thenReturn(transactions);

        ResponseEntity<ApiResponse<List<TransactionDTO>>> response = transactionsController.getTransactionsByCategory(categoryId);

        verify(transactionsService).getTransactionsByCategory(categoryId, tenantId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(transactions, response.getBody().getData());
    }

    @Test
    public void getTransactionsByNonExistentCategory() {
        Long categoryId = 999L;
        Long tenantId = 1L;
        when(transactionsService.getTransactionsByCategory(categoryId, tenantId)).thenThrow(new NoSuchElementException("Category not found with id: 999"));

        ResponseEntity<ApiResponse<List<TransactionDTO>>> response = transactionsController.getTransactionsByCategory(categoryId);

        verify(transactionsService).getTransactionsByCategory(categoryId, tenantId);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Category not found with id: 999", response.getBody().getMessage());
    }

    private TransactionDTO buildTransactionDto() {
        return TransactionDTO.builder()
            .sourceAccountId(1L)
            .destinationAccountId(2L)
            .bookingDate(LocalDateTime.of(2026, 1, 10, 12, 0))
            .valueDate(LocalDateTime.of(2026, 1, 10, 12, 0))
            .amount(100.0)
            .currency("USD")
            .description("Test transaction")
            .merchantId(1L)
            .categoryId(1L)
            .externalId("EXT123")
            .statusId(1L)
            .typeId(1L)
            .createdAt(LocalDate.of(2026, 1, 10))
            .updatedAt(LocalDate.of(2026, 1, 10))
            .build();
    }

}