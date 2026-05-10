package es.triana.company.budget.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.budget.model.api.ApiResponse;
import es.triana.company.budget.model.api.BudgetPlanDTO;
import es.triana.company.budget.model.api.BudgetPlanRequestDTO;
import es.triana.company.budget.model.api.BudgetSnapshotDTO;
import es.triana.company.budget.service.BudgetService;
import es.triana.company.budget.security.TenantContext;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/budget")
public class BudgetController {

    private final BudgetService budgetService;
    private final TenantContext tenantContext;

    public BudgetController(BudgetService budgetService, TenantContext tenantContext) {
        this.budgetService = budgetService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<BudgetPlanDTO>> upsertPlan(@Valid @RequestBody BudgetPlanRequestDTO request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        String bearerToken = extractBearerToken();
        BudgetPlanDTO plan = budgetService.upsertPlan(tenantId, bearerToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Budget plan saved", plan));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<BudgetPlanDTO>>> getPlans() {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<BudgetPlanDTO> plans = budgetService.getPlans(tenantId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Budget plans retrieved", plans));
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<ApiResponse<BudgetPlanDTO>> getPlan(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        BudgetPlanDTO plan = budgetService.getPlan(tenantId, id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Budget plan retrieved", plan));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        budgetService.deletePlan(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plans/{id}/snapshots/refresh")
    public ResponseEntity<ApiResponse<BudgetSnapshotDTO>> refreshSnapshot(@PathVariable("id") Long id, @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long tenantId = tenantContext.getCurrentTenantId();
        String bearerToken = extractBearerToken();
        BudgetSnapshotDTO snapshot = budgetService.refreshSnapshot(tenantId, bearerToken, id, startDate, endDate);
        return ResponseEntity.ok(new ApiResponse<>(200, "Budget snapshot refreshed", snapshot));
    }

    @GetMapping("/plans/{id}/snapshots")
    public ResponseEntity<ApiResponse<List<BudgetSnapshotDTO>>> getSnapshots(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<BudgetSnapshotDTO> snapshots = budgetService.getSnapshots(tenantId, id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Budget snapshots retrieved", snapshots));
    }

    @GetMapping("/plans/{id}/snapshots/latest")
    public ResponseEntity<ApiResponse<BudgetSnapshotDTO>> getLatestSnapshot(@PathVariable("id") Long id) {
        Long tenantId = tenantContext.getCurrentTenantId();
        BudgetSnapshotDTO snapshot = budgetService.getLatestSnapshot(tenantId, id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Latest budget snapshot retrieved", snapshot));
    }

    private String extractBearerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        throw new IllegalStateException("No JWT token found in security context");
    }
}
