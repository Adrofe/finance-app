package es.triana.company.banking.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.exception.TransactionValidationException;
import es.triana.company.banking.service.mapper.TransactionMapper;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CSVExporterService {

    private static final String[] CSV_HEADERS = {
            "source_account_id", "booking_date", "value_date", "amount", "currency", "merchant", "merchant_id",
            "description", "external_id", "destination_account_id", "category_id",
            "tag_ids", "status_id", "type_id"
    };

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public CSVExporterService(
            TransactionRepository transactionRepository,
            TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    /**
     * Exports transactions to CSV format
     * 
     * @param tenantId Tenant ID (required)
     * @param accountIdFilter Optional account ID to filter by
     * @return CSV content as byte array
     */
    public byte[] exportTransactions(Long tenantId, Long accountIdFilter) {
        return exportTransactions(tenantId, accountIdFilter, null, null);
    }

    /**
     * Exports transactions to CSV format
     * 
     * @param tenantId Tenant ID (required)
     * @param accountIdFilter Optional account ID to filter by
     * @param startDate Optional start date (inclusive) for booking_date
     * @param endDate Optional end date (inclusive) for booking_date
     * @return CSV content as byte array
     */
    public byte[] exportTransactions(Long tenantId, Long accountIdFilter, LocalDate startDate, LocalDate endDate) {
        if (tenantId == null) {
            throw new TransactionValidationException("Tenant id is required");
        }

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new TransactionValidationException("startDate must be before or equal to endDate");
        }

        try {
            // Fetch transactions from repository with optional filters applied in DB
            List<Transaction> filteredTransactions = transactionRepository.findAllForExport(
                tenantId,
                accountIdFilter,
                startDate,
                endDate);

                log.info("Exporting {} transactions for tenant {} {} with date range [{}, {}]", filteredTransactions.size(), tenantId,
                    accountIdFilter != null ? "and account " + accountIdFilter : "", startDate, endDate);

            return generateCSV(filteredTransactions);
        } catch (Exception ex) {
            log.error("Error exporting transactions", ex);
            throw new TransactionValidationException("Unable to export transactions: " + ex.getMessage());
        }
    }

    private byte[] generateCSV(List<Transaction> transactions) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = CSVFormat.DEFAULT.builder()
                     .setHeader(CSV_HEADERS)
                     .build()
                     .print(writer)) {

            for (Transaction transaction : transactions) {
                TransactionDTO dto = transactionMapper.toDto(transaction);
                List<String> record = toCSVRecord(dto);
                printer.printRecord(record);
            }

            writer.flush();
            return output.toByteArray();
        }
    }

    private List<String> toCSVRecord(TransactionDTO dto) {
        List<String> record = new ArrayList<>();

        // source_account_id
        record.add(dto.getSourceAccountId() != null ? dto.getSourceAccountId().toString() : "");

        // booking_date
        record.add(formatDate(dto.getBookingDate()));

        // value_date
        record.add(formatDate(dto.getValueDate()));

        // amount
        record.add(dto.getAmount() != null ? dto.getAmount().toString() : "");

        // currency
        record.add(dto.getCurrency() != null ? dto.getCurrency() : "");

        // merchant (name - merchant_name field in CSV)
        record.add(dto.getMerchantName() != null ? dto.getMerchantName() : "");

        // merchant_id
        record.add(dto.getMerchantId() != null ? dto.getMerchantId().toString() : "");

        // description
        record.add(dto.getDescription() != null ? dto.getDescription() : "");

        // external_id (not required for re-import without conflicts)
        record.add(dto.getExternalId() != null ? dto.getExternalId() : "");

        // destination_account_id
        record.add(dto.getDestinationAccountId() != null ? dto.getDestinationAccountId().toString() : "");

        // category_id
        record.add(dto.getCategoryId() != null ? dto.getCategoryId().toString() : "");

        // tag_ids (format: id1;id2;id3)
        record.add(formatTagIds(dto.getTagIds()));

        // status_id
        record.add(dto.getStatusId() != null ? dto.getStatusId().toString() : "");

        // type_id
        record.add(dto.getTypeId() != null ? dto.getTypeId().toString() : "");

        return record;
    }

    private String formatDate(Object dateTime) {
        if (dateTime == null) {
            return "";
        }

        if (dateTime instanceof LocalDate) {
            return ((LocalDate) dateTime).format(DATE_FORMATTER);
        } else if (dateTime instanceof LocalDateTime) {
            return ((LocalDateTime) dateTime).format(DATE_FORMATTER);
        }

        return dateTime.toString();
    }

    private String formatTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return "";
        }

        return tagIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(";"));
    }
}
