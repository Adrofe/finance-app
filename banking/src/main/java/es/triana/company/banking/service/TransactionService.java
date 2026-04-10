package es.triana.company.banking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.mapper.TransactionMapper;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountsRepository accountsRepository;
    private final TransactionMapper transactionMapper;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountsRepository accountsRepository,
            TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.accountsRepository = accountsRepository;
        this.transactionMapper = transactionMapper;
    }

    public TransactionDTO createTransaction(TransactionDTO transactionDTO, Long tenantId) {
        validateCreateRequest(transactionDTO, tenantId);

        Account sourceAccount = getRequiredAccount(transactionDTO.getSourceAccountId(), "Source");
        validateAccountBelongsToTenant(sourceAccount, tenantId, transactionDTO.getSourceAccountId(), "Source");

        if (transactionDTO.getDestinationAccountId() != null) {
            Account destinationAccount = getRequiredAccount(transactionDTO.getDestinationAccountId(), "Destination");
            validateAccountBelongsToTenant(destinationAccount, tenantId, transactionDTO.getDestinationAccountId(), "Destination");
        }

        validateExternalIdUniqueness(transactionDTO.getExternalId(), tenantId);

        String normalizedCurrency = normalizeCurrency(transactionDTO.getCurrency());
        LocalDateTime timestamp = LocalDateTime.now();

        Transaction transaction = transactionMapper.toEntity(transactionDTO, tenantId, normalizedCurrency, timestamp);

        Transaction savedTransaction = transactionRepository.save(transaction);
        return transactionMapper.toDto(savedTransaction);
    }

    public TransactionDTO getTransactionById(Long transactionId, Long tenantId) {
        validateTenantAccess(transactionId, tenantId, "Transaction id is required");

        Transaction transaction = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + transactionId));

        return transactionMapper.toDto(transaction);
    }

    public List<TransactionDTO> getTransactionsByAccount(Long accountId, Long tenantId) {
        validateTenantAccess(accountId, tenantId, "Account id is required");

        Account account = getRequiredAccount(accountId, "Account");
        validateAccountBelongsToTenant(account, tenantId, accountId, "Account");

        return transactionRepository.findAllByTenantIdAndAccountId(tenantId, accountId).stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    public List<TransactionDTO> getTransactionsByTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id is required");
        }

        return transactionRepository.findAllByTenantIdOrderByBookingDateDescIdDesc(tenantId).stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    public List<TransactionDTO> getTransactionsByDateRange(LocalDate startDate, LocalDate endDate, Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id is required");
        }

        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }

        if (endDate == null) {
            throw new IllegalArgumentException("End date is required");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be greater than or equal to start date");
        }

        return transactionRepository.findAllByTenantIdAndBookingDateBetweenOrderByBookingDateDescIdDesc(tenantId, startDate, endDate)
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    public TransactionDTO updateTransaction(Long transactionId, TransactionDTO transactionDTO, Long tenantId) {
        validateTenantAccess(transactionId, tenantId, "Transaction id is required");
        validateCreateRequest(transactionDTO, tenantId);

        Transaction existingTransaction = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + transactionId));

        Account sourceAccount = getRequiredAccount(transactionDTO.getSourceAccountId(), "Source");
        validateAccountBelongsToTenant(sourceAccount, tenantId, transactionDTO.getSourceAccountId(), "Source");

        if (transactionDTO.getDestinationAccountId() != null) {
            Account destinationAccount = getRequiredAccount(transactionDTO.getDestinationAccountId(), "Destination");
            validateAccountBelongsToTenant(destinationAccount, tenantId, transactionDTO.getDestinationAccountId(), "Destination");
        }

        validateExternalIdUniquenessForUpdate(transactionDTO.getExternalId(), tenantId, transactionId);

        String normalizedCurrency = normalizeCurrency(transactionDTO.getCurrency());
        LocalDateTime timestamp = LocalDateTime.now();

        transactionMapper.updateEntity(existingTransaction, transactionDTO, tenantId, normalizedCurrency, timestamp);

        Transaction savedTransaction = transactionRepository.save(existingTransaction);
        return transactionMapper.toDto(savedTransaction);
    }

    public void deleteTransaction(Long transactionId, Long tenantId) {
        validateTenantAccess(transactionId, tenantId, "Transaction id is required");

        Transaction transaction = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + transactionId));

        transactionRepository.delete(transaction);
    }

    public Double getAccountBalance(Long accountId, Long tenantId) {
        validateTenantAccess(accountId, tenantId, "Account id is required");

        Account account = accountsRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found with id: " + accountId));

        if (!tenantId.equals(account.getTenantId())) {
            throw new NoSuchElementException("Account not found with id: " + accountId);
        }

        if (account.getLastBalanceReal() != null) {
            return account.getLastBalanceReal();
        }

        if (account.getLastBalanceAvailable() != null) {
            return account.getLastBalanceAvailable();
        }

        return 0.0;
    }

    public Double getTenantBalance(Long tenantId) {
        if (tenantId == null) {
            throw new NoSuchElementException("Tenant not found with id: null");
        }

        List<Account> accounts = accountsRepository.findByTenantId(tenantId);
        if (accounts.isEmpty()) {
            throw new NoSuchElementException("Tenant not found with id: " + tenantId);
        }

        return accounts.stream()
                .mapToDouble(this::resolveCurrentBalance)
                .sum();
    }

    public List<TransactionDTO> getTransactionsByCategory(Long categoryId, Long tenantId) {
        validateTenantAccess(categoryId, tenantId, "Category id is required");

        return transactionRepository.findAllByTenantIdAndCategoryIdOrderByBookingDateDescIdDesc(tenantId, categoryId)
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    private void validateCreateRequest(TransactionDTO transactionDTO, Long tenantId) {
        if (transactionDTO == null) {
            throw new IllegalArgumentException("Transaction payload is required");
        }

        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id is required");
        }

        if (transactionDTO.getTenantId() != null && !Objects.equals(transactionDTO.getTenantId(), tenantId)) {
            throw new IllegalArgumentException("Transaction tenant does not match authenticated tenant");
        }

        if (transactionDTO.getSourceAccountId() == null) {
            throw new IllegalArgumentException("Source account id is required");
        }

        if (transactionDTO.getBookingDate() == null) {
            throw new IllegalArgumentException("Booking date is required");
        }

        if (transactionDTO.getAmount() == null) {
            throw new IllegalArgumentException("Amount must be provided");
        }

        // Allow positive amounts (income) and negative amounts (expenses).
        // Only reject zero values because they don't represent a movement.
        if (transactionDTO.getAmount().doubleValue() == 0.0) {
            throw new IllegalArgumentException("Amount must be non-zero");
        }

        normalizeCurrency(transactionDTO.getCurrency());
    }

    private Account getRequiredAccount(Long accountId, String role) {
        return accountsRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(role + " account not found with id: " + accountId));
    }

    private void validateAccountBelongsToTenant(Account account, Long tenantId, Long accountId, String role) {
        if (!tenantId.equals(account.getTenantId())) {
            throw new IllegalArgumentException(role + " account not found with id: " + accountId);
        }
    }

    private void validateExternalIdUniqueness(String externalId, Long tenantId) {
        String normalizedExternalId = transactionMapper.normalizeExternalId(externalId);
        if (normalizedExternalId != null && transactionRepository.existsByTenantIdAndExternalTxId(tenantId, normalizedExternalId)) {
            throw new IllegalArgumentException("Transaction with external id '" + normalizedExternalId + "' already exists for tenant " + tenantId);
        }
    }

    private void validateExternalIdUniquenessForUpdate(String externalId, Long tenantId, Long transactionId) {
        String normalizedExternalId = transactionMapper.normalizeExternalId(externalId);
        if (normalizedExternalId != null
                && transactionRepository.existsByTenantIdAndExternalTxIdAndIdNot(tenantId, normalizedExternalId, transactionId)) {
            throw new IllegalArgumentException("Transaction with external id '" + normalizedExternalId + "' already exists for tenant " + tenantId);
        }
    }

    private String normalizeCurrency(String currency) {
        String normalizedCurrency = currency == null ? null : currency.trim().toUpperCase();
        if (normalizedCurrency == null || !normalizedCurrency.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Currency must be a 3-letter uppercase ISO code");
        }
        return normalizedCurrency;
    }

    private void validateTenantAccess(Long resourceId, Long tenantId, String resourceErrorMessage) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id is required");
        }

        if (resourceId == null) {
            throw new IllegalArgumentException(resourceErrorMessage);
        }
    }

    private double resolveCurrentBalance(Account account) {
        if (account.getLastBalanceReal() != null) {
            return account.getLastBalanceReal();
        }

        if (account.getLastBalanceAvailable() != null) {
            return account.getLastBalanceAvailable();
        }

        return 0.0;
    }

}
