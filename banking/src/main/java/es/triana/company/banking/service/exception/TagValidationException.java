package es.triana.company.banking.service.exception;

public class TagValidationException extends RuntimeException {

    public TagValidationException(String message) {
        super(message);
    }
}