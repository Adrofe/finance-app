package es.triana.company.banking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
