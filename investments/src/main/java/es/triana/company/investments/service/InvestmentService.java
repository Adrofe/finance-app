package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import es.triana.company.investments.model.api.InvestmentDTO;
import es.triana.company.investments.model.api.InvestmentSummaryDTO;
import es.triana.company.investments.model.api.InvestmentTypeSummaryDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentPlatform;
import es.triana.company.investments.model.db.InvestmentTypeCatalog;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentPlatformRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;
import es.triana.company.investments.service.exception.InvestmentNotFoundException;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final InvestmentTypeCatalogRepository investmentTypeCatalogRepository;
    private final InvestmentInstrumentRepository investmentInstrumentRepository;
    private final InvestmentPlatformRepository investmentPlatformRepository;

    public InvestmentService(
            InvestmentRepository investmentRepository,
            InvestmentTypeCatalogRepository investmentTypeCatalogRepository,
            InvestmentInstrumentRepository investmentInstrumentRepository,
            InvestmentPlatformRepository investmentPlatformRepository) {
        this.investmentRepository = investmentRepository;
        this.investmentTypeCatalogRepository = investmentTypeCatalogRepository;
        this.investmentInstrumentRepository = investmentInstrumentRepository;
        this.investmentPlatformRepository = investmentPlatformRepository;
    }

    public List<InvestmentDTO> getAllByTenant(Long tenantId) {
        validateTenant(tenantId);
        List<Investment> items = investmentRepository.findByTenantIdOrderByUpdatedAtDescIdDesc(tenantId);
        Map<Long, InvestmentTypeCatalog> typeMap = loadTypeMap(items);
        Map<Long, InvestmentInstrument> instrumentMap = loadInstrumentMap(items);
        Map<Long, InvestmentPlatform> platformMap = loadPlatformMap(items);
        return items.stream()
            .map(item -> toDto(item, typeMap, instrumentMap, platformMap))
                .toList();
    }

    public InvestmentDTO getById(Long id, Long tenantId) {
        validateTenant(tenantId);
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new InvestmentNotFoundException(id));
        Map<Long, InvestmentTypeCatalog> typeMap = loadTypeMap(List.of(investment));
        Map<Long, InvestmentInstrument> instrumentMap = loadInstrumentMap(List.of(investment));
        Map<Long, InvestmentPlatform> platformMap = loadPlatformMap(List.of(investment));
        return toDto(investment, typeMap, instrumentMap, platformMap);
    }

    public InvestmentDTO create(InvestmentDTO dto) {
        validateTenant(dto.getTenantId());
        validateAmounts(dto);

        InvestmentTypeCatalog type = resolveType(dto.getTypeId());
        InvestmentInstrument instrument = resolveInstrument(dto.getInstrumentId());
        validateInstrumentBelongsToType(instrument, type);
        InvestmentPlatform platform = resolvePlatform(dto.getPlatformId());

        Investment investment = Investment.builder()
                .tenantId(dto.getTenantId())
                .typeId(type.getId())
                .name(trimToNull(dto.getName()))
            .instrumentId(instrument.getId())
                .platformId(platform != null ? platform.getId() : null)
                .currency(normalizeCurrency(dto.getCurrency()))
                .investedAmount(dto.getInvestedAmount())
                .currentValueManual(dto.getCurrentValueManual())
                .currentValueCalculated(calculateCurrentValue(instrument, dto.getQuantity()))
                .quantity(dto.getQuantity())
                .openedAt(dto.getOpenedAt())
                .notes(trimToNull(dto.getNotes()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            Investment saved = investmentRepository.save(investment);
            return toDto(
                saved,
                Map.of(type.getId(), type),
                Map.of(instrument.getId(), instrument),
                platform != null ? Map.of(platform.getId(), platform) : Map.of());
    }

    public InvestmentDTO update(Long id, InvestmentDTO dto) {
        validateTenant(dto.getTenantId());
        validateAmounts(dto);

        InvestmentTypeCatalog type = resolveType(dto.getTypeId());
        InvestmentInstrument instrument = resolveInstrument(dto.getInstrumentId());
        validateInstrumentBelongsToType(instrument, type);
        InvestmentPlatform platform = resolvePlatform(dto.getPlatformId());

        Investment investment = investmentRepository.findByIdAndTenantId(id, dto.getTenantId())
                .orElseThrow(() -> new InvestmentNotFoundException(id));

        investment.setTypeId(type.getId());
        investment.setName(trimToNull(dto.getName()));
        investment.setInstrumentId(instrument.getId());
        investment.setPlatformId(platform != null ? platform.getId() : null);
        investment.setCurrency(normalizeCurrency(dto.getCurrency()));
        investment.setInvestedAmount(dto.getInvestedAmount());
        investment.setCurrentValueManual(dto.getCurrentValueManual());
        investment.setCurrentValueCalculated(calculateCurrentValue(instrument, dto.getQuantity()));
        investment.setQuantity(dto.getQuantity());
        investment.setOpenedAt(dto.getOpenedAt());
        investment.setNotes(trimToNull(dto.getNotes()));
        investment.setUpdatedAt(LocalDateTime.now());

        Investment saved = investmentRepository.save(investment);
        return toDto(
            saved,
            Map.of(type.getId(), type),
            Map.of(instrument.getId(), instrument),
            platform != null ? Map.of(platform.getId(), platform) : Map.of());
    }

    public void delete(Long id, Long tenantId) {
        validateTenant(tenantId);
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new InvestmentNotFoundException(id));
        investmentRepository.delete(investment);
    }

    public InvestmentSummaryDTO getSummary(Long tenantId) {
        validateTenant(tenantId);
        List<Investment> items = investmentRepository.findByTenantIdOrderByUpdatedAtDescIdDesc(tenantId);
        Map<Long, InvestmentTypeCatalog> typeMap = loadTypeMap(items);

        BigDecimal totalInvested = items.stream()
                .map(Investment::getInvestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCurrent = items.stream()
                .map(this::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pnl = totalCurrent.subtract(totalInvested);
        BigDecimal pnlPct = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            pnlPct = pnl
                    .divide(totalInvested, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

                Map<Long, List<Investment>> groupedByType = items.stream()
                    .collect(Collectors.groupingBy(Investment::getTypeId));

                List<InvestmentTypeSummaryDTO> byType = groupedByType.entrySet().stream()
                    .map(entry -> {
                        List<Investment> byTypeItems = entry.getValue();
                        BigDecimal invested = byTypeItems.stream()
                            .map(Investment::getInvestedAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        BigDecimal current = byTypeItems.stream()
                            .map(this::getCurrentValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                        InvestmentTypeCatalog type = typeMap.get(entry.getKey());
                        return InvestmentTypeSummaryDTO.builder()
                            .typeId(entry.getKey())
                            .typeCode(type != null ? type.getCode() : null)
                            .typeName(type != null ? type.getName() : null)
                            .count(byTypeItems.size())
                            .investedAmount(invested)
                            .currentValue(current)
                            .pnl(current.subtract(invested))
                            .build();
                    })
                .toList();

        return InvestmentSummaryDTO.builder()
                .totalInvested(totalInvested)
                .totalCurrentValue(totalCurrent)
                .totalPnl(pnl)
                .totalPnlPct(pnlPct)
                .positions(items.size())
                .byType(byType)
                .build();
    }

    private void validateTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new InvestmentValidationException("tenantId is required and must be > 0");
        }
    }

    private void validateAmounts(InvestmentDTO dto) {
        if (dto.getTypeId() == null || dto.getTypeId() <= 0) {
            throw new InvestmentValidationException("typeId is required and must be > 0");
        }
        if (dto.getInstrumentId() == null || dto.getInstrumentId() <= 0) {
            throw new InvestmentValidationException("instrumentId is required and must be > 0");
        }
        if (dto.getInvestedAmount() == null || dto.getInvestedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvestmentValidationException("investedAmount must be >= 0");
        }
        if (dto.getCurrentValueManual() != null && dto.getCurrentValueManual().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvestmentValidationException("currentValueManual must be >= 0");
        }
        if (dto.getQuantity() != null && dto.getQuantity().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvestmentValidationException("quantity must be >= 0");
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) {
            return "EUR";
        }
        String c = currency.trim().toUpperCase();
        return c.isEmpty() ? "EUR" : c;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private InvestmentTypeCatalog resolveType(Long typeId) {
        if (typeId == null || typeId <= 0) {
            throw new InvestmentValidationException("typeId is required and must be > 0");
        }
        return investmentTypeCatalogRepository.findById(typeId)
                .orElseThrow(() -> new InvestmentValidationException("Unknown typeId: " + typeId));
    }

    private InvestmentInstrument resolveInstrument(Long instrumentId) {
        if (instrumentId == null || instrumentId <= 0) {
            throw new InvestmentValidationException("instrumentId is required and must be > 0");
        }
        return investmentInstrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new InvestmentValidationException("Unknown instrumentId: " + instrumentId));
    }

    private void validateInstrumentBelongsToType(InvestmentInstrument instrument, InvestmentTypeCatalog type) {
        if (!type.getId().equals(instrument.getTypeId())) {
            throw new InvestmentValidationException(
                    "instrumentId " + instrument.getId() + " does not belong to typeId " + type.getId());
        }
    }

    private InvestmentPlatform resolvePlatform(Long platformId) {
        if (platformId == null) {
            return null;
        }
        if (platformId <= 0) {
            throw new InvestmentValidationException("platformId must be > 0");
        }
        return investmentPlatformRepository.findById(platformId)
                .orElseThrow(() -> new InvestmentValidationException("Unknown platformId: " + platformId));
    }

    private Map<Long, InvestmentTypeCatalog> loadTypeMap(List<Investment> items) {
        Set<Long> ids = items.stream()
                .map(Investment::getTypeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, InvestmentTypeCatalog> result = new HashMap<>();
        investmentTypeCatalogRepository.findAllById(ids).forEach(type -> result.put(type.getId(), type));
        return result;
    }

    private Map<Long, InvestmentInstrument> loadInstrumentMap(List<Investment> items) {
        Set<Long> ids = items.stream()
                .map(Investment::getInstrumentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, InvestmentInstrument> result = new HashMap<>();
        investmentInstrumentRepository.findAllById(ids).forEach(instrument -> result.put(instrument.getId(), instrument));
        return result;
    }

    private Map<Long, InvestmentPlatform> loadPlatformMap(List<Investment> items) {
        Set<Long> ids = items.stream()
                .map(Investment::getPlatformId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, InvestmentPlatform> result = new HashMap<>();
        investmentPlatformRepository.findAllById(ids).forEach(platform -> result.put(platform.getId(), platform));
        return result;
    }

    private InvestmentDTO toDto(
            Investment entity,
            Map<Long, InvestmentTypeCatalog> typeMap,
            Map<Long, InvestmentInstrument> instrumentMap,
            Map<Long, InvestmentPlatform> platformMap) {
        InvestmentTypeCatalog type = typeMap.get(entity.getTypeId());
        InvestmentInstrument instrument = instrumentMap.get(entity.getInstrumentId());
        InvestmentPlatform platform = platformMap.get(entity.getPlatformId());
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

    /**
     * Calcula el valor actual: 
     * - Si cantidad y último precio existen, cantidad × lastPrice
     * - Sino, null (debe usarse manual + histórico)
     */
    private BigDecimal calculateCurrentValue(InvestmentInstrument instrument, BigDecimal quantity) {
        if (quantity == null || instrument.getLastPrice() == null) {
            return null;
        }
        return quantity.multiply(instrument.getLastPrice());
    }

    /**
     * Obtiene el valor actual de una inversión:
     * - Usa currentValueCalculated si existe (auto-calculado desde price)
     * - Sino, usa currentValueManual (ingresado por usuario)
     * - Si ambos son null, retorna ZERO
     */
    private BigDecimal getCurrentValue(Investment investment) {
        if (investment.getCurrentValueCalculated() != null) {
            return investment.getCurrentValueCalculated();
        }
        if (investment.getCurrentValueManual() != null) {
            return investment.getCurrentValueManual();
        }
        return BigDecimal.ZERO;
    }
}
