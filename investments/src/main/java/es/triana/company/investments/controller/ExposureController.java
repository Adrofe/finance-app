package es.triana.company.investments.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.investments.model.api.ApiResponse;
import es.triana.company.investments.model.api.ExposureOverviewDTO;
import es.triana.company.investments.model.api.ExposureRefreshResultDTO;
import es.triana.company.investments.security.TenantContext;
import es.triana.company.investments.service.ExposureOverviewService;
import es.triana.company.investments.service.ExposureRefreshService;

@RestController
@RequestMapping("/v1/api/investments")
public class ExposureController {

    private final ExposureRefreshService exposureRefreshService;
    private final ExposureOverviewService exposureOverviewService;
    private final TenantContext tenantContext;

    public ExposureController(
            ExposureRefreshService exposureRefreshService,
            ExposureOverviewService exposureOverviewService,
            TenantContext tenantContext) {
        this.exposureRefreshService = exposureRefreshService;
        this.exposureOverviewService = exposureOverviewService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/exposures/refresh")
    public ResponseEntity<ApiResponse<ExposureRefreshResultDTO>> refreshCompoundExposuresNow() {
        ExposureRefreshResultDTO result = exposureRefreshService.refreshCompoundExposuresNow();
        return ResponseEntity.ok(new ApiResponse<>(200, "Compound exposures refreshed", result));
    }

    @GetMapping("/exposures/overview")
    public ResponseEntity<ApiResponse<ExposureOverviewDTO>> getExposureOverview(
            @RequestParam(name = "typeCodes", required = false) List<String> typeCodes) {
        Long tenantId = tenantContext.getCurrentTenantId();
        ExposureOverviewDTO result = exposureOverviewService.getOverview(tenantId, typeCodes);
        return ResponseEntity.ok(new ApiResponse<>(200, "Exposure overview retrieved successfully", result));
    }
}