package es.triana.company.investments.service.exception;

/**
 * Thrown when an operation quantity is invalid (negative, zero, or exceeds limits).
 * This is a more granular validation error than the generic InvestmentValidationException.
 */
public class InvalidQuantityException extends InvestmentValidationException {

    public InvalidQuantityException(String message) {
        super(message);
    }
}
