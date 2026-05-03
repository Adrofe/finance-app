package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOperationRequest {

    @NotNull(message = "investmentId is required")
    private Long investmentId;

    /** Tenant ID will be extracted from Keycloak token if not provided */
    private Long tenantId;

    /** BUY or SELL */
    @NotNull(message = "type is required")
    private es.triana.company.investments.model.db.OperationType type;

    @NotNull(message = "operationDate is required")
    private LocalDate operationDate;

    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.0000000001", message = "quantity must be greater than 0")
    private BigDecimal quantity;

    @NotNull(message = "unitPrice is required")
    @DecimalMin(value = "0.0000000001", message = "unitPrice must be greater than 0")
    private BigDecimal unitPrice;

    /** Commissions/fees in operation currency. Must be >= 0. Defaults to 0 if null. */
    @DecimalMin(value = "0", message = "fees must not be negative")
    private BigDecimal fees;

    /** 3-letter ISO currency code of the operation */
    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be exactly 3 letters")
    private String currency;

    @Size(max = 500)
    private String notes;

    public CreateOperationRequest(CreateOperationRequest request, Long tenantId) {
        this.investmentId = request.getInvestmentId();
        this.tenantId = tenantId;
        this.type = request.getType();
        this.operationDate = request.getOperationDate();
        this.quantity = request.getQuantity();
        this.unitPrice = request.getUnitPrice();
        this.fees = request.getFees();
        this.currency = request.getCurrency();
        this.notes = request.getNotes();
    }
}
