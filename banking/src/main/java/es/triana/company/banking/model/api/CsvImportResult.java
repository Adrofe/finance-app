package es.triana.company.banking.model.api;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CsvImportResult {

    private int totalRows;
    private int successCount;
    private int failedCount;
    private int skippedCount;
    private List<ImportError> errors = new ArrayList<>();

    public void incrementSuccess() {
        successCount++;
    }

    public void incrementSkipped() {
        skippedCount++;
    }

    public void addError(ImportError error) {
        failedCount++;
        errors.add(error);
    }
}