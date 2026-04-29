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
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.exception.AccountConflictException;
import es.triana.company.banking.service.exception.AccountTypeNotFoundException;
import es.triana.company.banking.service.exception.InstitutionNotFoundException;
import es.triana.company.banking.service.exception.AccountNotFoundException;
import es.triana.company.banking.service.exception.AccountValidationException;
import es.triana.company.banking.service.exception.DuplicateAccountIbanException;
import es.triana.company.banking.service.exception.TenantMismatchException;
import es.triana.company.banking.service.mapper.AccountMapper;
import es.triana.company.banking.repository.TransactionRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountsService {

    @Autowired
    private AccountsRepository accountsRepository;
    @Autowired
    private AccountTypeRepository accountTypeRepository;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private TransactionRepository transactionRepository;

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
        // If a real balance is provided from the frontend, set the real balance date.
        BigDecimal initialBalance = account.getLastBalanceReal() != null ? toBigDecimal(account.getLastBalanceReal()) : BigDecimal.ZERO;
        accountEntity.setLastBalanceReal(initialBalance);
        accountEntity.setLastBalanceAvailable(initialBalance);

        LocalDate now = LocalDate.now();
        accountEntity.setLastBalanceRealDate(now);
        accountEntity.setLastBalanceAvailableDate(now);

        accountEntity.setCreatedAt(LocalDateTime.now());
        accountEntity.setUpdatedAt(LocalDateTime.now());
        accountEntity.setIsActive(true);

        Account saved = accountsRepository.save(accountEntity);
        return accountMapper.toDto(saved);
    }

    @Transactional
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
        // delete related transactions first to avoid FK constraint violations
        var txs = transactionRepository.findAllByTenantIdAndAccountId(tenantId, id);
        if (txs != null && !txs.isEmpty()) {
            transactionRepository.deleteAll(txs);
        }
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

        // update fields except available balance (server computes it)
        updateEntityFromDto(existingAccount, updatedAccount);
        validateUniqueIbanForUpdate(existingAccount);
        existingAccount.setUpdatedAt(LocalDateTime.now());

        Account saved = accountsRepository.save(existingAccount);
        return accountMapper.toDto(saved);
    }

    private void updateEntityFromDto(Account existingAccount, AccountDTO updatedAccount) {
        existingAccount.setName(normalizeName(updatedAccount.getName()));
        existingAccount.setIban(normalizeIban(updatedAccount.getIban()));
        existingAccount.setAccountType(resolveAccountType(updatedAccount.getAccountTypeId()));
        existingAccount.setInstitution(resolveInstitution(updatedAccount.getInstitutionId()));
        existingAccount.setCurrency(normalizeCurrency(updatedAccount.getCurrency()));
        // Do not allow clients to set balances on update. Balances are computed
        // and maintained server-side via transactions. Ignore any balance fields
        // present in the incoming DTO to avoid inconsistencies.
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
            throw new DuplicateAccountIbanException(account.getIban());
        }
    }

    private void validateUniqueIbanForUpdate(Account account) {
        if (account.getIban() != null && accountsRepository.existsByTenantIdAndIbanAndIdNot(account.getTenantId(), account.getIban(), account.getId())) {
            throw new DuplicateAccountIbanException(account.getIban());
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
     * Unified balance update method. If {@code updateBoth} is true, both real and available
     * balances are updated by {@code amountDelta}. Otherwise only the available balance
     * is updated. This is the single entry point intended for transaction-driven updates.
     */
    public void updateAccountBalances(Long accountId, Long tenantId, BigDecimal amountDelta, boolean updateBoth) {
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

        BigDecimal currentAvailable = account.getLastBalanceAvailable() != null ? account.getLastBalanceAvailable() : BigDecimal.ZERO;
        BigDecimal newAvailable = currentAvailable.add(amountDelta);
        account.setLastBalanceAvailable(newAvailable);
        account.setLastBalanceAvailableDate(LocalDate.now());

        if (updateBoth) {
            BigDecimal currentReal = account.getLastBalanceReal() != null ? account.getLastBalanceReal() : BigDecimal.ZERO;
            BigDecimal newReal = currentReal.add(amountDelta);
            account.setLastBalanceReal(newReal);
            account.setLastBalanceRealDate(LocalDate.now());
        }

        account.setUpdatedAt(LocalDateTime.now());
        accountsRepository.save(account);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }
}
