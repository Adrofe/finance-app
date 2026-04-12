package es.triana.company.banking.service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.AccountType;
import es.triana.company.banking.model.db.Institution;
import es.triana.company.banking.repository.AccountTypeRepository;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.InstitutionRepository;
import es.triana.company.banking.service.exception.AccountTypeNotFoundException;
import es.triana.company.banking.service.exception.InstitutionNotFoundException;
import es.triana.company.banking.service.exception.AccountNotFoundException;
import es.triana.company.banking.service.exception.AccountValidationException;
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
    private InstitutionRepository institutionRepository;
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

    public AccountDTO createAccount(AccountDTO account, Long tenantId) {
        Account accountEntity = accountMapper.toEntity(account);
        accountEntity.setTenantId(tenantId);
        normalizeAccount(accountEntity);
        validateUniqueIbanForCreate(accountEntity);
        accountEntity.setAccountType(resolveAccountType(account.getAccountTypeId()));
        accountEntity.setInstitution(resolveInstitution(account.getInstitutionId()));
        accountEntity.setCreatedAt(LocalDateTime.now());
        accountEntity.setUpdatedAt(LocalDateTime.now());
        return accountMapper.toDto(accountsRepository.save(accountEntity));
    }

    public void deleteAccount(Long id, Long tenantId) {
        if (id == null) {
            throw new AccountValidationException("Account id is required");
        }

        if (tenantId == null) {
            throw new AccountValidationException("Tenant id is required");
        }

        Account account = accountsRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        validateTenantOwnership(account, tenantId);
        accountsRepository.deleteById(id);
    }

    public AccountDTO getAccountById(Long id, Long tenantId) {
        if (id == null) {
            throw new AccountValidationException("Account id is required");
        }

        if (tenantId == null) {
            throw new AccountValidationException("Tenant id is required");
        }

        Account account = accountsRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        validateTenantOwnership(account, tenantId);
        return accountMapper.toDto(account);
    }

    public AccountDTO updateAccount(AccountDTO updatedAccount, Long tenantId) {
        Account existingAccount = accountsRepository.findById(updatedAccount.getId())
                .orElseThrow(() -> new AccountNotFoundException(updatedAccount.getId()));

        validateTenantOwnership(existingAccount, tenantId);

        updateEntityFromDto(existingAccount, updatedAccount);
        validateUniqueIbanForUpdate(existingAccount);
        existingAccount.setUpdatedAt(LocalDateTime.now());

        return accountMapper.toDto(accountsRepository.save(existingAccount));
    }

    private void updateEntityFromDto(Account existingAccount, AccountDTO updatedAccount) {
        existingAccount.setName(normalizeName(updatedAccount.getName()));
        existingAccount.setIban(normalizeIban(updatedAccount.getIban()));
        existingAccount.setAccountType(resolveAccountType(updatedAccount.getAccountTypeId()));
        existingAccount.setInstitution(resolveInstitution(updatedAccount.getInstitutionId()));
        existingAccount.setCurrency(normalizeCurrency(updatedAccount.getCurrency()));
        existingAccount.setLastBalanceReal(toBigDecimal(updatedAccount.getLastBalanceReal()));
        existingAccount.setLastBalanceRealDate(updatedAccount.getLastBalanceRealDate());
        existingAccount.setLastBalanceAvailable(toBigDecimal(updatedAccount.getLastBalanceAvailable()));
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

    private Institution resolveInstitution(Long institutionId) {
        if (institutionId == null) {
            return null;
        }

        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionId));
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

    private void validateTenantOwnership(Account existingAccount, Long tenantId) {
        if (!existingAccount.getTenantId().equals(tenantId)) {
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

    /**
     * Updates account balance incrementally by adding a delta amount.
     * This method is designed to be called when transactions are created, updated, or deleted.
     * 
     * @param accountId The ID of the account to update
     * @param tenantId The tenant ID for security validation
     * @param amountDelta The amount to add to the current balance (can be negative)
     */
    public void updateAccountBalance(Long accountId, Long tenantId, BigDecimal amountDelta) {
        if (accountId == null) {
            throw new AccountValidationException("Account id is required");
        }

        if (tenantId == null) {
            throw new AccountValidationException("Tenant id is required");
        }

        if (amountDelta == null) {
            throw new AccountValidationException("Amount delta is required");
        }

        Account account = accountsRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (!account.getTenantId().equals(tenantId)) {
            throw new AccountNotFoundException(accountId);
        }

        BigDecimal currentBalance = account.getLastBalanceReal() != null
                ? account.getLastBalanceReal()
                : BigDecimal.ZERO;

        BigDecimal newBalance = currentBalance.add(amountDelta);

        account.setLastBalanceReal(newBalance);
        account.setLastBalanceRealDate(LocalDate.now());
        account.setUpdatedAt(LocalDateTime.now());
        accountsRepository.save(account);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}
