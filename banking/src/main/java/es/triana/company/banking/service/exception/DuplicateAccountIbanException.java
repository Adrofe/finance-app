package es.triana.company.banking.service.exception;

public class DuplicateAccountIbanException extends RuntimeException {

    public DuplicateAccountIbanException(String iban, Long tenantId) {
        super("Account with IBAN '" + iban + "' already exists for tenant " + tenantId);
    }
}