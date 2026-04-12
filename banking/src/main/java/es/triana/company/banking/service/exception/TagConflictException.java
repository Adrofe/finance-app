package es.triana.company.banking.service.exception;

public class TagConflictException extends RuntimeException {

    public TagConflictException(String message) {
        super(message);
    }
}