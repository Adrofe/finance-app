package es.triana.company.banking.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.service.AccountsService;
import es.triana.company.banking.security.TenantContext;

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
            return ResponseEntity.noContent().build();
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

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@PathVariable Long id) {
        accountsService.deleteAccount(id);
        ApiResponse<Void> response = new ApiResponse<>(204, "Account deleted successfully", null);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDTO>> getAccountById(@PathVariable Long id) {
        AccountDTO account = accountsService.getAccountById(id);
        ApiResponse<AccountDTO> response = new ApiResponse<>(200, "Account retrieved successfully", account);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountDTO>> updateAccount(@PathVariable Long id, @Valid @RequestBody AccountDTO updatedAccount) {
        Long tenantId = tenantContext.getCurrentTenantId();

        if (updatedAccount.getId() == null) {
            updatedAccount.setId(id);
        } else if (!updatedAccount.getId().equals(id)) {
            throw new IllegalArgumentException("Path id does not match body id");
        }

        AccountDTO account = accountsService.updateAccount(updatedAccount, tenantId);
        ApiResponse<AccountDTO> response = new ApiResponse<>(200, "Account updated successfully", account);
        return ResponseEntity.ok(response);
    }

}
