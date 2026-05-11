package es.triana.company.banking.model.api;

import org.springframework.web.multipart.MultipartFile;

import es.triana.company.banking.service.bankcsv.BankFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportRequest {

    private MultipartFile file;
    private Long accountId;
    private boolean skipDuplicates;

    /** Bank format to use for parsing. Defaults to INTERNAL if null. */
    private BankFormat bankFormat;
}