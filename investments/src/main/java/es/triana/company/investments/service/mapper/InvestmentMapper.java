package es.triana.company.investments.service.mapper;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    public Investment toEntityForCreate(InvestmentDTO dto,
                                        InvestmentTypeCatalog type,
                                        InvestmentInstrument instrument,
                                        InvestmentPlatform platform) {
        if (dto == null) return null;

        BigDecimal currentValueCalculated = null;
        if (instrument != null && dto.getQuantity() != null && instrument.getLastPrice() != null) {
            currentValueCalculated = dto.getQuantity().multiply(instrument.getLastPrice());
        }

        return Investment.builder()
                .tenantId(dto.getTenantId())
                .typeId(type.getId())
                .name(trimToNull(dto.getName()))
                .instrumentId(instrument.getId())
                .platformId(platform != null ? platform.getId() : null)
                .currency(normalizeCurrency(dto.getCurrency()))
                .investedAmount(dto.getInvestedAmount())
                .currentValueManual(dto.getCurrentValueManual())
                .currentValueCalculated(currentValueCalculated)
                .quantity(dto.getQuantity())
                .openedAt(dto.getOpenedAt())
                .notes(trimToNull(dto.getNotes()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public Investment updateEntityFromDto(Investment entity,
                                          InvestmentDTO dto,
                                          InvestmentTypeCatalog type,
                                          InvestmentInstrument instrument,
                                          InvestmentPlatform platform) {
        if (entity == null || dto == null) return entity;

        BigDecimal currentValueCalculated = null;
        if (instrument != null && dto.getQuantity() != null && instrument.getLastPrice() != null) {
            currentValueCalculated = dto.getQuantity().multiply(instrument.getLastPrice());
        }

        entity.setTypeId(type.getId());
        entity.setName(trimToNull(dto.getName()));
        entity.setInstrumentId(instrument.getId());
        entity.setPlatformId(platform != null ? platform.getId() : null);
        entity.setCurrency(normalizeCurrency(dto.getCurrency()));
        entity.setInvestedAmount(dto.getInvestedAmount());
        entity.setCurrentValueManual(dto.getCurrentValueManual());
        entity.setCurrentValueCalculated(currentValueCalculated);
        entity.setQuantity(dto.getQuantity());
        entity.setOpenedAt(dto.getOpenedAt());
        entity.setNotes(trimToNull(dto.getNotes()));
        entity.setUpdatedAt(LocalDateTime.now());

        return entity;
    }

    // Helper methods local to mapper (kept private)
    private String normalizeCurrency(String currency) {
        if (currency == null) return "EUR";
        String c = currency.trim().toUpperCase();
        return c.isEmpty() ? "EUR" : c;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
