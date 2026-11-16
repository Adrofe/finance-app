package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.api.TransactionFilterRequest;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.model.db.TransactionStatus;
import es.triana.company.banking.model.db.TransactionType;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.repository.TransactionSpecification;
import es.triana.company.banking.repository.TransactionStatusRepository;
import es.triana.company.banking.repository.TransactionTypeRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:accountactivity;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS banking",
    "spring.jpa.properties.hibernate.default_schema=banking",
    "server.port=0"
})
@ActiveProfiles("test")
@Transactional
class AccountActivityIntegrationTest {

    @Autowired private AccountsService accountsService;
    @Autowired private TransactionService transactionService;
    @Autowired private AccountsRepository accountsRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionStatusRepository statuses;
    @Autowired private TransactionTypeRepository types;
    @Autowired private EntityManager entityManager;

    @Test
    void deactivationKeepsTransactionHistoryAvailableAfterReload() {
        Account account = accountsRepository.save(Account.builder()
            .tenantId(1L).name("Closed account").currency("EUR").isActive(true).build());
        transactionRepository.save(Transaction.builder()
            .tenantId(1L).sourceAccount(account).bookingDate(LocalDate.of(2025, 1, 15))
            .amount(new BigDecimal("-20.00")).currency("EUR").descriptionRaw("Historical transaction").build());

        accountsService.updateAccount(AccountDTO.builder()
            .id(account.getId()).name(account.getName()).currency("EUR").isActive(false).build(), 1L);
        entityManager.flush();
        entityManager.clear();

        assertEquals(Boolean.FALSE, accountsService.getAccountById(account.getId(), 1L).getIsActive());
        var history = transactionService.getTransactionsByAccount(account.getId(), 1L);
        assertEquals(1, history.size());
        assertEquals("Historical transaction", history.get(0).getDescription());
    }

    @Test
    void multipleSelectedFiltersApplyBeforePaginationAndOnlyMatchSourceAccounts() {
        Account first = accountsRepository.save(Account.builder().tenantId(1L).name("First").currency("EUR").isActive(true).build());
        Account second = accountsRepository.save(Account.builder().tenantId(1L).name("Second").currency("EUR").isActive(true).build());
        Account other = accountsRepository.save(Account.builder().tenantId(1L).name("Other").currency("EUR").isActive(true).build());
        TransactionStatus booked = statuses.save(TransactionStatus.builder().id(101L).code("TEST_BOOKED").build());
        TransactionStatus pending = statuses.save(TransactionStatus.builder().id(102L).code("TEST_PENDING").build());
        TransactionStatus excludedStatus = statuses.save(TransactionStatus.builder().id(103L).code("TEST_REJECTED").build());
        TransactionType expense = types.save(TransactionType.builder().id(101L).name("TEST_EXPENSE").build());
        TransactionType transfer = types.save(TransactionType.builder().id(102L).name("TEST_TRANSFER").build());
        TransactionType excludedType = types.save(TransactionType.builder().id(103L).name("TEST_OTHER").build());
        transactionRepository.save(transaction(1L, first, other, booked, expense, "First match"));
        transactionRepository.save(transaction(1L, second, other, pending, transfer, "Second match"));
        transactionRepository.save(transaction(1L, other, first, booked, expense, "Destination only"));
        transactionRepository.save(transaction(1L, first, other, excludedStatus, expense, "Wrong status"));
        transactionRepository.save(transaction(1L, first, other, booked, excludedType, "Wrong type"));
        transactionRepository.save(transaction(2L, first, other, booked, expense, "Other tenant"));
        entityManager.flush();
        entityManager.clear();

        TransactionFilterRequest filter = new TransactionFilterRequest();
        filter.setSourceAccountIds(List.of(first.getId(), second.getId()));
        filter.setStatusIds(List.of(101L, 102L));
        filter.setTypeIds(List.of(101L, 102L));
        var page = transactionRepository.findAll(new TransactionSpecification(filter, 1L), PageRequest.of(0, 1));
        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
        assertEquals(1, page.getContent().size());

        TransactionFilterRequest legacy = new TransactionFilterRequest();
        legacy.setAccountId(first.getId());
        legacy.setStatusId(101L);
        legacy.setTypeId(101L);
        assertEquals(2, transactionRepository.findAll(new TransactionSpecification(legacy, 1L)).size());
    }

    private Transaction transaction(Long tenantId, Account source, Account destination, TransactionStatus status, TransactionType type, String description) {
        return Transaction.builder().tenantId(tenantId).sourceAccount(source).destinationAccount(destination)
            .bookingDate(LocalDate.of(2025, 1, 15)).amount(new BigDecimal("-20.00")).currency("EUR")
            .descriptionRaw(description).status(status).transactionType(type).build();
    }
}