package es.triana.company.banking.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.service.AccountsService;
import es.triana.company.banking.security.TenantContext;
import es.triana.company.banking.service.exception.AccountValidationException;

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


@RestController
@RequestMapping("/v1/api/accounts")
public class AccountsController {

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private TenantContext tenantContext;
    
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDTO>>> getAllAccounts() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<AccountDTO> accounts = accountsService.getAccountsByTenant(String.valueOf(tenantId));

        if (accounts == null || accounts.isEmpty()) {
            ApiResponse<List<AccountDTO>> response = new ApiResponse<>(200, "No accounts found", List.of());
            return ResponseEntity.ok(response);
        }

        ApiResponse<List<AccountDTO>> response = new ApiResponse<>(200, "Accounts retrieved successfully", accounts);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDTO>> createAccount(@Valid @RequestBody AccountDTO newAccount) {
        Long tenantId = tenantContext.getCurrentTenantId();
        AccountDTO createdAccount = accountsService.createAccount(newAccount, tenantId);
        ApiResponse<AccountDTO> response = new ApiResponse<>(201, "Account created successfully", createdAccount);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        accountsService.deleteAccount(id, tenantId);
        ApiResponse<Void> response = new ApiResponse<>(200, "Account deleted successfully", null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<AccountDTO>> getAccountById(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        AccountDTO account = accountsService.getAccountById(id, tenantId);
        ApiResponse<AccountDTO> response = new ApiResponse<>(200, "Account retrieved successfully", account);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<AccountDTO>> updateAccount(@PathVariable("id") Long id, @Valid @RequestBody AccountDTO updatedAccount) {
        Long tenantId = tenantContext.getCurrentTenantId();

        if (updatedAccount.getId() == null) {
            updatedAccount.setId(id);
        } else if (!updatedAccount.getId().equals(id)) {
            throw new AccountValidationException("Path id does not match body id");
        }

        AccountDTO account = accountsService.updateAccount(updatedAccount, tenantId);
        ApiResponse<AccountDTO> response = new ApiResponse<>(200, "Account updated successfully", account);
        return ResponseEntity.ok(response);
    }

}
