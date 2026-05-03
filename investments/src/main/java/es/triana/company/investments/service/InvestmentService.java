package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import es.triana.company.investments.service.mapper.InvestmentMapper;
import es.triana.company.investments.service.exception.InvestmentNotFoundException;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final InvestmentTypeCatalogRepository investmentTypeCatalogRepository;
    private final InvestmentInstrumentRepository investmentInstrumentRepository;
    private final InvestmentPlatformRepository investmentPlatformRepository;
    private final InvestmentMapper investmentMapper;

    public InvestmentService(InvestmentRepository investmentRepository, InvestmentTypeCatalogRepository investmentTypeCatalogRepository, InvestmentInstrumentRepository investmentInstrumentRepository, InvestmentPlatformRepository investmentPlatformRepository, InvestmentMapper investmentMapper) {
        this.investmentRepository = investmentRepository;
        this.investmentTypeCatalogRepository = investmentTypeCatalogRepository;
        this.investmentInstrumentRepository = investmentInstrumentRepository;
        this.investmentPlatformRepository = investmentPlatformRepository;
        this.investmentMapper = investmentMapper;
    }

    public List<InvestmentDTO> getAllByTenant(Long tenantId) {
        validateTenant(tenantId);
        List<Investment> items = investmentRepository.findByTenantIdOrderByUpdatedAtDescIdDesc(tenantId);
        return items.stream()
            .map(investmentMapper::toDto)
            .toList();
    }

    public InvestmentDTO getById(Long id, Long tenantId) {
        validateTenant(tenantId);
        Investment investment = investmentRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new InvestmentNotFoundException(id));
        return investmentMapper.toDto(investment);
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
            return investmentMapper.toDto(saved);
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
        return investmentMapper.toDto(saved);
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
                    InvestmentTypeCatalog type = byTypeItems.isEmpty() ? null : byTypeItems.get(0).getType();
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
