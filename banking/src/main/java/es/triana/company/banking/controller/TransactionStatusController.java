package es.triana.company.banking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.TransactionStatusDTO;
import es.triana.company.banking.service.TransactionStatusService;

@RestController
@RequestMapping("/v1/api/transaction-statuses")
public class TransactionStatusController {

    private final TransactionStatusService transactionStatusService;

    public TransactionStatusController(TransactionStatusService transactionStatusService) {
        this.transactionStatusService = transactionStatusService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransactionStatusDTO>>> getAllStatuses() {
        List<TransactionStatusDTO> statuses = transactionStatusService.getAllStatuses();
        ApiResponse<List<TransactionStatusDTO>> response = new ApiResponse<>(200, "Transaction statuses retrieved successfully", statuses);
        return ResponseEntity.ok(response);
    }
}
