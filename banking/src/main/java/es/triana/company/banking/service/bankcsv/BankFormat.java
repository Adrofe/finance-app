package es.triana.company.banking.service.bankcsv;

/**
 * Supported bank CSV export formats.
 * INTERNAL is the native app format (existing importer).
 */
public enum BankFormat {
    INTERNAL,
    SANTANDER,
    BBVA,
    ING,
    IMAGIN
}
