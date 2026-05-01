package es.triana.company.investments.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record OperationDTO(
        Long id,
        Long investmentId,
        Long tenantId,
        String type,
        LocalDate operationDate,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal fees,
        BigDecimal totalAmount,
        String currency,
        BigDecimal eurExchangeRate,
        BigDecimal totalAmountEur,
        String notes,
        LocalDateTime createdAt,
        List<FifoLotDTO> fifoLots
) {
    public record FifoLotDTO(
            Long buyOperationId,
            BigDecimal quantity,
            BigDecimal buyUnitPriceEur,
            BigDecimal sellUnitPriceEur,
            BigDecimal gainLossEur
    ) {}
}
