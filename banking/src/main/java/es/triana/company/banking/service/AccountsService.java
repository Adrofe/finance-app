package es.triana.company.banking.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.AccountType;
import es.triana.company.banking.repository.AccountTypeRepository;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.service.exception.AccountTypeNotFoundException;
import es.triana.company.banking.service.exception.AccountNotFoundException;
import es.triana.company.banking.service.exception.DuplicateAccountIbanException;
import es.triana.company.banking.service.exception.TenantMismatchException;
import es.triana.company.banking.service.mapper.AccountMapper;

@Service
public class AccountsService {

    @Autowired
    private AccountsRepository accountsRepository;
    @Autowired
    private AccountTypeRepository accountTypeRepository;
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
        normalizeAccount(accountEntity);
        validateUniqueIbanForCreate(accountEntity);
        accountEntity.setAccountType(resolveAccountType(account.getAccountTypeId()));
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

        validateTenantOwnership(existingAccount, updatedAccount);

        updateEntityFromDto(existingAccount, updatedAccount);
        validateUniqueIbanForUpdate(existingAccount);
        existingAccount.setUpdatedAt(LocalDateTime.now());

        return accountMapper.toDto(accountsRepository.save(existingAccount));
    }

    private void updateEntityFromDto(Account existingAccount, AccountDTO updatedAccount) {
        existingAccount.setName(normalizeName(updatedAccount.getName()));
        existingAccount.setIban(normalizeIban(updatedAccount.getIban()));
        existingAccount.setAccountType(resolveAccountType(updatedAccount.getAccountTypeId()));
        existingAccount.setCurrency(normalizeCurrency(updatedAccount.getCurrency()));
        existingAccount.setLastBalanceReal(updatedAccount.getLastBalanceReal());
        existingAccount.setLastBalanceRealDate(updatedAccount.getLastBalanceRealDate());
        existingAccount.setLastBalanceAvailable(updatedAccount.getLastBalanceAvailable());
        existingAccount.setLastBalanceAvailableDate(updatedAccount.getLastBalanceAvailableDate());
        existingAccount.setIsActive(updatedAccount.getIsActive());
    }

    private AccountType resolveAccountType(Long accountTypeId) {
        if (accountTypeId == null) {
            return null;
        }

        return accountTypeRepository.findById(accountTypeId)
                .orElseThrow(() -> new AccountTypeNotFoundException(accountTypeId));
    }

    private void validateUniqueIbanForCreate(Account account) {
        if (account.getIban() != null && accountsRepository.existsByTenantIdAndIban(account.getTenantId(), account.getIban())) {
            throw new DuplicateAccountIbanException(account.getIban(), account.getTenantId());
        }
    }

    private void validateUniqueIbanForUpdate(Account account) {
        if (account.getIban() != null && accountsRepository.existsByTenantIdAndIbanAndIdNot(account.getTenantId(), account.getIban(), account.getId())) {
            throw new DuplicateAccountIbanException(account.getIban(), account.getTenantId());
        }
    }

    private void validateTenantOwnership(Account existingAccount, AccountDTO updatedAccount) {
        if (updatedAccount.getTenantId() != null && !existingAccount.getTenantId().equals(updatedAccount.getTenantId())) {
            throw new TenantMismatchException(existingAccount.getId());
        }
    }

    private void normalizeAccount(Account account) {
        account.setName(normalizeName(account.getName()));
        account.setIban(normalizeIban(account.getIban()));
        account.setCurrency(normalizeCurrency(account.getCurrency()));
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim();
    }

    private String normalizeIban(String iban) {
        if (iban == null) {
            return null;
        }

        String normalizedIban = iban.replace(" ", "").trim().toUpperCase();
        return normalizedIban.isEmpty() ? null : normalizedIban;
    }

    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase();
    }
}
