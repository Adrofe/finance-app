package es.triana.company.investments.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.triana.company.investments.model.api.OperationDTO;
import es.triana.company.investments.model.db.InvestmentOperation;
import es.triana.company.investments.model.db.OperationFifoLot;
import es.triana.company.investments.model.db.OperationType;
import es.triana.company.investments.service.mapper.OperationMapper;

class OperationMapperTest {

    @Test
    void toDto_mapsFields() {
        InvestmentOperation op = InvestmentOperation.builder()
                .id(123L)
                .investmentId(10L)
                .tenantId(1L)
                .type(OperationType.BUY)
                .operationDate(LocalDate.of(2024, 1, 2))
                .quantity(new BigDecimal("1.5"))
                .unitPrice(new BigDecimal("100"))
                .fees(new BigDecimal("1"))
                .totalAmount(new BigDecimal("151"))
                .currency("USD")
                .eurExchangeRate(new BigDecimal("1.1"))
                .totalAmountEur(new BigDecimal("137.2727"))
                .notes("note")
                .createdAt(LocalDateTime.now())
                .build();

        OperationFifoLot lot = OperationFifoLot.builder()
                .buyOperationId(11L)
                .quantity(new BigDecimal("0.5"))
                .buyUnitPriceEur(new BigDecimal("90"))
                .sellUnitPriceEur(new BigDecimal("100"))
                .gainLossEur(new BigDecimal("5"))
                .build();

        OperationMapper mapper = new OperationMapper();
        OperationDTO dto = mapper.toDto(op, List.of(lot));

        assertEquals(op.getId(), dto.id());
        assertEquals(op.getInvestmentId(), dto.investmentId());
        assertEquals(op.getTenantId(), dto.tenantId());
        assertEquals(op.getType(), dto.type());
        assertEquals(op.getOperationDate(), dto.operationDate());
        assertEquals(op.getQuantity(), dto.quantity());
        assertEquals(op.getUnitPrice(), dto.unitPrice());
        assertEquals(op.getFees(), dto.fees());
        assertEquals(op.getTotalAmount(), dto.totalAmount());
        assertEquals(op.getCurrency(), dto.currency());
        assertEquals(op.getEurExchangeRate(), dto.eurExchangeRate());
        assertEquals(op.getTotalAmountEur(), dto.totalAmountEur());
        assertEquals(op.getNotes(), dto.notes());
        assertEquals(1, dto.fifoLots().size());
        assertEquals(lot.getBuyOperationId(), dto.fifoLots().get(0).buyOperationId());
    }
}
