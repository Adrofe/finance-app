package es.triana.company.banking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.service.exception.AccountNotFoundException;
import es.triana.company.banking.service.mapper.AccountMapper;

@Service
public class AccountsService {

    @Autowired
    private AccountsRepository accountsRepository;
    @Autowired
    private AccountMapper accountMapper;

    public List<AccountDTO> getAccountsByTenant(String tenantId) {
        if (tenantId == null) {
            return getAllAccounts();
        } else {
            return getAccountsForTenant(tenantId);
        }
    }

    private List<AccountDTO> getAllAccounts() {
        return accountsRepository.findAll().stream()
                .map(accountMapper::toDto)
                .toList();
    }

    private List<AccountDTO> getAccountsForTenant(String tenantId) {
        return accountsRepository.findByTenantId(Long.parseLong(tenantId)).stream()
                .map(accountMapper::toDto)
                .toList();
    }

    public AccountDTO createAccount(AccountDTO account) {
        Account accountEntity = accountMapper.toEntity(account);
        accountEntity.setCreatedAt(LocalDateTime.now());
        accountEntity.setUpdatedAt(LocalDateTime.now());
        return accountMapper.toDto(accountsRepository.save(accountEntity));
    }

    public void deleteAccount(Long id) {
        if (!accountsRepository.existsById(id)) {
            throw new AccountNotFoundException(id);
        }
        accountsRepository.deleteById(id);
    }

    public AccountDTO getAccountById(Long id) {
        return accountsRepository.findById(id)
                .map(accountMapper::toDto)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    public AccountDTO updateAccount(AccountDTO updatedAccount) {
        Account existingAccount = accountsRepository.findById(updatedAccount.getId())
                .orElseThrow(() -> new AccountNotFoundException(updatedAccount.getId()));

        updateEntityFromDto(existingAccount, updatedAccount);
        existingAccount.setUpdatedAt(LocalDateTime.now());

        return accountMapper.toDto(accountsRepository.save(existingAccount));
    }

    private void updateEntityFromDto(Account existingAccount, AccountDTO updatedAccount) {
        existingAccount.setName(updatedAccount.getName());
        existingAccount.setIban(updatedAccount.getIban());
        existingAccount.setType(updatedAccount.getType());
        existingAccount.setCurrency(updatedAccount.getCurrency());
        existingAccount.setLastBalanceReal(updatedAccount.getLastBalanceReal());
        existingAccount.setLastBalanceRealDate(updatedAccount.getLastBalanceRealDate());
        existingAccount.setLastBalanceAvailable(updatedAccount.getLastBalanceAvailable());
        existingAccount.setLastBalanceAvailableDate(updatedAccount.getLastBalanceAvailableDate());
        existingAccount.setIsActive(updatedAccount.getIsActive());
    }
}
