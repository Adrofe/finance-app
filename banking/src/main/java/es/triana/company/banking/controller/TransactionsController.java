package es.triana.company.banking.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.service.TransactionService;

@RestController
@RequestMapping("/v1/api/transactions")
public class TransactionsController {

    private static final Long DEFAULT_TENANT_ID = 1L;

    private final TransactionService transactionService;

    public TransactionsController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionDTO>> createTransaction(@Valid @RequestBody TransactionDTO transactionDTO) {
        try {
            Long tenantId = resolveTenantId(transactionDTO.getTenantId());
            TransactionDTO createdTransaction = (TransactionDTO) transactionService.createTransaction(transactionDTO, tenantId);
            ApiResponse<TransactionDTO> response = new ApiResponse<>(201, "Transaction created successfully", createdTransaction);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<TransactionDTO> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionDTO>> getTransactionById(@PathVariable Long transactionId) {
        try {
            TransactionDTO transaction = (TransactionDTO) transactionService.getTransactionById(transactionId, DEFAULT_TENANT_ID);
            ApiResponse<TransactionDTO> response = new ApiResponse<>(200, "Transaction retrieved successfully", transaction);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<TransactionDTO> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByAccount(@PathVariable Long accountId) {
        try {
            List<TransactionDTO> transactions = castTransactionList(transactionService.getTransactionsByAccount(accountId, DEFAULT_TENANT_ID));
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByTenant(@PathVariable Long tenantId) {
        try {
            List<TransactionDTO> transactions = castTransactionList(transactionService.getTransactionsByTenant(tenantId));
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/date-range")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByDateRange(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        try {
            List<TransactionDTO> transactions = castTransactionList(transactionService.getTransactionsByDateRange(startDate, endDate, DEFAULT_TENANT_ID));
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionDTO>> updateTransaction(@PathVariable Long transactionId,
            @Valid @RequestBody TransactionDTO transactionDTO) {
        try {
            Long tenantId = resolveTenantId(transactionDTO.getTenantId());
            TransactionDTO updatedTransaction = (TransactionDTO) transactionService.updateTransaction(transactionId, transactionDTO, tenantId);
            ApiResponse<TransactionDTO> response = new ApiResponse<>(200, "Transaction updated successfully", updatedTransaction);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<TransactionDTO> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(@PathVariable Long transactionId) {
        try {
            transactionService.deleteTransaction(transactionId, DEFAULT_TENANT_ID);
            ApiResponse<Void> response = new ApiResponse<>(204, "Transaction deleted successfully", null);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<Void> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (RuntimeException e) {
            ApiResponse<Void> response = new ApiResponse<>(404, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/account/{accountId}/balance")
    public ResponseEntity<ApiResponse<Double>> getAccountBalance(@PathVariable Long accountId) {
        try {
            Double balance = (Double) transactionService.getAccountBalance(accountId, DEFAULT_TENANT_ID);
            ApiResponse<Double> response = new ApiResponse<>(200, "Account balance retrieved successfully", balance);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<Double> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (RuntimeException e) {
            ApiResponse<Double> response = new ApiResponse<>(404, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/tenant/{tenantId}/balance")
    public ResponseEntity<ApiResponse<Double>> getTenantBalance(@PathVariable Long tenantId) {
        try {
            Double balance = (Double) transactionService.getTenantBalance(tenantId);
            ApiResponse<Double> response = new ApiResponse<>(200, "Tenant balance retrieved successfully", balance);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<Double> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (RuntimeException e) {
            ApiResponse<Double> response = new ApiResponse<>(404, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<TransactionDTO>>> getTransactionsByCategory(@PathVariable Long categoryId) {
        try {
            List<TransactionDTO> transactions = castTransactionList(transactionService.getTransactionsByCategory(categoryId, DEFAULT_TENANT_ID));
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(200, "Transactions retrieved successfully", transactions);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(400, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (RuntimeException e) {
            ApiResponse<List<TransactionDTO>> response = new ApiResponse<>(404, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @SuppressWarnings("unchecked")
    private List<TransactionDTO> castTransactionList(Object value) {
        return (List<TransactionDTO>) value;
    }

    private Long resolveTenantId(Long tenantId) {
        //TODO: In a real application, you would extract the tenant ID from the authenticated user's context or a request header
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }
}
