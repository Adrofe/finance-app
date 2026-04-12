package es.triana.company.banking.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.PagedResponse;
import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.api.TransactionFilterRequest;
import es.triana.company.banking.service.CSVExporterService;
import es.triana.company.banking.service.TransactionService;
import es.triana.company.banking.security.TenantContext;

@RestController
@RequestMapping("/v1/api/transactions")
public class TransactionsController {

    private final TransactionService transactionService;
    private final CSVExporterService csvExporterService;
    private final TenantContext tenantContext;

    public TransactionsController(
            TransactionService transactionService,
            CSVExporterService csvExporterService,
            TenantContext tenantContext) {
        this.transactionService = transactionService;
        this.csvExporterService = csvExporterService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<TransactionDTO>>> searchTransactions(
            @RequestBody TransactionFilterRequest filter) {
        Long tenantId = tenantContext.getCurrentTenantId();
        PagedResponse<TransactionDTO> result = transactionService.searchTransactions(filter, tenantId);
        ApiResponse<PagedResponse<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", result);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportTransactions(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long tenantId = tenantContext.getCurrentTenantId();
        byte[] csvContent = csvExporterService.exportTransactions(tenantId, accountId, startDate, endDate);

        String filename = "transactions_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(csvContent));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionDTO>> createTransaction(@Valid @RequestBody TransactionDTO transactionDTO) {
        Long tenantId = tenantContext.getCurrentTenantId();
        TransactionDTO createdTransaction = transactionService.createTransaction(transactionDTO, tenantId);
        ApiResponse<TransactionDTO> response = new ApiResponse<>(201, "Transaction created successfully", createdTransaction);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{transactionId:\\d+}")
    public ResponseEntity<ApiResponse<TransactionDTO>> getTransactionById(@PathVariable("transactionId") Long transactionId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        TransactionDTO transaction = transactionService.getTransactionById(transactionId, tenantId);
        ApiResponse<TransactionDTO> response = new ApiResponse<>(200, "Transaction retrieved successfully", transaction);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{accountId:\\d+}")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByAccount(@PathVariable("accountId") Long accountId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<TransactionDTO> transactions = transactionService.getTransactionsByAccount(accountId, tenantId);
        ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
        return ResponseEntity.ok(response);
    }

    @GetMapping()
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByTenant() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<TransactionDTO> transactions = transactionService.getTransactionsByTenant(tenantId);
        ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByDateRange(
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate) {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<TransactionDTO> transactions = transactionService.getTransactionsByDateRange(startDate, endDate, tenantId);
        ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{transactionId:\\d+}")
    public ResponseEntity<ApiResponse<TransactionDTO>> updateTransaction(@PathVariable("transactionId") Long transactionId,
            @Valid @RequestBody TransactionDTO transactionDTO) {
        Long tenantId = tenantContext.getCurrentTenantId();
        TransactionDTO updatedTransaction = transactionService.updateTransaction(transactionId, transactionDTO, tenantId);
        ApiResponse<TransactionDTO> response = new ApiResponse<>(200, "Transaction updated successfully", updatedTransaction);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{transactionId:\\d+}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(@PathVariable("transactionId") Long transactionId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        transactionService.deleteTransaction(transactionId, tenantId);
        ApiResponse<Void> response = new ApiResponse<>(200, "Transaction deleted successfully", null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/account/{accountId:\\d+}/balance")
    public ResponseEntity<ApiResponse<Double>> getAccountBalance(@PathVariable("accountId") Long accountId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        Double balance = transactionService.getAccountBalance(accountId, tenantId);
        ApiResponse<Double> response = new ApiResponse<>(200, "Account balance retrieved successfully", balance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<Double>> getTenantBalance() {
        Long tenantId = tenantContext.getCurrentTenantId();
        Double balance = transactionService.getTenantBalance(tenantId);
        ApiResponse<Double> response = new ApiResponse<>(200, "Tenant balance retrieved successfully", balance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category/{categoryId:\\d+}")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByCategory(@PathVariable("categoryId") Long categoryId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<TransactionDTO> transactions = transactionService.getTransactionsByCategory(categoryId, tenantId);
        ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tag/{tagId:\\d+}")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByTag(@PathVariable("tagId") Long tagId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<TransactionDTO> transactions = transactionService.getTransactionsByTag(tagId, tenantId);
        ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
        return ResponseEntity.ok(response);
    }
}
