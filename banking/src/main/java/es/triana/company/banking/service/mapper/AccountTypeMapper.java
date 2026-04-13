package es.triana.company.banking.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.AccountTypeDTO;
import es.triana.company.banking.model.db.AccountType;

@Component
public class AccountTypeMapper {

    public AccountTypeDTO toDto(AccountType accountType) {
        if (accountType == null) {
            return null;
        }
        return AccountTypeDTO.builder()
                .id(accountType.getId())
                .name(accountType.getName())
                .description(accountType.getDescription())
                .build();
    }

    public AccountType toEntity(AccountTypeDTO accountTypeDTO) {
        if (accountTypeDTO == null) {
            return null;
        }
        return AccountType.builder()
                .id(accountTypeDTO.getId())
                .name(accountTypeDTO.getName())
                .description(accountTypeDTO.getDescription())
                .build();
    }
}
