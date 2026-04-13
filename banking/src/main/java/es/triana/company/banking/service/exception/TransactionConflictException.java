package es.triana.company.banking.service.exception;

public class TransactionConflictException extends RuntimeException {

    public TransactionConflictException(String message) {
        super(message);
    }
}