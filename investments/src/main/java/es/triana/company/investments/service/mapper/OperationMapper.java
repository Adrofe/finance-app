package es.triana.company.investments.service.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import java.util.List;

import es.triana.company.investments.model.api.CreateOperationRequest;
import es.triana.company.investments.model.db.InvestmentOperation;
import es.triana.company.investments.model.api.OperationDTO;
import es.triana.company.investments.model.db.OperationFifoLot;

/**
 * Mapper responsible for building InvestmentOperation entities from API requests
 * and computed fields. Keeps construction logic out of the service.
 */
@Component
public class OperationMapper {

    public InvestmentOperation toEntity(CreateOperationRequest req,
            BigDecimal totalAmount,
            BigDecimal eurExchangeRate,
            BigDecimal totalAmountEur,
            LocalDateTime now) {

        BigDecimal fees = req.getFees() != null ? req.getFees() : BigDecimal.ZERO;

        return InvestmentOperation.builder()
                .investmentId(req.getInvestmentId())
                .tenantId(req.getTenantId())
                .type(req.getType())
                .operationDate(req.getOperationDate())
                .quantity(req.getQuantity())
                .unitPrice(req.getUnitPrice())
                .fees(fees)
                .totalAmount(totalAmount)
                .currency(req.getCurrency())
                .eurExchangeRate(eurExchangeRate)
                .totalAmountEur(totalAmountEur)
                .notes(req.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public OperationDTO toDto(InvestmentOperation op, List<OperationFifoLot> lots) {
        List<OperationDTO.FifoLotDTO> lotDTOs = lots.stream()
            .map(l -> new OperationDTO.FifoLotDTO(
                l.getBuyOperationId(),
                l.getQuantity(),
                l.getBuyUnitPriceEur(),
                l.getSellUnitPriceEur(),
                l.getGainLossEur()))
            .toList();

        return new OperationDTO(
            op.getId(),
            op.getInvestmentId(),
            op.getTenantId(),
            op.getType(),
            op.getOperationDate(),
            op.getQuantity(),
            op.getUnitPrice(),
            op.getFees(),
            op.getTotalAmount(),
            op.getCurrency(),
            op.getEurExchangeRate(),
            op.getTotalAmountEur(),
            op.getNotes(),
            op.getCreatedAt(),
            lotDTOs);
        }
}
