package es.triana.company.banking.controller;

import java.util.List;

import jakarta.validation.Valid;
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

import es.triana.company.banking.model.api.ApiResponse;
import es.triana.company.banking.model.api.InstitutionDTO;
import es.triana.company.banking.service.InstitutionService;

@RestController
@RequestMapping("/v1/api/institutions")
public class InstitutionController {

    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InstitutionDTO>>> getAllInstitutions() {
        List<InstitutionDTO> institutions = institutionService.getAllInstitutions();
        ApiResponse<List<InstitutionDTO>> response = new ApiResponse<>(200, "Institutions retrieved successfully", institutions);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InstitutionDTO>> createInstitution(@Valid @RequestBody InstitutionDTO institutionDTO) {
        InstitutionDTO created = institutionService.createInstitution(institutionDTO);
        ApiResponse<InstitutionDTO> response = new ApiResponse<>(201, "Institution created successfully", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InstitutionDTO>> updateInstitution(@PathVariable Long id, @Valid @RequestBody InstitutionDTO institutionDTO) {
        InstitutionDTO updated = institutionService.updateInstitution(id, institutionDTO);
        if (updated == null) {
            ApiResponse<InstitutionDTO> response = new ApiResponse<>(404, "Institution not found", null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        ApiResponse<InstitutionDTO> response = new ApiResponse<>(200, "Institution updated successfully", updated);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteInstitution(@PathVariable Long id) {
        institutionService.deleteInstitution(id);
        ApiResponse<Void> response = new ApiResponse<>(200, "Institution deleted successfully", null);
        return ResponseEntity.ok(response);
    }
}
