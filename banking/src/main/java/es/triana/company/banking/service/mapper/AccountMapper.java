package es.triana.company.banking.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.AccountType;

@Component
public class AccountMapper {
    public AccountDTO toDto(Account account) {
    AccountType accountType = account.getAccountType();

        return AccountDTO.builder()
                .id(account.getId())
                .tenantId(account.getTenantId())
                .institutionId(account.getInstitutionId())
                .name(account.getName())
                .iban(account.getIban())
                .accountTypeId(accountType != null ? accountType.getId() : null)
                .accountTypeName(accountType != null ? accountType.getName() : null)
                .currency(account.getCurrency())
                .lastBalanceReal(account.getLastBalanceReal())
                .lastBalanceRealDate(account.getLastBalanceRealDate())
                .lastBalanceAvailable(account.getLastBalanceAvailable())
                .lastBalanceAvailableDate(account.getLastBalanceAvailableDate())
                .isActive(account.getIsActive())
                .build();
    }

    public Account toEntity(AccountDTO accountDTO) {
        Account account = Account.builder()
        .id(accountDTO.getId())
        .tenantId(accountDTO.getTenantId())
        .institutionId(accountDTO.getInstitutionId())
        .name(accountDTO.getName())
        .iban(accountDTO.getIban())
        .currency(accountDTO.getCurrency())
        .lastBalanceReal(accountDTO.getLastBalanceReal())
        .lastBalanceRealDate(accountDTO.getLastBalanceRealDate())
        .lastBalanceAvailable(accountDTO.getLastBalanceAvailable())
        .lastBalanceAvailableDate(accountDTO.getLastBalanceAvailableDate())
        .isActive(accountDTO.getIsActive())
        .build();
        return account;
    }
}
