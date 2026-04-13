package es.triana.company.banking.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.TransactionTypeDTO;
import es.triana.company.banking.model.db.TransactionType;

@Component
public class TransactionTypeMapper {

    public TransactionTypeDTO toDto(TransactionType type) {
        if (type == null) {
            return null;
        }
        return TransactionTypeDTO.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .build();
    }

    public TransactionType toEntity(TransactionTypeDTO typeDTO) {
        if (typeDTO == null) {
            return null;
        }
        return TransactionType.builder()
                .id(typeDTO.getId())
                .name(typeDTO.getName())
                .description(typeDTO.getDescription())
                .build();
    }
}
