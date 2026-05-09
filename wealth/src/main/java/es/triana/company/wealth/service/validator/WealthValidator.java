package es.triana.company.wealth.service.validator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import es.triana.company.wealth.model.api.WealthSnapshotCreateRequestDTO;

/**
 * Single Responsibility: Validate wealth operations and business rules.
 */
@Component
public class WealthValidator {

    public void validateTenantId(Long tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant id is required");
        }
    }

    public void validateSnapshotRequest(WealthSnapshotCreateRequestDTO request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one wealth item is required");
        }
    }
}
