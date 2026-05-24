package es.triana.company.investments.controller;

import java.util.List;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import es.triana.company.investments.model.api.ApiResponse;
import es.triana.company.investments.model.api.ExchangeRateDTO;
import es.triana.company.investments.model.api.ExchangeRateUpsertRequestDTO;
import es.triana.company.investments.model.api.PriceRefreshResultDTO;
import es.triana.company.investments.model.api.PriceUpdateRequestDTO;
import es.triana.company.investments.service.ExchangeRateRefreshService;
import es.triana.company.investments.service.ExchangeRateService;
import es.triana.company.investments.service.PriceRefreshService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/v1/api/investments")
public class PriceController {

    private final PriceRefreshService priceRefreshService;
    private final ExchangeRateRefreshService exchangeRateRefreshService;
    private final ExchangeRateService exchangeRateService;

    public PriceController(PriceRefreshService priceRefreshService,
                           ExchangeRateRefreshService exchangeRateRefreshService,
                           ExchangeRateService exchangeRateService) {
        this.priceRefreshService = priceRefreshService;
        this.exchangeRateRefreshService = exchangeRateRefreshService;
        this.exchangeRateService = exchangeRateService;
    }

    @PostMapping("/prices/refresh")
    public ResponseEntity<ApiResponse<PriceRefreshResultDTO>> refreshPricesOnDemand(@RequestBody List<@Valid PriceUpdateRequestDTO> updates) {
        PriceRefreshResultDTO result = priceRefreshService.refreshPricesOnDemand(updates);
        return ResponseEntity.ok(new ApiResponse<>(200, "Prices refreshed on demand", result));
    }

    @PostMapping("/prices/refresh/auto")
    public ResponseEntity<ApiResponse<Void>> refreshPricesAutoNow() {
        Thread.ofVirtual().start(priceRefreshService::refreshPricesAutomaticallyNow);
        return ResponseEntity.accepted()
                .body(new ApiResponse<>(202, "Price refresh started in background", null));
    }

    @PostMapping("/forex/refresh-day")
    public ResponseEntity<ApiResponse<Integer>> refreshForexForDay(@RequestParam("asOf") LocalDate asOf) {
        int saved = exchangeRateRefreshService.refreshRatesForDate(asOf);
        return ResponseEntity.ok(new ApiResponse<>(200, "Forex rates refresh executed for day " + asOf, saved));
    }

    @GetMapping("/forex/rates")
    public ResponseEntity<ApiResponse<List<ExchangeRateDTO>>> listForexRates(
            @RequestParam(value = "fromCurrency", required = false) String fromCurrency,
            @RequestParam(value = "toCurrency", required = false) String toCurrency,
            @RequestParam(value = "from", required = false) LocalDate from,
            @RequestParam(value = "to", required = false) LocalDate to) {

        List<ExchangeRateDTO> rates = exchangeRateService.findHistoricalRates(fromCurrency, toCurrency, from, to);
        return ResponseEntity.ok(new ApiResponse<>(200, "Forex rates loaded", rates));
    }

    @PostMapping("/forex/rates")
    public ResponseEntity<ApiResponse<ExchangeRateDTO>> upsertForexRate(@RequestBody @Valid ExchangeRateUpsertRequestDTO req) {
        ExchangeRateDTO saved = exchangeRateService.upsertManualRate(req);
        return ResponseEntity.ok(new ApiResponse<>(200, "Forex rate saved", saved));
    }

    @DeleteMapping("/forex/rates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForexRate(@PathVariable("id") Long id) {
        exchangeRateService.deleteRate(id);
    }
}
