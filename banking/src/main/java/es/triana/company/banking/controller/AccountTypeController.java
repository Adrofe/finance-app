package es.triana.company.banking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.AccountTypeDTO;
import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.service.AccountTypeService;

@RestController
@RequestMapping("/v1/api/account-types")
public class AccountTypeController {

    private final AccountTypeService accountTypeService;

    public AccountTypeController(AccountTypeService accountTypeService) {
        this.accountTypeService = accountTypeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountTypeDTO>>> getAllAccountTypes() {
        List<AccountTypeDTO> types = accountTypeService.getAllAccountTypes();
        ApiResponse<List<AccountTypeDTO>> response = new ApiResponse<>(200, "Account types retrieved successfully", types);
        return ResponseEntity.ok(response);
    }
}
