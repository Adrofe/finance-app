package es.triana.company.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.banking.model.api.AccountDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.repository.AccountsRepository;
import es.triana.company.banking.repository.TransactionRepository;
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
}