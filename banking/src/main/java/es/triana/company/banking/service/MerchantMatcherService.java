package es.triana.company.banking.service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.db.Merchant;
import es.triana.company.banking.repository.MerchantRepository;

/**
 * Matches a raw bank description to an existing Merchant by substring search.
 *
 * Strategy:
 *  1. Normalise both sides: uppercase, strip accents/diacritics.
 *  2. Sort merchants by name length descending so longer / more specific names
 *     are preferred (avoids "ING" matching before "INGENIERIA FINANCIERA").
 *  3. Return the first merchant whose normalised name is contained in the
 *     normalised description.
 *
 * Merchants are loaded once per call; callers that process many rows should
 * pre-load with {@link #loadAll()} and use {@link #match(String, List)}.
 */
@Service
public class MerchantMatcherService {

    private final MerchantRepository merchantRepository;

    public MerchantMatcherService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    /**
     * Load all merchants sorted by name length descending.
     * Call this once at the start of a bulk import to avoid N queries.
     */
    public List<Merchant> loadAll() {
        return merchantRepository.findAll()
                .stream()
                .sorted(Comparator.comparingInt((Merchant m) -> m.getName().length()).reversed())
                .toList();
    }

    /**
     * Find a merchant whose name is contained in {@code description},
     * using the pre-loaded list returned by {@link #loadAll()}.
     */
    public Optional<Merchant> match(String description, List<Merchant> merchants) {
        if (description == null || description.isBlank() || merchants == null || merchants.isEmpty()) {
            return Optional.empty();
        }
        String normDesc = normalize(description);
        return merchants.stream()
                .filter(m -> normDesc.contains(normalize(m.getName())))
                .findFirst();
    }

    /**
     * Convenience single-call variant that loads merchants from the DB each time.
     * Prefer {@link #match(String, List)} for bulk operations.
     */
    public Optional<Merchant> match(String description) {
        return match(description, loadAll());
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static String normalize(String input) {
        if (input == null) return "";
        // Decompose Unicode (é → e + combining accent), then strip combining chars
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toUpperCase();
    }
}
