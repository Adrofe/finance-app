package es.triana.company.banking.service.exception;

public class AccountTypeNotFoundException extends RuntimeException {

    public AccountTypeNotFoundException(Long accountTypeId) {
        super("Account type not found with id: " + accountTypeId);
    }
}