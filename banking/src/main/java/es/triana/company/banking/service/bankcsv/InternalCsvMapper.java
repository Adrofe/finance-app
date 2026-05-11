package es.triana.company.banking.service.bankcsv;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import es.triana.company.banking.service.exception.TransactionValidationException;

/**
 * Mapper for the native application CSV format.
 * Headers: booking_date, amount, currency, merchant, description
 * (plus optional: source_account_id, value_date, external_id,
 * destination_account_id, merchant_id, category_id, tag_ids, status_id, type_id)
 */
@Component
public class InternalCsvMapper implements BankCsvMapper {

    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "booking_date", "amount", "currency", "merchant", "description");

    @Override
    public BankFormat getSupportedFormat() {
        return BankFormat.INTERNAL;
    }

    @Override
    public List<NormalizedBankRow> map(MultipartFile file) {
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            validateHeaders(parser.getHeaderMap().keySet());

            List<NormalizedBankRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                rows.add(new NormalizedBankRow(
                        record.getRecordNumber() + 1,
                        getValue(record, "source_account_id"),
                        getValue(record, "booking_date"),
                        getValue(record, "value_date"),
                        getValue(record, "amount"),
                        getValue(record, "currency"),
                        getValue(record, "merchant"),
                        getValue(record, "merchant_id"),
                        getValue(record, "description"),
                        getValue(record, "external_id"),
                        getValue(record, "destination_account_id"),
                        getValue(record, "category_id"),
                        getValue(record, "tag_ids"),
                        getValue(record, "status_id"),
                        getValue(record, "type_id"),
                        record.toString()));
            }
            return rows;
        } catch (IOException ex) {
            throw new TransactionValidationException("Unable to read CSV file");
        }
    }

    private void validateHeaders(Set<String> headers) {
        Set<String> normalised = headers.stream()
                .map(h -> h == null ? "" : h.trim().toLowerCase())
                .collect(Collectors.toSet());
        for (String required : REQUIRED_HEADERS) {
            if (!normalised.contains(required)) {
                throw new TransactionValidationException("Missing required CSV header: " + required);
            }
        }
    }

    private String getValue(CSVRecord record, String header) {
        for (String key : record.toMap().keySet()) {
            if (header.equals(key == null ? "" : key.trim().toLowerCase())) {
                return record.get(key);
            }
        }
        return null;
    }
}
