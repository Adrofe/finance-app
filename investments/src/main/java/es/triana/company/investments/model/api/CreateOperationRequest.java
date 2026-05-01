package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOperationRequest(

        @NotNull(message = "investmentId is required")
        Long investmentId,

        @NotNull(message = "tenantId is required")
        Long tenantId,

        /** BUY or SELL */
        @NotBlank(message = "type is required")
        @Pattern(regexp = "BUY|SELL", message = "type must be BUY or SELL")
        String type,

        @NotNull(message = "operationDate is required")
        LocalDate operationDate,

        @NotNull(message = "quantity is required")
        @DecimalMin(value = "0.0000000001", message = "quantity must be positive")
        BigDecimal quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.0000000001", message = "unitPrice must be positive")
        BigDecimal unitPrice,

        /** Commissions/fees in operation currency. Defaults to 0 if null. */
        BigDecimal fees,

        /** 3-letter ISO currency code of the operation */
        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be exactly 3 letters")
        String currency,

        @Size(max = 500)
        String notes
) {}
