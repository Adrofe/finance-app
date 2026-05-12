package es.triana.company.banking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import es.triana.company.banking.model.api.CsvImportRequest;
import es.triana.company.banking.model.api.CsvImportResult;
import es.triana.company.banking.model.api.ImportError;
import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Merchant;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.MerchantRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.repository.TransactionTypeRepository;
import es.triana.company.banking.service.bankcsv.BankCsvMapper;
import es.triana.company.banking.service.bankcsv.BankFormat;
import es.triana.company.banking.service.bankcsv.NormalizedBankRow;
import es.triana.company.banking.service.exception.TransactionValidationException;

@Service
public class CSVImporterService {

    private final AccountsRepository accountsRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final MerchantMatcherService merchantMatcherService;
    private final TransactionTypeRepository transactionTypeRepository;
    private final Map<BankFormat, BankCsvMapper> mappers;

    public CSVImporterService(
            AccountsRepository accountsRepository,
            MerchantRepository merchantRepository,
            TransactionRepository transactionRepository,
            TransactionService transactionService,
            MerchantMatcherService merchantMatcherService,
            TransactionTypeRepository transactionTypeRepository,
            List<BankCsvMapper> bankCsvMappers) {
        this.accountsRepository = accountsRepository;
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.merchantMatcherService = merchantMatcherService;
        this.transactionTypeRepository = transactionTypeRepository;
        this.mappers = new EnumMap<>(BankFormat.class);
        for (BankCsvMapper mapper : bankCsvMappers) {
            this.mappers.put(mapper.getSupportedFormat(), mapper);
        }
    }

    public CsvImportResult importFile(CsvImportRequest request, Long tenantId) {
        validateImportRequest(request, tenantId);

        BankFormat format = request.getBankFormat() != null ? request.getBankFormat() : BankFormat.INTERNAL;
        BankCsvMapper mapper = mappers.get(format);
        if (mapper == null) {
            throw new TransactionValidationException("Unsupported bank format: " + format);
        }

        List<NormalizedBankRow> rows = mapper.map(request.getFile());
        Account defaultAccount = request.getAccountId() != null
            ? getAndValidateAccount(request.getAccountId(), tenantId)
            : null;

        // Pre-load merchants once for the whole batch to avoid N queries
        List<Merchant> allMerchants = merchantMatcherService.loadAll();

        // Pre-load EXPENSE / INCOME type IDs for automatic assignment
        Long expenseTypeId = transactionTypeRepository.findByName("EXPENSE").map(t -> t.getId()).orElse(null);
        Long incomeTypeId  = transactionTypeRepository.findByName("INCOME").map(t -> t.getId()).orElse(null);

        CsvImportResult result = new CsvImportResult();
        result.setTotalRows(rows.size());

        HashSet<String> seenExternalIds = new HashSet<>();
        for (NormalizedBankRow row : rows) {
            List<ImportError> rowErrors = validateRow(row, defaultAccount != null);
            if (!rowErrors.isEmpty()) {
                rowErrors.forEach(result::addError);
                continue;
            }

            String externalId = normalizeText(row.externalId());
            if (externalId != null && !seenExternalIds.add(externalId)) {
                if (request.isSkipDuplicates()) {
                    result.incrementSkipped();
                    continue;
                }

                result.addError(buildError(row.rowNumber(), "external_id", "Duplicate external_id inside CSV file", externalId));
                continue;
            }

            if (externalId != null && transactionRepository.existsByTenantIdAndExternalTxId(tenantId, externalId)) {
                if (request.isSkipDuplicates()) {
                    result.incrementSkipped();
                    continue;
                }

                result.addError(buildError(row.rowNumber(), "external_id", "Transaction with external_id already exists", externalId));
                continue;
            }

            try {
                TransactionDTO transactionDTO = toTransactionDto(row, defaultAccount, tenantId, allMerchants, expenseTypeId, incomeTypeId);
                transactionService.createTransaction(transactionDTO, tenantId);
                result.incrementSuccess();
            } catch (RuntimeException ex) {
                result.addError(buildError(row.rowNumber(), "row", ex.getMessage(), row.rawLine()));
            }
        }

        return result;
    }

