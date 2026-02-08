package es.triana.company.banking.service.exception;

public class AccountNotFoundException extends RuntimeException {
    private Long accountId;

    public AccountNotFoundException(String message) {
        super(message);
    }

    public AccountNotFoundException(Long accountId) {
        super("Account with ID " + accountId + " not found");
        this.accountId = accountId;
    }

    public Long getAccountId() {
        return accountId;
    }
}