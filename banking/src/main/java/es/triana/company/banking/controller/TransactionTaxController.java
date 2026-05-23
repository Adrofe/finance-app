package es.triana.company.banking.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.TaxTypeDTO;
import es.triana.company.banking.model.api.TransactionTaxDTO;
import es.triana.company.banking.model.api.TransactionTaxRequest;
import es.triana.company.banking.security.TenantContext;
import es.triana.company.banking.service.TransactionTaxService;

@RestController
@RequestMapping("/v1/api")
public class TransactionTaxController {

    private final TransactionTaxService transactionTaxService;
    private final TenantContext tenantContext;

    public TransactionTaxController(TransactionTaxService transactionTaxService, TenantContext tenantContext) {
        this.transactionTaxService = transactionTaxService;
        this.tenantContext = tenantContext;
    }

    // GET /v1/api/tax-types
    @GetMapping("/tax-types")
    public ResponseEntity<ApiResponse<List<TaxTypeDTO>>> getTaxTypes() {
        List<TaxTypeDTO> taxTypes = transactionTaxService.getAllTaxTypes();
        return ResponseEntity.ok(new ApiResponse<>(200, "Tax types retrieved", taxTypes));
    }

    // GET /v1/api/transactions/{transactionId}/tax
    @GetMapping("/transactions/{transactionId}/tax")
    public ResponseEntity<ApiResponse<TransactionTaxDTO>> getTax(@PathVariable("transactionId") Long transactionId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        TransactionTaxDTO dto = transactionTaxService.getTaxForTransaction(transactionId, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tax record retrieved", dto));
    }

    // PUT /v1/api/transactions/{transactionId}/tax  (upsert)
    @PutMapping("/transactions/{transactionId}/tax")
    public ResponseEntity<ApiResponse<TransactionTaxDTO>> saveTax(@PathVariable("transactionId") Long transactionId, @Valid @RequestBody TransactionTaxRequest request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        TransactionTaxDTO result = transactionTaxService.saveTaxForTransaction(transactionId, request, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tax record saved", result));
    }

    // DELETE /v1/api/transactions/{transactionId}/tax
    @DeleteMapping("/transactions/{transactionId}/tax")
    public ResponseEntity<ApiResponse<Void>> deleteTax(@PathVariable("transactionId") Long transactionId) {
        Long tenantId = tenantContext.getCurrentTenantId();
        transactionTaxService.deleteTaxForTransaction(transactionId, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tax record deleted", null));
    }

    // GET /v1/api/tax-report
    @GetMapping("/tax-report")
    public ResponseEntity<ApiResponse<List<TransactionTaxDTO>>> getTaxReport() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<TransactionTaxDTO> report = transactionTaxService.getAllTaxesForTenant(tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Tax report retrieved", report));
    }
}
