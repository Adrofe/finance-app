package es.triana.company.investments.controller;

import java.util.List;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.investments.model.api.ApiResponse;
import es.triana.company.investments.model.api.PriceRefreshResultDTO;
import es.triana.company.investments.model.api.PriceUpdateRequestDTO;
import es.triana.company.investments.service.ExchangeRateRefreshService;
import es.triana.company.investments.service.PriceRefreshService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/investments")
public class PriceController {

    private final PriceRefreshService priceRefreshService;
    private final ExchangeRateRefreshService exchangeRateRefreshService;

    public PriceController(PriceRefreshService priceRefreshService,
                           ExchangeRateRefreshService exchangeRateRefreshService) {
        this.priceRefreshService = priceRefreshService;
        this.exchangeRateRefreshService = exchangeRateRefreshService;
    }

    @PostMapping("/prices/refresh")
    public ResponseEntity<ApiResponse<PriceRefreshResultDTO>> refreshPricesOnDemand(@RequestBody List<@Valid PriceUpdateRequestDTO> updates) {
        PriceRefreshResultDTO result = priceRefreshService.refreshPricesOnDemand(updates);
        return ResponseEntity.ok(new ApiResponse<>(200, "Prices refreshed on demand", result));
    }

    @PostMapping("/prices/refresh/auto")
    public ResponseEntity<ApiResponse<PriceRefreshResultDTO>> refreshPricesAutoNow() {
        PriceRefreshResultDTO result = priceRefreshService.refreshPricesAutomaticallyNow();
        return ResponseEntity.ok(new ApiResponse<>(200, "Automatic price refresh executed", result));
    }

    @PostMapping("/forex/refresh-day")
    public ResponseEntity<ApiResponse<Integer>> refreshForexForDay(
            @RequestParam LocalDate asOf) {
        int saved = exchangeRateRefreshService.refreshRatesForDate(asOf);
        return ResponseEntity.ok(new ApiResponse<>(200, "Forex rates refresh executed for day " + asOf, saved));
    }
}
