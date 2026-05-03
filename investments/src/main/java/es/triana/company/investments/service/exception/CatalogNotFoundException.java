package es.triana.company.investments.service.exception;

public class CatalogNotFoundException extends RuntimeException {

    public CatalogNotFoundException(String message) {
        super(message);
    }
}
