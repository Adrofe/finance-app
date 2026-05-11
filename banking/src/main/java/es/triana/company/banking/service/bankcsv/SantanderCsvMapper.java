package es.triana.company.banking.service.bankcsv;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import es.triana.company.banking.service.exception.TransactionValidationException;

/**
 * Mapper for Santander Spain CSV exports.
 *
 * Expected format (semicolon-delimited, ISO-8859-1):
 *
 * The account must be selected by the user in the UI (no account column).
 */
@Component
public class SantanderCsvMapper implements BankCsvMapper {

    private static final Charset ENCODING = Charset.forName("ISO-8859-1");

    @Override
    public BankFormat getSupportedFormat() {
        return BankFormat.SANTANDER;
    }

    @Override
    public List<NormalizedBankRow> map(MultipartFile file) {
        try (Reader reader = new InputStreamReader(file.getInputStream(), ENCODING);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setDelimiter(';')
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<NormalizedBankRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                String rawDate    = getCol(record, "Fecha operacion", "Fecha operación");
                String rawValDate = getCol(record, "Fecha valor");
                String rawAmount  = getCol(record, "Importe EUR");
                String rawDesc    = getCol(record, "Concepto");

                String bookingDate = BankCsvMapperUtils.parseEsDate(rawDate);
                String valueDate   = BankCsvMapperUtils.parseEsDate(rawValDate);
                String amount      = BankCsvMapperUtils.parseEsAmount(rawAmount);
                String externalId  = BankCsvMapperUtils.generateExternalId(
                        "SANTANDER", bookingDate, amount, rawDesc);

                rows.add(new NormalizedBankRow(
                        record.getRecordNumber() + 1,
                        null,               // sourceAccountId — set from request.accountId
                        bookingDate,
                        valueDate,
                        amount,
                        "EUR",
                        rawDesc,            // merchant (description used as merchant name)
                        null,               // merchantId
                        rawDesc,            // description
                        externalId,
                        null, null, null, null, null,
                        record.toString()));
            }
            return rows;
        } catch (IOException ex) {
            throw new TransactionValidationException("Unable to read Santander CSV file");
        }
    }

    /** Tries each header alias in order and returns the first non-null value found. */
    private String getCol(CSVRecord record, String... aliases) {
        for (String alias : aliases) {
            try {
                String v = record.get(alias);
                if (v != null && !v.isBlank()) return v;
            } catch (IllegalArgumentException ignored) {
                // column not present, try next alias
            }
        }
        return null;
    }
}
