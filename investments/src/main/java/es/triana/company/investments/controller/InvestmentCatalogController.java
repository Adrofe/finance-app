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
import es.triana.company.investments.model.api.CatalogOptionDTO;
import es.triana.company.investments.model.api.InvestmentInstrumentDTO;
import es.triana.company.investments.model.api.InvestmentInstrumentExposureDTO;
import es.triana.company.investments.model.api.InvestmentPlatformDTO;
import es.triana.company.investments.model.db.InvestmentTypeCatalog;
import es.triana.company.investments.service.InvestmentCatalogService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/investments/catalog")
public class InvestmentCatalogController {

    private final InvestmentCatalogService investmentCatalogService;

    public InvestmentCatalogController(InvestmentCatalogService investmentCatalogService) {
        this.investmentCatalogService = investmentCatalogService;
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<InvestmentTypeCatalog>>> getTypes() {
        List<InvestmentTypeCatalog> data = investmentCatalogService.getAllTypes();
        return ResponseEntity.ok(new ApiResponse<>(200, "Investment types retrieved successfully", data));
    }

    @GetMapping("/countries")
    public ResponseEntity<ApiResponse<List<CatalogOptionDTO>>> getCountries() {
        List<CatalogOptionDTO> data = investmentCatalogService.getAllCountries();
        return ResponseEntity.ok(new ApiResponse<>(200, "Country catalog retrieved successfully", data));
    }

    @PostMapping("/countries")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> createCountry(@RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.createCountry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Country created successfully", data));
    }

    @PutMapping("/countries/{id}")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> updateCountry(@PathVariable("id") Long id, @RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.updateCountry(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Country updated successfully", data));
    }

    @DeleteMapping("/countries/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCountry(@PathVariable("id") Long id) {
        investmentCatalogService.deleteCountry(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Country deleted successfully", null));
    }

    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<List<CatalogOptionDTO>>> getRegions() {
        List<CatalogOptionDTO> data = investmentCatalogService.getAllRegions();
        return ResponseEntity.ok(new ApiResponse<>(200, "Region catalog retrieved successfully", data));
    }

    @PostMapping("/regions")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> createRegion(@RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.createRegion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Region created successfully", data));
    }

    @PutMapping("/regions/{id}")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> updateRegion(@PathVariable("id") Long id, @RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.updateRegion(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Region updated successfully", data));
    }

    @DeleteMapping("/regions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRegion(@PathVariable("id") Long id) {
        investmentCatalogService.deleteRegion(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Region deleted successfully", null));
    }

    @GetMapping("/sectors")
    public ResponseEntity<ApiResponse<List<CatalogOptionDTO>>> getSectors() {
        List<CatalogOptionDTO> data = investmentCatalogService.getAllSectors();
        return ResponseEntity.ok(new ApiResponse<>(200, "Sector catalog retrieved successfully", data));
    }

    @PostMapping("/sectors")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> createSector(@RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.createSector(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Sector created successfully", data));
    }

    @PutMapping("/sectors/{id}")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> updateSector(@PathVariable("id") Long id, @RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.updateSector(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Sector updated successfully", data));
    }

    @DeleteMapping("/sectors/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSector(@PathVariable("id") Long id) {
        investmentCatalogService.deleteSector(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Sector deleted successfully", null));
    }

    @GetMapping("/industries")
    public ResponseEntity<ApiResponse<List<CatalogOptionDTO>>> getIndustries() {
        List<CatalogOptionDTO> data = investmentCatalogService.getAllIndustries();
        return ResponseEntity.ok(new ApiResponse<>(200, "Industry catalog retrieved successfully", data));
    }

    @PostMapping("/industries")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> createIndustry(@RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.createIndustry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Industry created successfully", data));
    }

    @PutMapping("/industries/{id}")
    public ResponseEntity<ApiResponse<CatalogOptionDTO>> updateIndustry(@PathVariable("id") Long id, @RequestBody CatalogOptionDTO request) {
        CatalogOptionDTO data = investmentCatalogService.updateIndustry(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Industry updated successfully", data));
    }

    @DeleteMapping("/industries/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteIndustry(@PathVariable("id") Long id) {
        investmentCatalogService.deleteIndustry(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Industry deleted successfully", null));
    }

    @GetMapping("/instruments/{id}/exposures")
    public ResponseEntity<ApiResponse<List<InvestmentInstrumentExposureDTO>>> getInstrumentExposures(@PathVariable("id") Long id) {
        List<InvestmentInstrumentExposureDTO> data = investmentCatalogService.getExposuresByInstrument(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument exposures retrieved successfully", data));
    }

    @PostMapping("/instruments/{id}/exposures")
    public ResponseEntity<ApiResponse<InvestmentInstrumentExposureDTO>> createInstrumentExposure(
            @PathVariable("id") Long id,
            @Valid @RequestBody InvestmentInstrumentExposureDTO request) {
        InvestmentInstrumentExposureDTO data = investmentCatalogService.createExposure(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Instrument exposure created successfully", data));
    }

    @PutMapping("/instruments/{id}/exposures/{exposureId}")
    public ResponseEntity<ApiResponse<InvestmentInstrumentExposureDTO>> updateInstrumentExposure(
            @PathVariable("id") Long id,
            @PathVariable("exposureId") Long exposureId,
            @Valid @RequestBody InvestmentInstrumentExposureDTO request) {
        InvestmentInstrumentExposureDTO data = investmentCatalogService.updateExposure(id, exposureId, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument exposure updated successfully", data));
    }

    @DeleteMapping("/instruments/{id}/exposures/{exposureId}")
    public ResponseEntity<ApiResponse<Void>> deleteInstrumentExposure(
            @PathVariable("id") Long id,
            @PathVariable("exposureId") Long exposureId) {
        investmentCatalogService.deleteExposure(id, exposureId);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument exposure deleted successfully", null));
    }

    @GetMapping("/instruments")
    public ResponseEntity<ApiResponse<List<InvestmentInstrumentDTO>>> getInstruments() {
        List<InvestmentInstrumentDTO> data = investmentCatalogService.getAllInstruments();
        return ResponseEntity.ok(new ApiResponse<>(200, "Instruments retrieved successfully", data));
    }

    @GetMapping("/instruments/{id}")
    public ResponseEntity<ApiResponse<InvestmentInstrumentDTO>> getInstrumentById(@PathVariable("id") Long id) {
        InvestmentInstrumentDTO data = investmentCatalogService.getInstrumentById(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument retrieved successfully", data));
    }

    @PostMapping("/instruments")
    public ResponseEntity<ApiResponse<InvestmentInstrumentDTO>> createInstrument(@Valid @RequestBody InvestmentInstrumentDTO request) {
        InvestmentInstrumentDTO data = investmentCatalogService.createInstrument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Instrument created successfully", data));
    }

    @PutMapping("/instruments/{id}")
    public ResponseEntity<ApiResponse<InvestmentInstrumentDTO>> updateInstrument(@PathVariable("id") Long id, @Valid @RequestBody InvestmentInstrumentDTO request) {
        InvestmentInstrumentDTO data = investmentCatalogService.updateInstrument(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument updated successfully", data));
    }

    @DeleteMapping("/instruments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteInstrument(@PathVariable("id") Long id) {
        investmentCatalogService.deleteInstrument(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Instrument deleted successfully", null));
    }

    @GetMapping("/platforms")
    public ResponseEntity<ApiResponse<List<InvestmentPlatformDTO>>> getPlatforms() {
        List<InvestmentPlatformDTO> data = investmentCatalogService.getAllPlatforms();
        return ResponseEntity.ok(new ApiResponse<>(200, "Platforms retrieved successfully", data));
    }

    @GetMapping("/platforms/{id}")
    public ResponseEntity<ApiResponse<InvestmentPlatformDTO>> getPlatformById(@PathVariable("id") Long id) {
        InvestmentPlatformDTO data = investmentCatalogService.getPlatformById(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Platform retrieved successfully", data));
    }

    @PostMapping("/platforms")
    public ResponseEntity<ApiResponse<InvestmentPlatformDTO>> createPlatform(@Valid @RequestBody InvestmentPlatformDTO request) {
        InvestmentPlatformDTO data = investmentCatalogService.createPlatform(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(201, "Platform created successfully", data));
    }

    @PutMapping("/platforms/{id}")
    public ResponseEntity<ApiResponse<InvestmentPlatformDTO>> updatePlatform(@PathVariable("id") Long id, @Valid @RequestBody InvestmentPlatformDTO request) {
        InvestmentPlatformDTO data = investmentCatalogService.updatePlatform(id, request);
        return ResponseEntity.ok(new ApiResponse<>(200, "Platform updated successfully", data));
    }

    @DeleteMapping("/platforms/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlatform(@PathVariable("id") Long id) {
        investmentCatalogService.deletePlatform(id);
        return ResponseEntity.ok(new ApiResponse<>(200, "Platform deleted successfully", null));
    }
}
