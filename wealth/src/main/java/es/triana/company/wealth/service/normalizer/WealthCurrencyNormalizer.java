package es.triana.company.wealth.service.normalizer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single Responsibility: Normalize and validate currency codes.
 */
@Component
public class WealthCurrencyNormalizer {

    private static final int ISO_CURRENCY_LENGTH = 3;
    private static final String DEFAULT_CURRENCY = "EUR";

    public String normalize(String currency) {
        String normalized = currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency.trim().toUpperCase();
        
        if (normalized.length() != ISO_CURRENCY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Currency must be an ISO-4217 3-letter code");
        }
        
        return normalized;
    }
}