    private void validateImportRequest(CsvImportRequest request, Long tenantId) {
        if (tenantId == null) {
            throw new TransactionValidationException("Tenant id is required");
        }

        if (request == null) {
            throw new TransactionValidationException("Import request is required");
        }

        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            throw new TransactionValidationException("CSV file is required");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new TransactionValidationException("File must be a CSV");
        }
    }

    private Account getAndValidateAccount(Long accountId, Long tenantId) {
        Account account = accountsRepository.findById(accountId)
                .orElseThrow(() -> new TransactionValidationException("Account not found with id: " + accountId));

        if (!tenantId.equals(account.getTenantId())) {
            throw new TransactionValidationException("Account does not belong to tenant " + tenantId);
        }

        return account;
    }

    private List<ImportError> validateRow(NormalizedBankRow row, boolean hasDefaultAccount) {
        List<ImportError> errors = new ArrayList<>();

        validateOptionalLong(row.sourceAccountId(), "source_account_id", row.rowNumber(), errors);
        if (!hasDefaultAccount && normalizeText(row.sourceAccountId()) == null) {
            errors.add(buildError(row.rowNumber(), "source_account_id",
                    "source_account_id is required when accountId is not provided", row.sourceAccountId()));
        }

        if (normalizeText(row.bookingDate()) == null) {
            errors.add(buildError(row.rowNumber(), "booking_date", "booking_date is required", row.bookingDate()));
        } else {
            try {
                LocalDate.parse(row.bookingDate());
            } catch (DateTimeParseException ex) {
                errors.add(buildError(row.rowNumber(), "booking_date", "booking_date must use yyyy-MM-dd format", row.bookingDate()));
            }
        }

        if (normalizeText(row.amount()) == null) {
            errors.add(buildError(row.rowNumber(), "amount", "amount is required", row.amount()));
        } else {
            try {
                BigDecimal amount = new BigDecimal(row.amount());
                if (BigDecimal.ZERO.compareTo(amount) == 0) {
                    errors.add(buildError(row.rowNumber(), "amount", "amount must be non-zero", row.amount()));
                }
            } catch (NumberFormatException ex) {
                errors.add(buildError(row.rowNumber(), "amount", "amount must be numeric", row.amount()));
            }
        }

        if (normalizeText(row.currency()) == null) {
            errors.add(buildError(row.rowNumber(), "currency", "currency is required", row.currency()));
        } else if (!row.currency().trim().toUpperCase().matches("^[A-Z]{3}$")) {
            errors.add(buildError(row.rowNumber(), "currency", "currency must be a 3-letter ISO code", row.currency()));
        }

        validateOptionalDate(row.valueDate(), "value_date", row.rowNumber(), errors);
        validateOptionalLong(row.destinationAccountId(), "destination_account_id", row.rowNumber(), errors);
        validateOptionalLong(row.merchantId(), "merchant_id", row.rowNumber(), errors);
        validateOptionalLong(row.categoryId(), "category_id", row.rowNumber(), errors);
        validateOptionalLong(row.statusId(), "status_id", row.rowNumber(), errors);
        validateOptionalLong(row.typeId(), "type_id", row.rowNumber(), errors);
        validateTagIds(row.tagIds(), row.rowNumber(), errors);

        return errors;
    }

    private TransactionDTO toTransactionDto(NormalizedBankRow row, Account defaultAccount, Long tenantId,
            List<Merchant> allMerchants, Long expenseTypeId, Long incomeTypeId) {
        Long merchantId = resolveMerchantId(row.merchantId(), row.merchant(), allMerchants);
        LocalDate bookingDate = LocalDate.parse(row.bookingDate());
        LocalDate valueDate = normalizeText(row.valueDate()) != null ? LocalDate.parse(row.valueDate()) : bookingDate;
        BigDecimal amount = new BigDecimal(row.amount());

        Account sourceAccount = resolveSourceAccount(row, defaultAccount, tenantId);

        // Auto-assign type based on sign when not explicitly set in the CSV
        Long typeId = parseLongOrNull(row.typeId());
        if (typeId == null) {
            typeId = amount.signum() < 0 ? expenseTypeId : incomeTypeId;
        }

        // Auto-assign category from merchant if not explicitly set in the CSV
        Long categoryId = parseLongOrNull(row.categoryId());
        if (categoryId == null && merchantId != null) {
            // Find merchant from the list and use its category if available
            Optional<Merchant> merchant = allMerchants.stream()
                    .filter(m -> m.getId().equals(merchantId))
                    .findFirst();
            if (merchant.isPresent() && merchant.get().getCategory() != null) {
                categoryId = merchant.get().getCategory().getId();
            }
        }

        return TransactionDTO.builder()
                .sourceAccountId(sourceAccount.getId())
                .destinationAccountId(parseLongOrNull(row.destinationAccountId()))
                .bookingDate(bookingDate.atStartOfDay())
                .valueDate(valueDate.atStartOfDay())
                .amount(amount.doubleValue())
                .currency(row.currency().trim().toUpperCase())
                .description(normalizeText(row.description()))
                .merchantId(merchantId)
                .categoryId(categoryId)
                .tagIds(parseTagIds(row.tagIds()))
                .externalId(normalizeText(row.externalId()))
                .statusId(parseLongOrNull(row.statusId()))
                .typeId(typeId)
                .build();
    }

    private Account resolveSourceAccount(NormalizedBankRow row, Account defaultAccount, Long tenantId) {
        Long sourceAccountId = parseLongOrNull(row.sourceAccountId());
        if (sourceAccountId == null) {
            if (defaultAccount == null) {
                throw new TransactionValidationException(
                        "source_account_id is required when accountId is not provided");
            }
            return defaultAccount;
        }

        Account csvAccount = accountsRepository.findById(sourceAccountId)
                .orElseThrow(() -> new TransactionValidationException("Source account not found with id: " + sourceAccountId));
        if (!tenantId.equals(csvAccount.getTenantId())) {
            throw new TransactionValidationException("Source account does not belong to tenant " + tenantId);
        }
        return csvAccount;
    }

    private Long resolveMerchantId(String merchantIdValue, String merchantName, List<Merchant> allMerchants) {
        // 1. Explicit numeric ID takes precedence
        Long merchantId = parseLongOrNull(merchantIdValue);
        if (merchantId != null) {
            return merchantId;
        }

        String normalizedMerchant = normalizeText(merchantName);
        if (normalizedMerchant == null) {
            return null;
        }

        // 2. Exact name match
        Optional<Merchant> exact = merchantRepository.findByName(normalizedMerchant);
        if (exact.isPresent()) {
            return exact.get().getId();
        }

        // 3. Substring match: find a merchant whose name appears inside the description
        return merchantMatcherService.match(normalizedMerchant, allMerchants)
                .map(Merchant::getId)
                .orElse(null);
    }

    private void validateOptionalDate(String value, String field, long rowNumber, List<ImportError> errors) {
        if (normalizeText(value) == null) {
            return;
        }

        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            errors.add(buildError(rowNumber, field, field + " must use yyyy-MM-dd format", value));
        }
    }

    private void validateOptionalLong(String value, String field, long rowNumber, List<ImportError> errors) {
        if (normalizeText(value) == null) {
            return;
        }

        try {
            Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            errors.add(buildError(rowNumber, field, field + " must be numeric", value));
        }
    }

    private void validateTagIds(String value, long rowNumber, List<ImportError> errors) {
        if (normalizeText(value) == null) {
            return;
        }

        for (String tagId : splitTagIds(value)) {
            try {
                Long.parseLong(tagId);
            } catch (NumberFormatException ex) {
                errors.add(buildError(rowNumber, "tag_ids", "tag_ids must contain only numeric values", value));
                return;
            }
        }
    }

    private Long parseLongOrNull(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : Long.parseLong(normalized);
    }

    private List<Long> parseTagIds(String value) {
        if (normalizeText(value) == null) {
            return List.of();
        }

        return splitTagIds(value).stream()
                .map(Long::parseLong)
                .toList();
    }

    private List<String> splitTagIds(String value) {
        return Arrays.stream(value.split("[;,]"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private ImportError buildError(long rowNumber, String field, String message, String rawValue) {
        return ImportError.builder()
                .rowNumber(rowNumber)
                .field(field)
                .message(message)
                .rawValue(rawValue)
                .build();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}