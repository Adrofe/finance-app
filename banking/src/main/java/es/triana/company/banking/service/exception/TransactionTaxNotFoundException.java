package es.triana.company.banking.service.exception;

public class TransactionTaxNotFoundException extends RuntimeException {

    public TransactionTaxNotFoundException(String message) {
        super(message);
    }
}
