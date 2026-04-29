package es.triana.company.banking.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.PagedResponse;
import es.triana.company.banking.model.api.TransactionFilterRequest;
import es.triana.company.banking.repository.TransactionSpecification;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Category;
import es.triana.company.banking.model.db.Merchant;
import es.triana.company.banking.model.db.Tag;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.model.db.TransactionStatus;
import es.triana.company.banking.model.db.TransactionType;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.CategoryRepository;
import es.triana.company.banking.repository.MerchantRepository;
import es.triana.company.banking.repository.TagRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.repository.TransactionStatusRepository;
import es.triana.company.banking.repository.TransactionTypeRepository;
import es.triana.company.banking.service.exception.TransactionConflictException;
import es.triana.company.banking.service.exception.TransactionNotFoundException;
import es.triana.company.banking.service.exception.TransactionValidationException;
import es.triana.company.banking.service.mapper.TransactionMapper;

@Service
public class TransactionService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "bookingDate", "valueDate", "amount", "createdAt", "id");

    private final TransactionRepository transactionRepository;
    private final AccountsRepository accountsRepository;
    private final CategoryRepository categoryRepository;
    private final MerchantRepository merchantRepository;
    private final TagRepository tagRepository;
    private final TransactionStatusRepository transactionStatusRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final TransactionMapper transactionMapper;
    private final AccountsService accountsService;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountsRepository accountsRepository,
            CategoryRepository categoryRepository,
            MerchantRepository merchantRepository,
            TagRepository tagRepository,
            TransactionStatusRepository transactionStatusRepository,
            TransactionTypeRepository transactionTypeRepository,
            TransactionMapper transactionMapper,
            AccountsService accountsService) {
        this.transactionRepository = transactionRepository;
        this.accountsRepository = accountsRepository;
        this.categoryRepository = categoryRepository;
        this.merchantRepository = merchantRepository;
        this.tagRepository = tagRepository;
        this.transactionStatusRepository = transactionStatusRepository;
        this.transactionTypeRepository = transactionTypeRepository;
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
        Set<Tag> tags = resolveTags(transactionDTO.getTagIds(), tenantId);
        TransactionStatus status = resolveStatus(transactionDTO.getStatusId());
        TransactionType transactionType = resolveTransactionType(transactionDTO.getTypeId());
        String normalizedCurrency = normalizeCurrency(transactionDTO.getCurrency());
        LocalDateTime timestamp = LocalDateTime.now();

        Transaction transaction = transactionMapper.toEntity(
                transactionDTO,
                sourceAccount,
                destinationAccount,
                merchant,
                category,
                status,
                transactionType,
                tags,
                tenantId,
                normalizedCurrency,
                timestamp);

        if (destinationAccount != null) {
            // Double-entry: save source transaction, then create mirror for the destination
            Transaction savedSource = transactionRepository.save(transaction);
            applySingleAccountBalance(savedSource, tenantId);

            Transaction mirror = Transaction.builder()
                    .tenantId(tenantId)
                    .sourceAccount(destinationAccount)
                    .bookingDate(savedSource.getBookingDate())
                    .valueDate(savedSource.getValueDate())
                    .amount(savedSource.getAmount().negate())
                    .currency(normalizedCurrency)
                    .descriptionRaw(savedSource.getDescriptionRaw())
                    .merchant(merchant)
                    .category(category)
                    .tags(new LinkedHashSet<>(tags))
                    .status(status)
                    .transactionType(transactionType)
                    .linkedTransactionId(savedSource.getId())
                    .createdAt(timestamp)
                    .updatedAt(timestamp)
                    .build();

            Transaction savedMirror = transactionRepository.save(mirror);
            applySingleAccountBalance(savedMirror, tenantId);

            savedSource.setLinkedTransactionId(savedMirror.getId());
            savedSource = transactionRepository.save(savedSource);
            return transactionMapper.toDto(savedSource);
        } else {
            Transaction savedTransaction = transactionRepository.save(transaction);
            applyBalanceForCreate(savedTransaction, tenantId);
            return transactionMapper.toDto(savedTransaction);
        }
    }

    public TransactionDTO getTransactionById(Long transactionId, Long tenantId) {
        validateTenantAccess(transactionId, tenantId, "Transaction id is required");

        Transaction transaction = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with id: " + transactionId));

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
            throw new TransactionValidationException("Tenant id is required");
        }

        return transactionRepository.findAllByTenantIdOrderByBookingDateDescIdDesc(tenantId).stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    public List<TransactionDTO> getTransactionsByDateRange(LocalDate startDate, LocalDate endDate, Long tenantId) {
        if (tenantId == null) {
            throw new TransactionValidationException("Tenant id is required");
        }

        if (startDate == null) {
            throw new TransactionValidationException("Start date is required");
        }

        if (endDate == null) {
            throw new TransactionValidationException("End date is required");
        }

        if (endDate.isBefore(startDate)) {
            throw new TransactionValidationException("End date must be greater than or equal to start date");
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
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with id: " + transactionId));

        Long oldSourceAccountId = existingTransaction.getSourceAccount().getId();
        Long oldDestinationAccountId = existingTransaction.getDestinationAccount() != null ? existingTransaction.getDestinationAccount().getId() : null;
        java.math.BigDecimal oldAmount = existingTransaction.getAmount();
        TransactionStatus oldStatus = existingTransaction.getStatus();

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
        Set<Tag> tags = resolveTags(transactionDTO.getTagIds(), tenantId);
        TransactionStatus status = resolveStatus(transactionDTO.getStatusId());
        TransactionType transactionType = resolveTransactionType(transactionDTO.getTypeId());
        String normalizedCurrency = normalizeCurrency(transactionDTO.getCurrency());
        LocalDateTime timestamp = LocalDateTime.now();

        Long existingLinkedId = existingTransaction.getLinkedTransactionId();
        Transaction linkedTransaction = existingLinkedId != null
                ? transactionRepository.findByIdAndTenantId(existingLinkedId, tenantId).orElse(null)
                : null;

        transactionMapper.updateEntity(
                existingTransaction,
                transactionDTO,
                sourceAccount,
                destinationAccount,
                merchant,
                category,
                status,
                transactionType,
                tags,
                tenantId,
                normalizedCurrency,
                timestamp);

        if (linkedTransaction != null) {
            // Double-entry transfer: revert both sides, update both, apply both
            revertSingleAccountBalance(oldSourceAccountId, oldAmount, oldStatus, tenantId);
            revertSingleAccountBalance(linkedTransaction.getSourceAccount().getId(),
                    linkedTransaction.getAmount(), linkedTransaction.getStatus(), tenantId);

            Transaction savedTransaction = transactionRepository.save(existingTransaction);

            // Mirror source tracks the transfer destination
            Account mirrorSource = destinationAccount != null ? destinationAccount : linkedTransaction.getSourceAccount();
            linkedTransaction.setSourceAccount(mirrorSource);
            linkedTransaction.setAmount(savedTransaction.getAmount().negate());
            linkedTransaction.setBookingDate(savedTransaction.getBookingDate());
            linkedTransaction.setValueDate(savedTransaction.getValueDate());
            linkedTransaction.setDescriptionRaw(savedTransaction.getDescriptionRaw());
            linkedTransaction.setStatus(status);
            linkedTransaction.setCategory(category);
            linkedTransaction.setMerchant(merchant);
            linkedTransaction.setTransactionType(transactionType);
            linkedTransaction.setCurrency(normalizedCurrency);
            linkedTransaction.setUpdatedAt(timestamp);
            transactionRepository.save(linkedTransaction);

            applySingleAccountBalance(savedTransaction, tenantId);
            applySingleAccountBalance(linkedTransaction, tenantId);

            return transactionMapper.toDto(savedTransaction);
        } else {
            Transaction savedTransaction = transactionRepository.save(existingTransaction);
            revertBalanceForUpdate(oldSourceAccountId, oldDestinationAccountId, oldAmount, oldStatus, tenantId);
            applyBalanceForUpdate(savedTransaction, tenantId);
            return transactionMapper.toDto(savedTransaction);
        }
    }

    public void deleteTransaction(Long transactionId, Long tenantId) {
        validateTenantAccess(transactionId, tenantId, "Transaction id is required");

        Transaction transaction = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found with id: " + transactionId));

        Long linkedId = transaction.getLinkedTransactionId();
        Long sourceAccountId = transaction.getSourceAccount().getId();
        java.math.BigDecimal amount = transaction.getAmount();
        TransactionStatus status = transaction.getStatus();

        if (linkedId != null) {
            // Double-entry transfer: delete both halves
            transactionRepository.findByIdAndTenantId(linkedId, tenantId).ifPresent(mirror -> {
                Long mirrorSourceId = mirror.getSourceAccount().getId();
                java.math.BigDecimal mirrorAmount = mirror.getAmount();
                TransactionStatus mirrorStatus = mirror.getStatus();

                // Break mutual links first to avoid FK constraint issues
                mirror.setLinkedTransactionId(null);
                transactionRepository.save(mirror);
                transaction.setLinkedTransactionId(null);
                transactionRepository.save(transaction);

                transactionRepository.delete(mirror);
                revertSingleAccountBalance(mirrorSourceId, mirrorAmount, mirrorStatus, tenantId);
            });

            transactionRepository.delete(transaction);
            revertSingleAccountBalance(sourceAccountId, amount, status, tenantId);
        } else {
            // Non-transfer or legacy single transaction
            Long destinationAccountId = transaction.getDestinationAccount() != null
                    ? transaction.getDestinationAccount().getId() : null;
            transactionRepository.delete(transaction);
            revertBalanceForDelete(sourceAccountId, destinationAccountId, amount, status, tenantId);
        }
    }

    public Double getAccountBalance(Long accountId, Long tenantId) {
        validateTenantAccess(accountId, tenantId, "Account id is required");

        Account account = accountsRepository.findById(accountId)
                .orElseThrow(() -> new TransactionNotFoundException("Account not found with id: " + accountId));

        if (!tenantId.equals(account.getTenantId())) {
            throw new TransactionNotFoundException("Account not found with id: " + accountId);
        }

        if (account.getLastBalanceReal() != null) {
            return account.getLastBalanceReal().doubleValue();
        }

        if (account.getLastBalanceAvailable() != null) {
            return account.getLastBalanceAvailable().doubleValue();
        }

        return 0.0;
    }

    public Double getTenantBalance(Long tenantId) {
        if (tenantId == null) {
            throw new TransactionValidationException("Tenant id is required");
        }

        List<Account> accounts = accountsRepository.findByTenantId(tenantId);
        if (accounts.isEmpty()) {
            throw new TransactionNotFoundException("Tenant not found with id: " + tenantId);
        }

        return accounts.stream()
                .mapToDouble(this::resolveCurrentBalance)
                .sum();
    }

    public PagedResponse<TransactionDTO> searchTransactions(TransactionFilterRequest filter, Long tenantId) {
        if (tenantId == null) {
            throw new TransactionValidationException("Tenant id is required");
        }

        if (filter == null) {
            filter = new TransactionFilterRequest();
        }

        int page = Math.max(0, filter.getPage());
        int size = Math.min(Math.max(1, filter.getSize()), 100);
        String sortField = ALLOWED_SORT_FIELDS.contains(filter.getSortBy()) ? filter.getSortBy() : "bookingDate";
        Sort.Direction direction = "ASC".equalsIgnoreCase(filter.getSortDirection())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Sort sort = Sort.by(direction, sortField);
        if (!"id".equals(sortField)) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }

        PageRequest pageRequest = PageRequest.of(page, size, sort);
        TransactionSpecification spec = new TransactionSpecification(filter, tenantId);
        Page<TransactionDTO> resultPage = transactionRepository.findAll(spec, pageRequest)
                .map(transactionMapper::toDto);

        return PagedResponse.from(resultPage);
    }

    public List<TransactionDTO> getTransactionsByCategory(Long categoryId, Long tenantId) {
        validateTenantAccess(categoryId, tenantId, "Category id is required");

        return transactionRepository.findAllByTenantIdAndCategory_IdOrderByBookingDateDescIdDesc(tenantId, categoryId)
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    public List<TransactionDTO> getTransactionsByTag(Long tagId, Long tenantId) {
        validateTenantAccess(tagId, tenantId, "Tag id is required");

        tagRepository.findByIdAndTenantId(tagId, tenantId)
                .orElseThrow(() -> new TransactionNotFoundException("Tag not found with id: " + tagId));

        return transactionRepository.findDistinctByTenantIdAndTags_IdOrderByBookingDateDescIdDesc(tenantId, tagId)
                .stream()
                .map(transactionMapper::toDto)
                .toList();
    }

    private void validateCreateRequest(TransactionDTO transactionDTO, Long tenantId) {
        if (transactionDTO == null) {
            throw new TransactionValidationException("Transaction payload is required");
        }

        if (tenantId == null) {
            throw new TransactionValidationException("Tenant id is required");
        }

        if (transactionDTO.getSourceAccountId() == null) {
            throw new TransactionValidationException("Source account id is required");
        }

        if (transactionDTO.getBookingDate() == null) {
            throw new TransactionValidationException("Booking date is required");
        }

        if (transactionDTO.getAmount() == null) {
            throw new TransactionValidationException("Amount must be provided");
        }

        if (transactionDTO.getAmount().doubleValue() == 0.0) {
            throw new TransactionValidationException("Amount must be non-zero");
        }

        normalizeCurrency(transactionDTO.getCurrency());
    }

    private Account getRequiredAccount(Long accountId, String role) {
        return accountsRepository.findById(accountId)
                .orElseThrow(() -> new TransactionNotFoundException(role + " account not found with id: " + accountId));
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new TransactionNotFoundException("Category not found with id: " + categoryId));
    }

    private Merchant resolveMerchant(Long merchantId) {
        if (merchantId == null) {
            return null;
        }

        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new TransactionNotFoundException("Merchant not found with id: " + merchantId));
    }

    private Set<Tag> resolveTags(List<Long> tagIds, Long tenantId) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Tag> tags = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId == null) {
                throw new TransactionValidationException("Tag id is required");
            }

            Tag tag = tagRepository.findByIdAndTenantId(tagId, tenantId)
                    .orElseThrow(() -> new TransactionNotFoundException("Tag not found with id: " + tagId));
            tags.add(tag);
        }

        return tags;
    }

    private TransactionStatus resolveStatus(Long statusId) {
        Long resolvedStatusId = statusId != null ? statusId : 1L;

        return transactionStatusRepository.findById(resolvedStatusId)
                .orElseThrow(() -> new TransactionNotFoundException("Status not found with id: " + resolvedStatusId));
    }

    private TransactionType resolveTransactionType(Long typeId) {
        if (typeId == null) {
            return null;
        }

        return transactionTypeRepository.findById(typeId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction type not found with id: " + typeId));
    }

    private void validateAccountBelongsToTenant(Account account, Long tenantId, Long accountId, String role) {
        if (!tenantId.equals(account.getTenantId())) {
            throw new TransactionNotFoundException(role + " account not found with id: " + accountId);
        }
    }

    private void validateExternalIdUniqueness(String externalId, Long tenantId) {
        String normalizedExternalId = transactionMapper.normalizeExternalId(externalId);
        if (normalizedExternalId != null && transactionRepository.existsByTenantIdAndExternalTxId(tenantId, normalizedExternalId)) {
            throw new TransactionConflictException("Transaction with external id '" + normalizedExternalId + "' already exists for tenant " + tenantId);
        }
    }

    private void validateExternalIdUniquenessForUpdate(String externalId, Long tenantId, Long transactionId) {
        String normalizedExternalId = transactionMapper.normalizeExternalId(externalId);
        if (normalizedExternalId != null
                && transactionRepository.existsByTenantIdAndExternalTxIdAndIdNot(tenantId, normalizedExternalId, transactionId)) {
            throw new TransactionConflictException("Transaction with external id '" + normalizedExternalId + "' already exists for tenant " + tenantId);
        }
    }

    private String normalizeCurrency(String currency) {
        String normalizedCurrency = currency == null ? null : currency.trim().toUpperCase();
        if (normalizedCurrency == null || !normalizedCurrency.matches("^[A-Z]{3}$")) {
            throw new TransactionValidationException("Currency must be a 3-letter uppercase ISO code");
        }
        return normalizedCurrency;
    }

    private void validateTenantAccess(Long resourceId, Long tenantId, String resourceErrorMessage) {
        if (tenantId == null) {
            throw new TransactionValidationException("Tenant id is required");
        }

        if (resourceId == null) {
            throw new TransactionValidationException(resourceErrorMessage);
        }
    }

    private double resolveCurrentBalance(Account account) {
        if (account.getLastBalanceReal() != null) {
            return account.getLastBalanceReal().doubleValue();
        }

        if (account.getLastBalanceAvailable() != null) {
            return account.getLastBalanceAvailable().doubleValue();
        }

        return 0.0;
    }

    private void applySingleAccountBalance(Transaction transaction, Long tenantId) {
        BalanceDeltas deltas = computeBalanceDeltas(
            transaction.getSourceAccount().getId(),
            null,
            transaction.getAmount(),
            transaction.getStatus(),
            false,
            false);
        adjustAccountsForTransaction(deltas.sourceId, null, tenantId, deltas.sourceDelta, null, deltas.updateBoth);
    }

    private void revertSingleAccountBalance(Long sourceAccountId, java.math.BigDecimal amount,
            TransactionStatus status, Long tenantId) {
        BalanceDeltas deltas = computeBalanceDeltas(sourceAccountId, null, amount, status, true, true);
        adjustAccountsForTransaction(deltas.sourceId, null, tenantId, deltas.sourceDelta, null, deltas.updateBoth);
    }

    private void applyBalanceForCreate(Transaction transaction, Long tenantId) {
        BalanceDeltas deltas = computeBalanceDeltas(
            transaction.getSourceAccount().getId(),
            transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getId() : null,
            transaction.getAmount(),
            transaction.getStatus(),
            false,
            false);

        adjustAccountsForTransaction(
            deltas.sourceId,
            deltas.destId,
            tenantId,
            deltas.sourceDelta,
            deltas.destDelta,
            deltas.updateBoth);
    }

    private void revertBalanceForUpdate(Long oldSourceAccountId, Long oldDestinationAccountId,
            java.math.BigDecimal oldAmount, TransactionStatus oldStatus, Long tenantId) {
        BalanceDeltas deltas = computeBalanceDeltas(
            oldSourceAccountId,
            oldDestinationAccountId,
            oldAmount,
            oldStatus,
            true,
            false);

        adjustAccountsForTransaction(
            deltas.sourceId,
            deltas.destId,
            tenantId,
            deltas.sourceDelta,
            deltas.destDelta,
            deltas.updateBoth);
    }

    private void applyBalanceForUpdate(Transaction transaction, Long tenantId) {
        // Same logic as applyBalanceForCreate but using the saved transaction status
        applyBalanceForCreate(transaction, tenantId);
    }

    private void revertBalanceForDelete(Long sourceAccountId, Long destinationAccountId,
            java.math.BigDecimal amount, TransactionStatus status, Long tenantId) {
        // Per requirement: deletes always update both balances (real and available)
        BalanceDeltas deltas = computeBalanceDeltas(
                sourceAccountId,
                destinationAccountId,
                amount,
                status,
                true,
                true);

        adjustAccountsForTransaction(
                deltas.sourceId,
                deltas.destId,
                tenantId,
                deltas.sourceDelta,
                deltas.destDelta,
                deltas.updateBoth);
    }
    private void adjustAccountsForTransaction(Long sourceAccountId, Long destinationAccountId, Long tenantId,
            java.math.BigDecimal sourceDelta, java.math.BigDecimal destinationDelta, boolean updateBoth) {
        if (destinationAccountId == null) {
            accountsService.updateAccountBalances(sourceAccountId, tenantId, sourceDelta, updateBoth);
        } else {
            accountsService.updateAccountBalances(sourceAccountId, tenantId, sourceDelta, updateBoth);
            accountsService.updateAccountBalances(destinationAccountId, tenantId, destinationDelta, updateBoth);
        }
    }

    private static class BalanceDeltas {
        Long sourceId;
        Long destId;
        java.math.BigDecimal sourceDelta;
        java.math.BigDecimal destDelta;
        boolean updateBoth;

        BalanceDeltas(Long sourceId, Long destId, java.math.BigDecimal sourceDelta, java.math.BigDecimal destDelta, boolean updateBoth) {
            this.sourceId = sourceId;
            this.destId = destId;
            this.sourceDelta = sourceDelta;
            this.destDelta = destDelta;
            this.updateBoth = updateBoth;
        }
    }

    private BalanceDeltas computeBalanceDeltas(Long sourceId, Long destId, java.math.BigDecimal amount, TransactionStatus status, boolean invert, boolean forceBoth) {
        boolean pending = status != null && "PENDING".equalsIgnoreCase(status.getCode());

        java.math.BigDecimal sourceDelta;
        java.math.BigDecimal destDelta = null;
        boolean updateBoth;

        if (destId == null) {
            if (pending) {
                sourceDelta = amount.negate();
                updateBoth = false;
            } else {
                sourceDelta = amount;
                updateBoth = true;
            }
        } else {
            // Transfer: amount represents the outflow from source (negative = deduct source, credit dest)
            if (pending) {
                sourceDelta = amount;
                destDelta = amount.negate();
                updateBoth = false;
            } else {
                sourceDelta = amount;
                destDelta = amount.negate();
                updateBoth = true;
            }
        }

        if (invert) {
            sourceDelta = sourceDelta.negate();
            if (destDelta != null) {
                destDelta = destDelta.negate();
            }
        }

        // forceBoth overrides updateBoth
        if (forceBoth) {
            updateBoth = true;
        }

        return new BalanceDeltas(sourceId, destId, sourceDelta, destDelta, updateBoth);
    }
}
