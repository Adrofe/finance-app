package es.triana.company.banking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Category;
import es.triana.company.banking.model.db.Merchant;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.CategoryRepository;
import es.triana.company.banking.repository.MerchantRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.service.mapper.TransactionMapper;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountsRepository accountsRepository;
    private final CategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionMapper transactionMapper;
    private final AccountsService accountsService;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountsRepository accountsRepository,
            CategoryRepository categoryRepository,
            MerchantRepository merchantRepository,
            TransactionMapper transactionMapper,
            AccountsService accountsService) {
        this.transactionRepository = transactionRepository;
        this.accountsRepository = accountsRepository;
        this.categoryRepository = categoryRepository;
        this.merchantRepository = merchantRepository;
        this.transactionMapper = transactionMapper;
        this.accountsService = accountsService;
    }

    public TransactionDTO createTransaction(TransactionDTO transactionDTO, Long tenantId) {
        validateCreateRequest(transactionDTO, tenantId);

        Account sourceAccount = getRequiredAccount(transactionDTO.getSourceAccountId(), "Source");
        validateAccountBelongsToTenant(sourceAccount, tenantId, transactionDTO.getSourceAccountId(), "Source");

        Account destinationAccount = null;
        if (transactionDTO.getDestinationAccountId() != null) {
            destinationAccount = getRequiredAccount(transactionDTO.getDestinationAccountId(), "Destination");
            validateAccountBelongsToTenant(destinationAccount, tenantId, transactionDTO.getDestinationAccountId(), "Destination");
        }

        validateExternalIdUniqueness(transactionDTO.getExternalId(), tenantId);

        Merchant merchant = resolveMerchant(transactionDTO.getMerchantId());
        Category category = resolveCategory(transactionDTO.getCategoryId());
        String normalizedCurrency = normalizeCurrency(transactionDTO.getCurrency());
        LocalDateTime timestamp = LocalDateTime.now();

    Transaction transaction = transactionMapper.toEntity(transactionDTO, sourceAccount, destinationAccount, merchant, category, tenantId, normalizedCurrency, timestamp);

        Transaction savedTransaction = transactionRepository.save(transaction);
        applyBalanceForCreate(savedTransaction, tenantId);
        
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

        // Store old transaction data to revert balance effects
        Long oldSourceAccountId = existingTransaction.getSourceAccount().getId();
        Long oldDestinationAccountId = existingTransaction.getDestinationAccount() != null ? existingTransaction.getDestinationAccount().getId() : null;
        java.math.BigDecimal oldAmount = existingTransaction.getAmount();

        Account sourceAccount = getRequiredAccount(transactionDTO.getSourceAccountId(), "Source");
        validateAccountBelongsToTenant(sourceAccount, tenantId, transactionDTO.getSourceAccountId(), "Source");

        Account destinationAccount = null;
        if (transactionDTO.getDestinationAccountId() != null) {
            destinationAccount = getRequiredAccount(transactionDTO.getDestinationAccountId(), "Destination");
            validateAccountBelongsToTenant(destinationAccount, tenantId, transactionDTO.getDestinationAccountId(), "Destination");
        }

        validateExternalIdUniquenessForUpdate(transactionDTO.getExternalId(), tenantId, transactionId);

        Merchant merchant = resolveMerchant(transactionDTO.getMerchantId());
        Category category = resolveCategory(transactionDTO.getCategoryId());
        String normalizedCurrency = normalizeCurrency(transactionDTO.getCurrency());
        LocalDateTime timestamp = LocalDateTime.now();

    transactionMapper.updateEntity(existingTransaction, transactionDTO, sourceAccount, destinationAccount, merchant, category, tenantId, normalizedCurrency, timestamp);

        Transaction savedTransaction = transactionRepository.save(existingTransaction);
        
        revertBalanceForUpdate(oldSourceAccountId, oldDestinationAccountId, oldAmount, tenantId);
        applyBalanceForUpdate(savedTransaction, tenantId);
        
        return transactionMapper.toDto(savedTransaction);
    }

    public void deleteTransaction(Long transactionId, Long tenantId) {
        validateTenantAccess(transactionId, tenantId, "Transaction id is required");

        Transaction transaction = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + transactionId));

        Long sourceAccountId = transaction.getSourceAccount().getId();
        Long destinationAccountId = transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getId() : null;
        java.math.BigDecimal amount = transaction.getAmount();
        
        transactionRepository.delete(transaction);
        revertBalanceForDelete(sourceAccountId, destinationAccountId, amount, tenantId);
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

        return transactionRepository.findAllByTenantIdAndCategory_IdOrderByBookingDateDescIdDesc(tenantId, categoryId)
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

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));
    }

    private Merchant resolveMerchant(Long merchantId) {
        if (merchantId == null) {
            return null;
        }

        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found with id: " + merchantId));
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

    /**
     * Applies balance updates for a newly created transaction.
     * For simple transactions (no destination): adds amount directly to source account.
     * For internal transfers: subtracts from source, adds to destination.
     * 
     * @param transaction The created transaction
     * @param tenantId The tenant ID for validation
     */
    private void applyBalanceForCreate(Transaction transaction, Long tenantId) {
        if (transaction.getDestinationAccount() == null) {
            // Simple transaction: add amount directly (can be positive or negative)
            accountsService.updateAccountBalance(
                transaction.getSourceAccount().getId(), 
                tenantId, 
                transaction.getAmount()
            );
        } else {
            // Internal transfer: subtract from source, add to destination
            accountsService.updateAccountBalance(
                transaction.getSourceAccount().getId(), 
                tenantId, 
                transaction.getAmount().negate()
            );
            accountsService.updateAccountBalance(
                transaction.getDestinationAccount().getId(), 
                tenantId, 
                transaction.getAmount()
            );
        }
    }

    /**
     * Reverts balance effects of the old transaction before applying updates.
     * This is the opposite operation of what was originally applied.
     * 
     * @param oldSourceAccountId The old source account ID
     * @param oldDestinationAccountId The old destination account ID (null for simple transactions)
     * @param oldAmount The old transaction amount
     * @param tenantId The tenant ID for validation
     */
    private void revertBalanceForUpdate(Long oldSourceAccountId, Long oldDestinationAccountId, 
                                        java.math.BigDecimal oldAmount, Long tenantId) {
        if (oldDestinationAccountId == null) {
            // Was a simple transaction: subtract the old amount
            accountsService.updateAccountBalance(oldSourceAccountId, tenantId, oldAmount.negate());
        } else {
            // Was a transfer: revert by adding to source, subtracting from destination
            accountsService.updateAccountBalance(oldSourceAccountId, tenantId, oldAmount);
            accountsService.updateAccountBalance(oldDestinationAccountId, tenantId, oldAmount.negate());
        }
    }

    /**
     * Applies balance updates for the updated transaction.
     * Same logic as create operation.
     * 
     * @param transaction The updated transaction
     * @param tenantId The tenant ID for validation
     */
    private void applyBalanceForUpdate(Transaction transaction, Long tenantId) {
        if (transaction.getDestinationAccount() == null) {
            // Simple transaction: add amount
            accountsService.updateAccountBalance(
                transaction.getSourceAccount().getId(), 
                tenantId, 
                transaction.getAmount()
            );
        } else {
            // Transfer: subtract from source, add to destination
            accountsService.updateAccountBalance(
                transaction.getSourceAccount().getId(), 
                tenantId, 
                transaction.getAmount().negate()
            );
            accountsService.updateAccountBalance(
                transaction.getDestinationAccount().getId(), 
                tenantId, 
                transaction.getAmount()
            );
        }
    }

    /**
     * Reverts balance effects of a deleted transaction.
     * This undoes what was originally applied when the transaction was created.
     * 
     * @param sourceAccountId The source account ID
     * @param destinationAccountId The destination account ID (null for simple transactions)
     * @param amount The transaction amount
     * @param tenantId The tenant ID for validation
     */
    private void revertBalanceForDelete(Long sourceAccountId, Long destinationAccountId, 
                                        java.math.BigDecimal amount, Long tenantId) {
        if (destinationAccountId == null) {
            // Was a simple transaction: subtract the amount to revert
            accountsService.updateAccountBalance(sourceAccountId, tenantId, amount.negate());
        } else {
            // Was a transfer: add to source (revert subtraction), subtract from destination (revert addition)
            accountsService.updateAccountBalance(sourceAccountId, tenantId, amount);
            accountsService.updateAccountBalance(destinationAccountId, tenantId, amount.negate());
        }
    }

}
