package es.triana.company.banking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.TransactionTypeDTO;
import es.triana.company.banking.service.TransactionTypeService;

@RestController
@RequestMapping("/v1/api/transaction-types")
public class TransactionTypeController {

    private final TransactionTypeService transactionTypeService;

    public TransactionTypeController(TransactionTypeService transactionTypeService) {
        this.transactionTypeService = transactionTypeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransactionTypeDTO>>> getAllTypes() {
        List<TransactionTypeDTO> types = transactionTypeService.getAllTypes();
        ApiResponse<List<TransactionTypeDTO>> response = new ApiResponse<>(200, "Transaction types retrieved successfully", types);
        return ResponseEntity.ok(response);
    }
}
