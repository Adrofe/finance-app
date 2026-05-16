package es.triana.company.banking.service.exception;

public class TransactionTaxValidationException extends RuntimeException {

    public TransactionTaxValidationException(String message) {
        super(message);
    }
}
