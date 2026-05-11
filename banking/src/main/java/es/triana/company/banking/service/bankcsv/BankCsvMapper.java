package es.triana.company.banking.service.bankcsv;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * Strategy interface for bank-specific CSV mappers.
 * Each implementation knows how to parse one bank's CSV export
 * and produce a list of NormalizedBankRow ready for the generic
 * import pipeline (validation, dedup, insert).
 */
public interface BankCsvMapper {

    BankFormat getSupportedFormat();

    List<NormalizedBankRow> map(MultipartFile file);
}
