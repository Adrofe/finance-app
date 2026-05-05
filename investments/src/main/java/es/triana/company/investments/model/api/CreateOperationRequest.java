package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
public class CreateOperationRequest {

    /**
     * Optional. If provided, operation is linked directly to this position.
     * If absent, backend will resolve/create a position from instrumentId/platformId.
     */
    private Long investmentId;

    /** Optional. Used when investmentId is not provided. */
    private Long instrumentId;

    /** Optional. Used with instrumentId to resolve/create the target position. */
    private Long platformId;

    /** Optional display name for auto-created positions. */
    @Size(max = 150)
    private String positionName;

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

    public CreateOperationRequest(
            Long investmentId,
            Long tenantId,
            es.triana.company.investments.model.db.OperationType type,
            LocalDate operationDate,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fees,
            String currency,
            String notes) {
        this.investmentId = investmentId;
        this.tenantId = tenantId;
        this.type = type;
        this.operationDate = operationDate;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fees = fees;
        this.currency = currency;
        this.notes = notes;
    }

    public CreateOperationRequest(
            Long investmentId,
            Long instrumentId,
            Long platformId,
            String positionName,
            Long tenantId,
            es.triana.company.investments.model.db.OperationType type,
            LocalDate operationDate,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fees,
            String currency,
            String notes) {
        this.investmentId = investmentId;
        this.instrumentId = instrumentId;
        this.platformId = platformId;
        this.positionName = positionName;
        this.tenantId = tenantId;
        this.type = type;
        this.operationDate = operationDate;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fees = fees;
        this.currency = currency;
        this.notes = notes;
    }

    public CreateOperationRequest(CreateOperationRequest request, Long tenantId) {
        this.investmentId = request.getInvestmentId();
        this.instrumentId = request.getInstrumentId();
        this.platformId = request.getPlatformId();
        this.positionName = request.getPositionName();
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
