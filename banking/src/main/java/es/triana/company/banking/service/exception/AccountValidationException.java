package es.triana.company.banking.service.exception;

public class AccountValidationException extends RuntimeException {

    public AccountValidationException(String message) {
        super(message);
    }
}