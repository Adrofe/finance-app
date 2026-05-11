package es.triana.company.banking.service.bankcsv;

/**
 * Normalised row produced by any BankCsvMapper.
 * All date strings must be in yyyy-MM-dd format.
 * All amount strings must use '.' as decimal separator (e.g. "-45.50").
 * Fields absent in a bank's CSV should be null.
 */
public record NormalizedBankRow(
        long rowNumber,
        String sourceAccountId,
        String bookingDate,
        String valueDate,
        String amount,
        String currency,
        String merchant,
        String merchantId,
        String description,
        String externalId,
        String destinationAccountId,
        String categoryId,
        String tagIds,
        String statusId,
        String typeId,
        String rawLine) {}
