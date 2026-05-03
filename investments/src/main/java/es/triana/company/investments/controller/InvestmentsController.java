package es.triana.company.investments.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.investments.model.api.ApiResponse;
import es.triana.company.investments.model.api.InvestmentDTO;
import es.triana.company.investments.model.api.InvestmentSummaryDTO;
import es.triana.company.investments.security.TenantContext;
import es.triana.company.investments.service.InvestmentService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/investments")
public class InvestmentsController {

    private final InvestmentService investmentService;
    private final TenantContext tenantContext;

    public InvestmentsController(InvestmentService investmentService, TenantContext tenantContext) {
        this.investmentService = investmentService;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvestmentDTO>>> getAll() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<InvestmentDTO> data = investmentService.getAllByTenant(tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Investments retrieved successfully", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvestmentDTO>> getById(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        InvestmentDTO data = investmentService.getById(id, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Investment retrieved successfully", data));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<InvestmentSummaryDTO>> getSummary() {
        Long tenantId = tenantContext.getCurrentTenantId();
        InvestmentSummaryDTO data = investmentService.getSummary(tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Investment summary retrieved successfully", data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InvestmentDTO>> create(@Valid @RequestBody InvestmentDTO request) {
        InvestmentDTO created = investmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Investment created successfully", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InvestmentDTO>> update(@PathVariable("id") Long id, @Valid @RequestBody InvestmentDTO request) {
        InvestmentDTO updated = investmentService.update(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Investment updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        investmentService.delete(id, tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Investment deleted successfully", null));
    }
}
