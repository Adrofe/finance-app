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
import es.triana.company.investments.model.api.InvestmentInstrumentDTO;
import es.triana.company.investments.model.api.InvestmentPlatformDTO;
import es.triana.company.investments.service.InvestmentCatalogService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/investments/catalog")
public class InvestmentCatalogController {

    private final InvestmentCatalogService investmentCatalogService;

    public InvestmentCatalogController(InvestmentCatalogService investmentCatalogService) {
        this.investmentCatalogService = investmentCatalogService;
    }

    @GetMapping("/instruments")
    public ResponseEntity<ApiResponse<List<InvestmentInstrumentDTO>>> getInstruments() {
        List<InvestmentInstrumentDTO> data = investmentCatalogService.getAllInstruments();
        return ResponseEntity.ok(new ApiResponse<>(200, "Instruments retrieved successfully", data));
    }

    @GetMapping("/instruments/{id}")
    public ResponseEntity<ApiResponse<InvestmentInstrumentDTO>> getInstrumentById(@PathVariable Long id) {
        InvestmentInstrumentDTO data = investmentCatalogService.getInstrumentById(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument retrieved successfully", data));
    }

    @PostMapping("/instruments")
    public ResponseEntity<ApiResponse<InvestmentInstrumentDTO>> createInstrument(@Valid @RequestBody InvestmentInstrumentDTO request) {
        InvestmentInstrumentDTO data = investmentCatalogService.createInstrument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Instrument created successfully", data));
    }

    @PutMapping("/instruments/{id}")
    public ResponseEntity<ApiResponse<InvestmentInstrumentDTO>> updateInstrument(@PathVariable Long id, @Valid @RequestBody InvestmentInstrumentDTO request) {
        InvestmentInstrumentDTO data = investmentCatalogService.updateInstrument(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument updated successfully", data));
    }

    @DeleteMapping("/instruments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteInstrument(@PathVariable Long id) {
        investmentCatalogService.deleteInstrument(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument deleted successfully", null));
    }

    @GetMapping("/platforms")
    public ResponseEntity<ApiResponse<List<InvestmentPlatformDTO>>> getPlatforms() {
        List<InvestmentPlatformDTO> data = investmentCatalogService.getAllPlatforms();
        return ResponseEntity.ok(new ApiResponse<>(200, "Platforms retrieved successfully", data));
    }

    @GetMapping("/platforms/{id}")
    public ResponseEntity<ApiResponse<InvestmentPlatformDTO>> getPlatformById(@PathVariable Long id) {
        InvestmentPlatformDTO data = investmentCatalogService.getPlatformById(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Platform retrieved successfully", data));
    }

    @PostMapping("/platforms")
    public ResponseEntity<ApiResponse<InvestmentPlatformDTO>> createPlatform(@Valid @RequestBody InvestmentPlatformDTO request) {
        InvestmentPlatformDTO data = investmentCatalogService.createPlatform(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Platform created successfully", data));
    }

    @PutMapping("/platforms/{id}")
    public ResponseEntity<ApiResponse<InvestmentPlatformDTO>> updatePlatform(@PathVariable Long id, @Valid @RequestBody InvestmentPlatformDTO request) {
        InvestmentPlatformDTO data = investmentCatalogService.updatePlatform(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Platform updated successfully", data));
    }

    @DeleteMapping("/platforms/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlatform(@PathVariable Long id) {
        investmentCatalogService.deletePlatform(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Platform deleted successfully", null));
    }
}
