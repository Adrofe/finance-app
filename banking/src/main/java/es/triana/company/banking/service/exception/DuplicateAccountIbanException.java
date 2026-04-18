package es.triana.company.banking.service.exception;

public class DuplicateAccountIbanException extends RuntimeException {

    public DuplicateAccountIbanException(String iban) {
        super("Account with IBAN '" + iban + "' already exists for your user.");
    }
}