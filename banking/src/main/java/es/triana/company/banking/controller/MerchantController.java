package es.triana.company.banking.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
