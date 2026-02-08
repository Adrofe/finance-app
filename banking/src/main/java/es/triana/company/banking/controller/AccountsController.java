package es.triana.company.banking.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.service.AccountsService;
import es.triana.company.banking.service.exception.AccountNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
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


@RestController
@RequestMapping("/v1/api/accounts")
public class AccountsController {

    @Autowired
    private AccountsService accountsService;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getAllAccounts(@RequestParam(required = false) String tenantId) {
        List<AccountDTO> accounts = accountsService.getAccountsByTenant(tenantId);

        if (accounts == null || accounts.isEmpty()) {
            ApiResponse<List<AccountDTO>> response = new ApiResponse<>(204, "No accounts found", null);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
        }

        ApiResponse<List<AccountDTO>> response = new ApiResponse<>(200, "Accounts retrieved successfully", accounts);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDTO>> createAccount(@RequestBody AccountDTO newAccount) {
        AccountDTO createdAccount = accountsService.createAccount(newAccount);
        ApiResponse<AccountDTO> response = new ApiResponse<>(201, "Account created successfully", createdAccount);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        try {
            accountsService.deleteAccount(id);
            ApiResponse<Void> response = new ApiResponse<>(204, "Account deleted successfully", null);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
        } catch (AccountNotFoundException e) {
            ApiResponse<Void> response = new ApiResponse<>(404, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            ApiResponse<Void> response = new ApiResponse<>(500, "Internal server error: " + e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDTO>> getAccountById(@PathVariable Long id) {
        try {
            AccountDTO account = accountsService.getAccountById(id);
            ApiResponse<AccountDTO> response = new ApiResponse<>(200, "Account retrieved successfully", account);
            return ResponseEntity.ok(response);
        } catch (AccountNotFoundException e) {
            ApiResponse<AccountDTO> response = new ApiResponse<>(404, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            ApiResponse<AccountDTO> response = new ApiResponse<>(500, "Internal server error: " + e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AccountDTO>> updateAccount(@RequestBody AccountDTO updatedAccount) {
        try {
            AccountDTO account = accountsService.updateAccount(updatedAccount);
            ApiResponse<AccountDTO> response = new ApiResponse<>(201, "Account updated successfully", account);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (AccountNotFoundException e) {
            ApiResponse<AccountDTO> response = new ApiResponse<>(404, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            ApiResponse<AccountDTO> response = new ApiResponse<>(500, "Internal server error: " + e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

}
