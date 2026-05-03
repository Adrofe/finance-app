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
import es.triana.company.investments.model.api.FifoRebuildResultDTO;
import es.triana.company.investments.model.api.OperationDTO;
import es.triana.company.investments.model.api.TaxSummaryDTO;
import es.triana.company.investments.security.TenantContext;
import es.triana.company.investments.service.OperationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/investments/operations")
public class OperationsController {

    private final OperationService operationService;
    private final TenantContext tenantContext;

    public OperationsController(OperationService operationService, TenantContext tenantContext) {
        this.operationService = operationService;
        this.tenantContext = tenantContext;
    }

    /**
     * Register a BUY or SELL operation.
     * On SELL, the FIFO matching is computed automatically and returned in the response.
     * The tenant_id is extracted from the Keycloak token.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationDTO>> register(@Valid @RequestBody CreateOperationRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        CreateOperationRequest securedRequest = new CreateOperationRequest(request, tenantId);
        OperationDTO result = operationService.registerOperation(securedRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Operation registered successfully", result));
    }

    /** List all operations for a specific investment position */
    @GetMapping("/by-investment")
    public ResponseEntity<ApiResponse<List<OperationDTO>>> getByInvestment(@RequestParam Long investmentId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<OperationDTO> data = operationService.getByInvestment(investmentId, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Operations retrieved successfully", data));
    }

    /** List all operations for a tenant (all investments) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<OperationDTO>>> getByTenant() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<OperationDTO> data = operationService.getByTenant(tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Operations retrieved successfully", data));
    }

    /**
     * Fiscal summary for one year based on realised FIFO lots (SELL side date).
     * Includes totals and breakdowns by instrument and currency.
     */
    @GetMapping("/tax-summary")
    public ResponseEntity<ApiResponse<TaxSummaryDTO>> getTaxSummary(@RequestParam int year) {
        Long tenantId = tenantContext.getCurrentTenantId();
        TaxSummaryDTO data = operationService.getTaxSummary(tenantId, year);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tax summary retrieved successfully", data));
    }

    /**
     * Rebuild FIFO lots from scratch for one instrument+tenant.
     * Useful when backdated operations were inserted and prior matching is stale.
     */
    @PostMapping("/rebuild-fifo")
    public ResponseEntity<ApiResponse<FifoRebuildResultDTO>> rebuildFifo(@RequestParam Long instrumentId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        FifoRebuildResultDTO data = operationService.rebuildFifoForInstrumentTenant(instrumentId, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "FIFO rebuilt successfully", data));
    }
}
