package es.triana.company.banking.service.exception;

public class InstitutionNotFoundException extends RuntimeException {

    public InstitutionNotFoundException(Long institutionId) {
        super("Institution not found with id: " + institutionId);
    }
}
