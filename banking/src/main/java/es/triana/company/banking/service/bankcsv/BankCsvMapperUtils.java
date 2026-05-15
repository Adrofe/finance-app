package es.triana.company.banking.service.bankcsv;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Shared parsing utilities for bank CSV mappers.
 */
public final class BankCsvMapperUtils {

    private static final DateTimeFormatter ES_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private BankCsvMapperUtils() {}

    /**
     * Converts a Spanish-format date string (dd/MM/yyyy) to ISO format (yyyy-MM-dd).
     * Returns null if value is blank or unparseable.
     */
    public static String parseEsDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim(), ES_DATE).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Normalises a European decimal amount ("1.234,56" or "-45,50") to a
     * dot-decimal string ("-45.50").  Returns null if value is blank.
     */
    public static String parseEsAmount(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim()
                .replace("\u00a0", "") // non-breaking space
                .replace(".", "")      // thousands separator
                .replace(",", ".");    // decimal separator
        try {
            new BigDecimal(normalized);
            return normalized;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Generates a stable, deterministic external ID for deduplication when
     * the bank CSV does not provide one.
     * Format: BANK_date_firstBytes-of-sha256(bankFormat|date|amount|desc)
     */
    public static String generateExternalId(String bankPrefix, String date, String amount, String description) {
        String raw = bankPrefix + "|" + date + "|" + amount + "|" + (description == null ? "" : description);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return bankPrefix + "_" + hex;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present in all JVMs
            return bankPrefix + "_" + Integer.toHexString(raw.hashCode());
        }
    }
}
