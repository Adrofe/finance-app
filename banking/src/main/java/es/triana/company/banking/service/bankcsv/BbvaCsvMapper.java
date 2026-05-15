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
 * Mapper for BBVA Spain CSV exports.
 *
 * Expected format (semicolon-delimited, ISO-8859-1):
 */
@Component
public class BbvaCsvMapper implements BankCsvMapper {

    private static final Charset ENCODING = Charset.forName("ISO-8859-1");

    @Override
    public BankFormat getSupportedFormat() {
        return BankFormat.BBVA;
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
                String rawDate    = getCol(record, "Fecha");
                String rawValDate = getCol(record, "F.Valor");
                String rawDesc    = getCol(record, "Concepto");
                String rawAmount  = getCol(record, "Importe");

                // Combine description + observations for merchant name
                String merchant = (rawDesc != null && !rawDesc.isBlank()) ? rawDesc : null;
                String description = rawDesc;

                String bookingDate = BankCsvMapperUtils.parseEsDate(rawDate);
                String valueDate   = BankCsvMapperUtils.parseEsDate(rawValDate);
                String amount      = BankCsvMapperUtils.parseEsAmount(rawAmount);
                String externalId  = BankCsvMapperUtils.generateExternalId("BBVA", bookingDate, amount, rawDesc);

                rows.add(new NormalizedBankRow(
                        record.getRecordNumber() + 1,
                        null, bookingDate, valueDate, amount, "EUR",
                        merchant, null, description, externalId,
                        null, null, null, null, null,
                        record.toString()));
            }
            return rows;
        } catch (IOException ex) {
            throw new TransactionValidationException("Unable to read BBVA CSV file");
        }
    }

    private String getCol(CSVRecord record, String... aliases) {
        for (String alias : aliases) {
            try {
                String v = record.get(alias);
                if (v != null && !v.isBlank()) return v;
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }
}
