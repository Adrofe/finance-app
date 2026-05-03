package es.triana.company.investments.service.mapper;

import org.springframework.stereotype.Component;

import es.triana.company.investments.model.api.InvestmentDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentPlatform;
import es.triana.company.investments.model.db.InvestmentTypeCatalog;

/**
 * Mapper responsible for converting `Investment` entities to `InvestmentDTO`.
 */
@Component
public class InvestmentMapper {

    public InvestmentDTO toDto(Investment entity) {
        if (entity == null) return null;

        InvestmentTypeCatalog type = entity.getType();
        InvestmentInstrument instrument = entity.getInstrument();
        InvestmentPlatform platform = entity.getPlatform();

        return InvestmentDTO.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .typeId(entity.getTypeId())
                .typeCode(type != null ? type.getCode() : null)
                .typeName(type != null ? type.getName() : null)
                .name(entity.getName())
                .instrumentId(entity.getInstrumentId())
                .instrumentSymbol(instrument != null ? instrument.getSymbol() : null)
                .instrumentName(instrument != null ? instrument.getName() : null)
                .platformId(entity.getPlatformId())
                .platformCode(platform != null ? platform.getCode() : null)
                .platformName(platform != null ? platform.getName() : null)
                .currency(entity.getCurrency())
                .investedAmount(entity.getInvestedAmount())
                .currentValueManual(entity.getCurrentValueManual())
                .currentValueCalculated(entity.getCurrentValueCalculated())
                .quantity(entity.getQuantity())
                .openedAt(entity.getOpenedAt())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
