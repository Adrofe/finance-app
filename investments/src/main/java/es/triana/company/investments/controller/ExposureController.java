package es.triana.company.investments.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.investments.model.api.ApiResponse;
import es.triana.company.investments.model.api.ExposureRefreshResultDTO;
import es.triana.company.investments.service.ExposureRefreshService;

@RestController
@RequestMapping("/v1/api/investments")
public class ExposureController {

    private final ExposureRefreshService exposureRefreshService;

    public ExposureController(ExposureRefreshService exposureRefreshService) {
        this.exposureRefreshService = exposureRefreshService;
    }

    @PostMapping("/exposures/refresh")
    public ResponseEntity<ApiResponse<ExposureRefreshResultDTO>> refreshCompoundExposuresNow() {
        ExposureRefreshResultDTO result = exposureRefreshService.refreshCompoundExposuresNow();
        return ResponseEntity.ok(new ApiResponse<>(200, "Compound exposures refreshed", result));
    }
}