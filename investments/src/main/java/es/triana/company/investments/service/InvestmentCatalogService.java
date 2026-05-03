package es.triana.company.investments.service;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import es.triana.company.investments.model.api.InvestmentInstrumentDTO;
import es.triana.company.investments.model.api.InvestmentPlatformDTO;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentPlatform;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentPlatformRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;
import es.triana.company.investments.service.exception.CatalogNotFoundException;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class InvestmentCatalogService {

    private final InvestmentInstrumentRepository investmentInstrumentRepository;
    private final InvestmentPlatformRepository investmentPlatformRepository;
    private final InvestmentTypeCatalogRepository investmentTypeCatalogRepository;

    public InvestmentCatalogService(InvestmentInstrumentRepository investmentInstrumentRepository, InvestmentPlatformRepository investmentPlatformRepository, InvestmentTypeCatalogRepository investmentTypeCatalogRepository) {
        this.investmentInstrumentRepository = investmentInstrumentRepository;
        this.investmentPlatformRepository = investmentPlatformRepository;
        this.investmentTypeCatalogRepository = investmentTypeCatalogRepository;
    }

    public List<InvestmentInstrumentDTO> getAllInstruments() {
        return investmentInstrumentRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toInstrumentDto)
                .toList();
    }

    public InvestmentInstrumentDTO getInstrumentById(Long id) {
        return toInstrumentDto(findInstrument(id));
    }

    public InvestmentInstrumentDTO createInstrument(InvestmentInstrumentDTO request) {
        validateInstrumentRequest(request);
        String code = normalizeCode(request.getCode());
        if (investmentInstrumentRepository.existsByCodeIgnoreCase(code)) {
            throw new InvestmentValidationException("Instrument code already exists: " + code);
        }

        InvestmentInstrument instrument = InvestmentInstrument.builder()
                .typeId(request.getTypeId())
                .code(code)
                .symbol(normalizeSymbol(request.getSymbol()))
                .name(trimRequired(request.getName(), "name"))
                .market(trimToNull(request.getMarket()))
                .currency(normalizeCurrency(request.getCurrency()))
                .lastPrice(request.getLastPrice())
                .lastPriceSource(trimToNull(request.getLastPriceSource()))
                .lastPriceAt(request.getLastPriceAt())
                .build();

        return toInstrumentDto(investmentInstrumentRepository.save(instrument));
    }

    public InvestmentInstrumentDTO updateInstrument(Long id, InvestmentInstrumentDTO request) {
        validateInstrumentRequest(request);
        String code = normalizeCode(request.getCode());
        if (investmentInstrumentRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new InvestmentValidationException("Instrument code already exists: " + code);
        }

        InvestmentInstrument instrument = findInstrument(id);
        instrument.setTypeId(request.getTypeId());
        instrument.setCode(code);
        instrument.setSymbol(normalizeSymbol(request.getSymbol()));
        instrument.setName(trimRequired(request.getName(), "name"));
        instrument.setMarket(trimToNull(request.getMarket()));
        instrument.setCurrency(normalizeCurrency(request.getCurrency()));
        instrument.setLastPrice(request.getLastPrice());
        instrument.setLastPriceSource(trimToNull(request.getLastPriceSource()));
        instrument.setLastPriceAt(request.getLastPriceAt());

        return toInstrumentDto(investmentInstrumentRepository.save(instrument));
    }

    public void deleteInstrument(Long id) {
        InvestmentInstrument instrument = findInstrument(id);
        try {
            investmentInstrumentRepository.delete(instrument);
        } catch (DataIntegrityViolationException ex) {
            throw new InvestmentValidationException(
                    "Instrument " + id + " cannot be deleted because it is referenced by other records.");
        }
    }

    public List<InvestmentPlatformDTO> getAllPlatforms() {
        return investmentPlatformRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toPlatformDto)
                .toList();
    }

    public InvestmentPlatformDTO getPlatformById(Long id) {
        return toPlatformDto(findPlatform(id));
    }

    public InvestmentPlatformDTO createPlatform(InvestmentPlatformDTO request) {
        validatePlatformRequest(request);
        String code = normalizePlatformCode(request.getCode());
        if (investmentPlatformRepository.existsByCodeIgnoreCase(code)) {
            throw new InvestmentValidationException("Platform code already exists: " + code);
        }

        InvestmentPlatform platform = InvestmentPlatform.builder()
                .code(code)
                .name(trimRequired(request.getName(), "name"))
                .build();

        return toPlatformDto(investmentPlatformRepository.save(platform));
    }

    public InvestmentPlatformDTO updatePlatform(Long id, InvestmentPlatformDTO request) {
        validatePlatformRequest(request);
        String code = normalizePlatformCode(request.getCode());
        if (investmentPlatformRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new InvestmentValidationException("Platform code already exists: " + code);
        }

        InvestmentPlatform platform = findPlatform(id);
        platform.setCode(code);
        platform.setName(trimRequired(request.getName(), "name"));

        return toPlatformDto(investmentPlatformRepository.save(platform));
    }

    public void deletePlatform(Long id) {
        InvestmentPlatform platform = findPlatform(id);
        try {
            investmentPlatformRepository.delete(platform);
        } catch (DataIntegrityViolationException ex) {
            throw new InvestmentValidationException(
                    "Platform " + id + " cannot be deleted because it is referenced by other records.");
        }
    }

    private InvestmentInstrument findInstrument(Long id) {
        if (id == null || id <= 0) {
            throw new InvestmentValidationException("instrument id is required and must be > 0");
        }
        return investmentInstrumentRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("Instrument not found with id: " + id));
    }

    private InvestmentPlatform findPlatform(Long id) {
        if (id == null || id <= 0) {
            throw new InvestmentValidationException("platform id is required and must be > 0");
        }
        return investmentPlatformRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("Platform not found with id: " + id));
    }

    private void validateInstrumentRequest(InvestmentInstrumentDTO request) {
        if (request.getTypeId() == null || request.getTypeId() <= 0) {
            throw new InvestmentValidationException("typeId is required and must be > 0");
        }
        if (investmentTypeCatalogRepository.findById(request.getTypeId()).isEmpty()) {
            throw new InvestmentValidationException("Unknown typeId: " + request.getTypeId());
        }
        if (request.getLastPrice() != null && request.getLastPrice().signum() < 0) {
            throw new InvestmentValidationException("lastPrice must be >= 0");
        }
    }

    private void validatePlatformRequest(InvestmentPlatformDTO request) {
        trimRequired(request.getCode(), "code");
        trimRequired(request.getName(), "name");
    }

    private InvestmentInstrumentDTO toInstrumentDto(InvestmentInstrument instrument) {
        return InvestmentInstrumentDTO.builder()
                .id(instrument.getId())
                .typeId(instrument.getTypeId())
                .code(instrument.getCode())
                .symbol(instrument.getSymbol())
                .name(instrument.getName())
                .market(instrument.getMarket())
                .currency(instrument.getCurrency())
                .lastPrice(instrument.getLastPrice())
                .lastPriceSource(instrument.getLastPriceSource())
                .lastPriceAt(instrument.getLastPriceAt())
                .build();
    }

    private InvestmentPlatformDTO toPlatformDto(InvestmentPlatform platform) {
        return InvestmentPlatformDTO.builder()
                .id(platform.getId())
                .code(platform.getCode())
                .name(platform.getName())
                .build();
    }

    private String normalizeCode(String code) {
        return trimRequired(code, "code").toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        return trimRequired(symbol, "symbol").toUpperCase(Locale.ROOT);
    }

    private String normalizePlatformCode(String code) {
        return trimRequired(code, "code").toUpperCase(Locale.ROOT);
    }

    private String normalizeCurrency(String currency) {
        String normalized = trimRequired(currency, "currency").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new InvestmentValidationException("currency must be exactly 3 letters");
        }
        return normalized;
    }

    private String trimRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvestmentValidationException(fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
