package es.triana.company.banking.model.api;

import org.springframework.web.multipart.MultipartFile;

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
}