package es.triana.company.banking.service.exception;

public class TenantMismatchException extends RuntimeException {

    public TenantMismatchException(Long accountId) {
        super("Tenant id cannot be changed for account with id: " + accountId);
    }
}