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
import es.triana.company.banking.model.api.MerchantDTO;
import es.triana.company.banking.service.MerchantService;

@RestController
@RequestMapping("/v1/api/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MerchantDTO>>> getAllMerchants() {
        List<MerchantDTO> merchants = merchantService.getAllMerchants();
        ApiResponse<List<MerchantDTO>> response = new ApiResponse<>(200, "Merchants retrieved successfully", merchants);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MerchantDTO>> createMerchant(@Valid @RequestBody MerchantDTO merchantDTO) {
        MerchantDTO created = merchantService.createMerchant(merchantDTO);
        ApiResponse<MerchantDTO> response = new ApiResponse<>(201, "Merchant created successfully", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantDTO>> updateMerchant(@PathVariable Long id, @Valid @RequestBody MerchantDTO merchantDTO) {
        MerchantDTO updated = merchantService.updateMerchant(id, merchantDTO);
        if (updated == null) {
            ApiResponse<MerchantDTO> response = new ApiResponse<>(404, "Merchant not found", null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        ApiResponse<MerchantDTO> response = new ApiResponse<>(200, "Merchant updated successfully", updated);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMerchant(@PathVariable Long id) {
        merchantService.deleteMerchant(id);
        ApiResponse<Void> response = new ApiResponse<>(200, "Merchant deleted successfully", null);
        return ResponseEntity.ok(response);
    }
}
