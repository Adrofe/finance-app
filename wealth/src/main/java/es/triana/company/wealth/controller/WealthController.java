package es.triana.company.wealth.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.wealth.model.api.ApiResponse;
import es.triana.company.wealth.model.api.WealthSnapshotCreateRequestDTO;
import es.triana.company.wealth.model.api.WealthSnapshotDTO;
import es.triana.company.wealth.security.TenantContext;
import es.triana.company.wealth.service.WealthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/wealth")
public class WealthController {

    private final WealthService wealthService;
    private final TenantContext tenantContext;

    public WealthController(WealthService wealthService, TenantContext tenantContext) {
        this.wealthService = wealthService;
        this.tenantContext = tenantContext;
    }

    @PostMapping("/snapshots")
    public ResponseEntity<ApiResponse<WealthSnapshotDTO>> upsertSnapshot(@Valid @RequestBody WealthSnapshotCreateRequestDTO request) {
        Long tenantId = tenantContext.getCurrentTenantId();
        WealthSnapshotDTO created = wealthService.upsertSnapshot(tenantId, request);
        ApiResponse<WealthSnapshotDTO> response = new ApiResponse<>(201, "Wealth snapshot saved", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/snapshots")
    public ResponseEntity<ApiResponse<List<WealthSnapshotDTO>>> getSnapshots(@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(name = "includeItems", defaultValue = "false") boolean includeItems) {
        Long tenantId = tenantContext.getCurrentTenantId();
        List<WealthSnapshotDTO> snapshots = wealthService.getSnapshots(tenantId, from, to, includeItems);
        ApiResponse<List<WealthSnapshotDTO>> response = new ApiResponse<>(200, "Wealth snapshots retrieved", snapshots);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/snapshots/latest")
    public ResponseEntity<ApiResponse<WealthSnapshotDTO>> getLatest(@RequestParam(name = "includeItems", defaultValue = "false") boolean includeItems) {
        Long tenantId = tenantContext.getCurrentTenantId();
        WealthSnapshotDTO snapshot = wealthService.getLatest(tenantId, includeItems);
        ApiResponse<WealthSnapshotDTO> response = new ApiResponse<>(200, "Latest wealth snapshot retrieved", snapshot);
        return ResponseEntity.ok(response);
    }
}
