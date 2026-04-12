package es.triana.company.banking.service.mapper;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.AccountType;
import es.triana.company.banking.model.db.Institution;

@Component
public class AccountMapper {
    public AccountDTO toDto(Account account) {
    AccountType accountType = account.getAccountType();
    Institution institution = account.getInstitution();

        return AccountDTO.builder()
                .id(account.getId())
                .institutionId(institution != null ? institution.getId() : null)
                .institutionName(institution != null ? institution.getName() : null)
                .name(account.getName())
                .iban(account.getIban())
                .accountTypeId(accountType != null ? accountType.getId() : null)
                .accountTypeName(accountType != null ? accountType.getName() : null)
                .currency(account.getCurrency())
            .lastBalanceReal(toDouble(account.getLastBalanceReal()))
                .lastBalanceRealDate(account.getLastBalanceRealDate())
            .lastBalanceAvailable(toDouble(account.getLastBalanceAvailable()))
                .lastBalanceAvailableDate(account.getLastBalanceAvailableDate())
                .isActive(account.getIsActive())
                .build();
    }

    public Account toEntity(AccountDTO accountDTO) {
        Account account = Account.builder()
        .id(accountDTO.getId())
        .name(accountDTO.getName())
        .iban(accountDTO.getIban())
        .currency(accountDTO.getCurrency())
        .lastBalanceReal(toBigDecimal(accountDTO.getLastBalanceReal()))
        .lastBalanceRealDate(accountDTO.getLastBalanceRealDate())
        .lastBalanceAvailable(toBigDecimal(accountDTO.getLastBalanceAvailable()))
        .lastBalanceAvailableDate(accountDTO.getLastBalanceAvailableDate())
        .isActive(accountDTO.getIsActive())
        .build();
        return account;
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}
