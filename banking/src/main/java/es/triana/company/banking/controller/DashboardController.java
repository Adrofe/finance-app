package es.triana.company.banking.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.DashboardSummaryDTO;
import es.triana.company.banking.model.api.SpendingByCategoryDTO;
import es.triana.company.banking.model.api.TimeSeriesPointDTO;
import es.triana.company.banking.security.TenantContext;
import es.triana.company.banking.service.DashboardService;

@RestController
@RequestMapping("/v1/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TenantContext tenantContext;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryDTO>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Long tenantId = tenantContext.getCurrentTenantId();
        DashboardSummaryDTO summary = dashboardService.getSummary(tenantId, startDate, endDate);
        return ResponseEntity.ok(new ApiResponse<>(200, "Dashboard summary retrieved successfully", summary));
    }

    @GetMapping("/spending-by-category")
    public ResponseEntity<ApiResponse<List<SpendingByCategoryDTO>>> getSpendingByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Long tenantId = tenantContext.getCurrentTenantId();
        List<SpendingByCategoryDTO> data = dashboardService.getSpendingByCategory(tenantId, startDate, endDate);
        return ResponseEntity.ok(new ApiResponse<>(200, "Spending by category retrieved successfully", data));
    }

    @GetMapping("/time-series")
    public ResponseEntity<ApiResponse<List<TimeSeriesPointDTO>>> getTimeSeries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "MONTH") String groupBy) {

        Long tenantId = tenantContext.getCurrentTenantId();
        List<TimeSeriesPointDTO> data = dashboardService.getTimeSeries(tenantId, startDate, endDate, groupBy);
        return ResponseEntity.ok(new ApiResponse<>(200, "Time series retrieved successfully", data));
    }
}
