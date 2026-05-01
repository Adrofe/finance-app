package es.triana.company.investments.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.investments.model.api.ApiResponse;
import es.triana.company.investments.model.api.CreateOperationRequest;
import es.triana.company.investments.model.api.OperationDTO;
import es.triana.company.investments.service.OperationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/investments/operations")
public class OperationsController {

    private final OperationService operationService;

    public OperationsController(OperationService operationService) {
        this.operationService = operationService;
    }

    /**
     * Register a BUY or SELL operation.
     * On SELL, the FIFO matching is computed automatically and returned in the response.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationDTO>> register(@Valid @RequestBody CreateOperationRequest request) {
        OperationDTO result = operationService.registerOperation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(201, "Operation registered successfully", result));
    }

    /** List all operations for a specific investment position */
    @GetMapping("/by-investment")
    public ResponseEntity<ApiResponse<List<OperationDTO>>> getByInvestment(
            @RequestParam Long investmentId,
            @RequestParam Long tenantId) {
        List<OperationDTO> data = operationService.getByInvestment(investmentId, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Operations retrieved successfully", data));
    }

    /** List all operations for a tenant (all investments) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OperationDTO>>> getByTenant(@RequestParam Long tenantId) {
        List<OperationDTO> data = operationService.getByTenant(tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Operations retrieved successfully", data));
    }
}
