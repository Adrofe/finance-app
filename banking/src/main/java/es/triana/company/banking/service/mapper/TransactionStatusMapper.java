package es.triana.company.banking.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.TransactionStatusDTO;
import es.triana.company.banking.model.db.TransactionStatus;

@Component
public class TransactionStatusMapper {

    public TransactionStatusDTO toDto(TransactionStatus status) {
        if (status == null) {
            return null;
        }
        return TransactionStatusDTO.builder()
                .id(status.getId())
                .code(status.getCode())
                .description(status.getDescription())
                .build();
    }

    public TransactionStatus toEntity(TransactionStatusDTO statusDTO) {
        if (statusDTO == null) {
            return null;
        }
        return TransactionStatus.builder()
                .id(statusDTO.getId())
                .code(statusDTO.getCode())
                .description(statusDTO.getDescription())
                .build();
    }
}
