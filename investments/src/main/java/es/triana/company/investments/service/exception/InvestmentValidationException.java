package es.triana.company.investments.service.exception;

public class InvestmentValidationException extends RuntimeException {

    public InvestmentValidationException(String message) {
        super(message);
    }
}
