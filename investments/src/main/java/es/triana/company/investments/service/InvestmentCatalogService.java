package es.triana.company.investments.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import es.triana.company.investments.model.api.CatalogOptionDTO;
import es.triana.company.investments.model.api.InvestmentInstrumentDTO;
import es.triana.company.investments.model.api.InvestmentInstrumentExposureDTO;
import es.triana.company.investments.model.api.InvestmentPlatformDTO;
import es.triana.company.investments.model.db.InvestmentCountryCatalog;
import es.triana.company.investments.model.db.InvestmentIndustryCatalog;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentPlatform;
import es.triana.company.investments.model.db.InvestmentRegionCatalog;
import es.triana.company.investments.model.db.InvestmentSectorCatalog;
import es.triana.company.investments.model.db.InvestmentInstrumentExposure;
import es.triana.company.investments.model.db.InvestmentTypeCatalog;
import es.triana.company.investments.repository.InvestmentCountryCatalogRepository;
import es.triana.company.investments.repository.InvestmentIndustryCatalogRepository;
import es.triana.company.investments.repository.InvestmentInstrumentExposureRepository;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentPlatformRepository;
import es.triana.company.investments.repository.InvestmentRegionCatalogRepository;
import es.triana.company.investments.repository.InvestmentSectorCatalogRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;
import es.triana.company.investments.service.exception.CatalogNotFoundException;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class InvestmentCatalogService {

    private static final Logger LOG = LoggerFactory.getLogger(InvestmentCatalogService.class);

    private final InvestmentInstrumentRepository investmentInstrumentRepository;
    private final InvestmentPlatformRepository investmentPlatformRepository;
    private final InvestmentTypeCatalogRepository investmentTypeCatalogRepository;
    private final InvestmentCountryCatalogRepository investmentCountryCatalogRepository;
    private final InvestmentRegionCatalogRepository investmentRegionCatalogRepository;
    private final InvestmentSectorCatalogRepository investmentSectorCatalogRepository;
    private final InvestmentIndustryCatalogRepository investmentIndustryCatalogRepository;
    private final InvestmentInstrumentExposureRepository investmentInstrumentExposureRepository;

    public InvestmentCatalogService(InvestmentInstrumentRepository investmentInstrumentRepository,
            InvestmentPlatformRepository investmentPlatformRepository,
            InvestmentTypeCatalogRepository investmentTypeCatalogRepository,
            InvestmentCountryCatalogRepository investmentCountryCatalogRepository,
            InvestmentRegionCatalogRepository investmentRegionCatalogRepository,
            InvestmentSectorCatalogRepository investmentSectorCatalogRepository,
            InvestmentIndustryCatalogRepository investmentIndustryCatalogRepository,
            InvestmentInstrumentExposureRepository investmentInstrumentExposureRepository) {
        this.investmentInstrumentRepository = investmentInstrumentRepository;
        this.investmentPlatformRepository = investmentPlatformRepository;
        this.investmentTypeCatalogRepository = investmentTypeCatalogRepository;
        this.investmentCountryCatalogRepository = investmentCountryCatalogRepository;
        this.investmentRegionCatalogRepository = investmentRegionCatalogRepository;
        this.investmentSectorCatalogRepository = investmentSectorCatalogRepository;
        this.investmentIndustryCatalogRepository = investmentIndustryCatalogRepository;
        this.investmentInstrumentExposureRepository = investmentInstrumentExposureRepository;
    }

    public List<InvestmentInstrumentDTO> getAllInstruments() {
        Map<Long, CatalogOptionDTO> countries = loadCountryCatalogMap();
        Map<Long, CatalogOptionDTO> regions = loadRegionCatalogMap();
        Map<Long, CatalogOptionDTO> sectors = loadSectorCatalogMap();
        Map<Long, CatalogOptionDTO> industries = loadIndustryCatalogMap();

        return investmentInstrumentRepository.findAllByOrderByNameAsc()
                .stream()
                .map(instrument -> toInstrumentDto(instrument, countries, regions, sectors, industries))
                .toList();
    }

    public List<InvestmentTypeCatalog> getAllTypes() {
        return investmentTypeCatalogRepository.findAll();
    }

    public List<CatalogOptionDTO> getAllCountries() {
        return investmentCountryCatalogRepository.findAllByOrderByNameAsc().stream()
                .map(this::toCatalogOption)
                .toList();
    }

    public List<CatalogOptionDTO> getAllRegions() {
        return investmentRegionCatalogRepository.findAllByOrderByNameAsc().stream()
                .map(this::toCatalogOption)
                .toList();
    }

    public List<CatalogOptionDTO> getAllSectors() {
        return investmentSectorCatalogRepository.findAllByOrderByNameAsc().stream()
                .map(this::toCatalogOption)
                .toList();
    }

    public List<CatalogOptionDTO> getAllIndustries() {
        return investmentIndustryCatalogRepository.findAllByOrderByNameAsc().stream()
                .map(this::toCatalogOption)
                .toList();
    }

    public CatalogOptionDTO createCountry(CatalogOptionDTO request) {
        String code = normalizeCodeWithLength(request.getCode(), "country code", 2);
        String name = trimRequired(request.getName(), "country name");
        if (investmentCountryCatalogRepository.existsByCodeIgnoreCase(code)) {
            throw new InvestmentValidationException("Country code already exists: " + code);
        }
        InvestmentCountryCatalog saved = investmentCountryCatalogRepository.save(
                InvestmentCountryCatalog.builder().code(code).name(name).build());
        return toCatalogOption(saved);
    }

    public CatalogOptionDTO updateCountry(Long id, CatalogOptionDTO request) {
        InvestmentCountryCatalog entity = findCountry(id);
        String code = normalizeCodeWithLength(request.getCode(), "country code", 2);
        String name = trimRequired(request.getName(), "country name");
        if (investmentCountryCatalogRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new InvestmentValidationException("Country code already exists: " + code);
        }
        entity.setCode(code);
        entity.setName(name);
        return toCatalogOption(investmentCountryCatalogRepository.save(entity));
    }

    public void deleteCountry(Long id) {
        findCountry(id);
        if (investmentInstrumentRepository.existsByCountryId(id)) {
            throw new InvestmentValidationException("Country " + id + " cannot be deleted because it is referenced by instruments.");
        }
        investmentCountryCatalogRepository.deleteById(id);
    }

    public CatalogOptionDTO createRegion(CatalogOptionDTO request) {
        String code = normalizeCodeWithLength(request.getCode(), "region code", 40);
        String name = trimRequired(request.getName(), "region name");
        if (investmentRegionCatalogRepository.existsByCodeIgnoreCase(code)) {
            throw new InvestmentValidationException("Region code already exists: " + code);
        }
        InvestmentRegionCatalog saved = investmentRegionCatalogRepository.save(
                InvestmentRegionCatalog.builder().code(code).name(name).build());
        return toCatalogOption(saved);
    }

    public CatalogOptionDTO updateRegion(Long id, CatalogOptionDTO request) {
        InvestmentRegionCatalog entity = findRegion(id);
        String code = normalizeCodeWithLength(request.getCode(), "region code", 40);
        String name = trimRequired(request.getName(), "region name");
        if (investmentRegionCatalogRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new InvestmentValidationException("Region code already exists: " + code);
        }
        entity.setCode(code);
        entity.setName(name);
        return toCatalogOption(investmentRegionCatalogRepository.save(entity));
    }

    public void deleteRegion(Long id) {
        findRegion(id);
        if (investmentInstrumentRepository.existsByRegionId(id)) {
            throw new InvestmentValidationException("Region " + id + " cannot be deleted because it is referenced by instruments.");
        }
        investmentRegionCatalogRepository.deleteById(id);
    }

    public CatalogOptionDTO createSector(CatalogOptionDTO request) {
        String code = normalizeCodeWithLength(request.getCode(), "sector code", 60);
        String name = trimRequired(request.getName(), "sector name");
        if (investmentSectorCatalogRepository.existsByCodeIgnoreCase(code)) {
            throw new InvestmentValidationException("Sector code already exists: " + code);
        }
        InvestmentSectorCatalog saved = investmentSectorCatalogRepository.save(
                InvestmentSectorCatalog.builder().code(code).name(name).build());
        return toCatalogOption(saved);
    }

    public CatalogOptionDTO updateSector(Long id, CatalogOptionDTO request) {
        InvestmentSectorCatalog entity = findSector(id);
        String code = normalizeCodeWithLength(request.getCode(), "sector code", 60);
        String name = trimRequired(request.getName(), "sector name");
        if (investmentSectorCatalogRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new InvestmentValidationException("Sector code already exists: " + code);
        }
        entity.setCode(code);
        entity.setName(name);
        return toCatalogOption(investmentSectorCatalogRepository.save(entity));
    }

    public void deleteSector(Long id) {
        findSector(id);
        if (investmentInstrumentRepository.existsBySectorId(id)) {
            throw new InvestmentValidationException("Sector " + id + " cannot be deleted because it is referenced by instruments.");
        }
        investmentSectorCatalogRepository.deleteById(id);
    }

    public CatalogOptionDTO createIndustry(CatalogOptionDTO request) {
        String code = normalizeCodeWithLength(request.getCode(), "industry code", 80);
        String name = trimRequired(request.getName(), "industry name");
        if (investmentIndustryCatalogRepository.existsByCodeIgnoreCase(code)) {
            throw new InvestmentValidationException("Industry code already exists: " + code);
        }
        InvestmentIndustryCatalog saved = investmentIndustryCatalogRepository.save(
                InvestmentIndustryCatalog.builder().code(code).name(name).build());
        return toCatalogOption(saved);
    }

    public CatalogOptionDTO updateIndustry(Long id, CatalogOptionDTO request) {
        InvestmentIndustryCatalog entity = findIndustry(id);
        String code = normalizeCodeWithLength(request.getCode(), "industry code", 80);
        String name = trimRequired(request.getName(), "industry name");
        if (investmentIndustryCatalogRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new InvestmentValidationException("Industry code already exists: " + code);
        }
        entity.setCode(code);
        entity.setName(name);
        return toCatalogOption(investmentIndustryCatalogRepository.save(entity));
    }

    public void deleteIndustry(Long id) {
        findIndustry(id);
        if (investmentInstrumentRepository.existsByIndustryId(id)) {
            throw new InvestmentValidationException("Industry " + id + " cannot be deleted because it is referenced by instruments.");
        }
        investmentIndustryCatalogRepository.deleteById(id);
    }

    public List<InvestmentInstrumentExposureDTO> getExposuresByInstrument(Long instrumentId) {
        findInstrument(instrumentId);
        return investmentInstrumentExposureRepository.findAllByInstrumentIdOrderByDimensionAscIdAsc(instrumentId)
                .stream()
                .map(this::toExposureDto)
                .toList();
    }

    public InvestmentInstrumentExposureDTO createExposure(Long instrumentId, InvestmentInstrumentExposureDTO request) {
        InvestmentInstrument instrument = findInstrument(instrumentId);
        validateExposureRequest(instrumentId, request);
        InvestmentInstrumentExposure exposure = buildExposureEntity(instrument, request);
        return toExposureDto(investmentInstrumentExposureRepository.save(exposure));
    }

    public InvestmentInstrumentExposureDTO updateExposure(Long instrumentId, Long exposureId, InvestmentInstrumentExposureDTO request) {
        findInstrument(instrumentId);
        validateExposureRequest(instrumentId, request);
        InvestmentInstrumentExposure exposure = investmentInstrumentExposureRepository.findById(exposureId)
                .orElseThrow(() -> new CatalogNotFoundException("Exposure not found with id: " + exposureId));
        if (exposure.getInstrument() == null || !instrumentId.equals(exposure.getInstrument().getId())) {
            throw new InvestmentValidationException("Exposure does not belong to instrument " + instrumentId);
        }
        exposure.setDimension(request.getDimension());
        exposure.setCountry(request.getCountryId() == null ? null : findCountry(request.getCountryId()));
        exposure.setRegion(request.getRegionId() == null ? null : findRegion(request.getRegionId()));
        exposure.setSector(request.getSectorId() == null ? null : findSector(request.getSectorId()));
        exposure.setIndustry(request.getIndustryId() == null ? null : findIndustry(request.getIndustryId()));
        exposure.setWeightPct(request.getWeightPct());
        return toExposureDto(investmentInstrumentExposureRepository.save(exposure));
    }

    public void deleteExposure(Long instrumentId, Long exposureId) {
        findInstrument(instrumentId);
        InvestmentInstrumentExposure exposure = investmentInstrumentExposureRepository.findById(exposureId)
                .orElseThrow(() -> new CatalogNotFoundException("Exposure not found with id: " + exposureId));
        if (exposure.getInstrument() == null || !instrumentId.equals(exposure.getInstrument().getId())) {
            throw new InvestmentValidationException("Exposure does not belong to instrument " + instrumentId);
        }
        investmentInstrumentExposureRepository.delete(exposure);
    }

    public InvestmentInstrumentDTO getInstrumentById(Long id) {
        Map<Long, CatalogOptionDTO> countries = loadCountryCatalogMap();
        Map<Long, CatalogOptionDTO> regions = loadRegionCatalogMap();
        Map<Long, CatalogOptionDTO> sectors = loadSectorCatalogMap();
        Map<Long, CatalogOptionDTO> industries = loadIndustryCatalogMap();
        return toInstrumentDto(findInstrument(id), countries, regions, sectors, industries);
    }

    public InvestmentInstrumentDTO createInstrument(InvestmentInstrumentDTO request) {
        LOG.info("Creating instrument: {}", summarizeInstrumentRequest(request));
        try {
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
                    .scraperUrl(trimToNull(request.getScraperUrl()))
                        .countryId(validateCatalogId(request.getCountryId(), investmentCountryCatalogRepository::existsById, "countryId"))
                        .regionId(validateCatalogId(request.getRegionId(), investmentRegionCatalogRepository::existsById, "regionId"))
                        .sectorId(validateCatalogId(request.getSectorId(), investmentSectorCatalogRepository::existsById, "sectorId"))
                        .industryId(validateCatalogId(request.getIndustryId(), investmentIndustryCatalogRepository::existsById, "industryId"))
                    .build();

            Map<Long, CatalogOptionDTO> countries = loadCountryCatalogMap();
            Map<Long, CatalogOptionDTO> regions = loadRegionCatalogMap();
            Map<Long, CatalogOptionDTO> sectors = loadSectorCatalogMap();
            Map<Long, CatalogOptionDTO> industries = loadIndustryCatalogMap();

            InvestmentInstrumentDTO savedInstrument = toInstrumentDto(
                    investmentInstrumentRepository.save(instrument),
                    countries,
                    regions,
                    sectors,
                    industries);
            LOG.info("Instrument created successfully. id={} code={} symbol={}",
                    savedInstrument.getId(),
                    savedInstrument.getCode(),
                    savedInstrument.getSymbol());
            return savedInstrument;
        } catch (RuntimeException ex) {
            LOG.error("Instrument creation failed: {}", summarizeInstrumentRequest(request), ex);
            throw ex;
        }
    }

    public InvestmentInstrumentDTO updateInstrument(Long id, InvestmentInstrumentDTO request) {
        LOG.info("Updating instrument id={} with payload: {}", id, summarizeInstrumentRequest(request));
        try {
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
            instrument.setScraperUrl(trimToNull(request.getScraperUrl()));
            instrument.setCountryId(validateCatalogId(request.getCountryId(), investmentCountryCatalogRepository::existsById, "countryId"));
            instrument.setRegionId(validateCatalogId(request.getRegionId(), investmentRegionCatalogRepository::existsById, "regionId"));
            instrument.setSectorId(validateCatalogId(request.getSectorId(), investmentSectorCatalogRepository::existsById, "sectorId"));
            instrument.setIndustryId(validateCatalogId(request.getIndustryId(), investmentIndustryCatalogRepository::existsById, "industryId"));

            Map<Long, CatalogOptionDTO> countries = loadCountryCatalogMap();
            Map<Long, CatalogOptionDTO> regions = loadRegionCatalogMap();
            Map<Long, CatalogOptionDTO> sectors = loadSectorCatalogMap();
            Map<Long, CatalogOptionDTO> industries = loadIndustryCatalogMap();

            InvestmentInstrumentDTO updatedInstrument = toInstrumentDto(
                    investmentInstrumentRepository.save(instrument),
                    countries,
                    regions,
                    sectors,
                    industries);
            LOG.info("Instrument updated successfully. id={} code={} symbol={}",
                    updatedInstrument.getId(),
                    updatedInstrument.getCode(),
                    updatedInstrument.getSymbol());
            return updatedInstrument;
        } catch (RuntimeException ex) {
            LOG.error("Instrument update failed. id={} payload={}", id, summarizeInstrumentRequest(request), ex);
            throw ex;
        }
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

    private InvestmentCountryCatalog findCountry(Long id) {
        if (id == null || id <= 0) {
            throw new InvestmentValidationException("country id is required and must be > 0");
        }
        return investmentCountryCatalogRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("Country not found with id: " + id));
    }

    private InvestmentRegionCatalog findRegion(Long id) {
        if (id == null || id <= 0) {
            throw new InvestmentValidationException("region id is required and must be > 0");
        }
        return investmentRegionCatalogRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("Region not found with id: " + id));
    }

    private InvestmentSectorCatalog findSector(Long id) {
        if (id == null || id <= 0) {
            throw new InvestmentValidationException("sector id is required and must be > 0");
        }
        return investmentSectorCatalogRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("Sector not found with id: " + id));
    }

    private InvestmentIndustryCatalog findIndustry(Long id) {
        if (id == null || id <= 0) {
            throw new InvestmentValidationException("industry id is required and must be > 0");
        }
        return investmentIndustryCatalogRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("Industry not found with id: " + id));
    }

    private void validateExposureRequest(Long instrumentId, InvestmentInstrumentExposureDTO request) {
        if (request == null) {
            throw new InvestmentValidationException("exposure request is required");
        }
        if (request.getInstrumentId() != null && !instrumentId.equals(request.getInstrumentId())) {
            throw new InvestmentValidationException("instrumentId does not match path parameter");
        }
        if (request.getWeightPct() == null || request.getWeightPct().signum() < 0) {
            throw new InvestmentValidationException("weightPct must be >= 0");
        }
        if (request.getWeightPct().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new InvestmentValidationException("weightPct must be <= 100");
        }
        resolveExposureBucket(request);
    }

    private InvestmentInstrumentExposure buildExposureEntity(InvestmentInstrument instrument, InvestmentInstrumentExposureDTO request) {
        InvestmentInstrumentExposure.Dimension dimension = request.getDimension();
        InvestmentInstrumentExposure exposure = InvestmentInstrumentExposure.builder()
                .instrument(instrument)
                .dimension(dimension)
                .weightPct(request.getWeightPct())
                .build();
        assignExposureBucket(exposure, request);
        return exposure;
    }

    private void assignExposureBucket(InvestmentInstrumentExposure exposure, InvestmentInstrumentExposureDTO request) {
        switch (request.getDimension()) {
            case COUNTRY -> exposure.setCountry(findCountry(resolveExposureBucketId(request)));
            case REGION -> exposure.setRegion(findRegion(resolveExposureBucketId(request)));
            case SECTOR -> exposure.setSector(findSector(resolveExposureBucketId(request)));
            case INDUSTRY -> exposure.setIndustry(findIndustry(resolveExposureBucketId(request)));
        }
    }

    private CatalogOptionDTO resolveExposureBucket(InvestmentInstrumentExposureDTO request) {
        Long bucketId = resolveExposureBucketId(request);
        return switch (request.getDimension()) {
            case COUNTRY -> toCatalogOption(findCountry(bucketId));
            case REGION -> toCatalogOption(findRegion(bucketId));
            case SECTOR -> toCatalogOption(findSector(bucketId));
            case INDUSTRY -> toCatalogOption(findIndustry(bucketId));
        };
    }

    private Long resolveExposureBucketId(InvestmentInstrumentExposureDTO request) {
        return switch (request.getDimension()) {
            case COUNTRY -> request.getCountryId();
            case REGION -> request.getRegionId();
            case SECTOR -> request.getSectorId();
            case INDUSTRY -> request.getIndustryId();
        };
    }

    private InvestmentInstrumentExposureDTO toExposureDto(InvestmentInstrumentExposure exposure) {
        CatalogOptionDTO bucket = switch (exposure.getDimension()) {
            case COUNTRY -> exposure.getCountry() == null ? null : toCatalogOption(exposure.getCountry());
            case REGION -> exposure.getRegion() == null ? null : toCatalogOption(exposure.getRegion());
            case SECTOR -> exposure.getSector() == null ? null : toCatalogOption(exposure.getSector());
            case INDUSTRY -> exposure.getIndustry() == null ? null : toCatalogOption(exposure.getIndustry());
        };

        return InvestmentInstrumentExposureDTO.builder()
                .id(exposure.getId())
                .instrumentId(exposure.getInstrument() == null ? null : exposure.getInstrument().getId())
                .dimension(exposure.getDimension())
                .countryId(exposure.getCountry() == null ? null : exposure.getCountry().getId())
                .regionId(exposure.getRegion() == null ? null : exposure.getRegion().getId())
                .sectorId(exposure.getSector() == null ? null : exposure.getSector().getId())
                .industryId(exposure.getIndustry() == null ? null : exposure.getIndustry().getId())
                .bucketCode(bucket == null ? null : bucket.getCode())
                .bucketName(bucket == null ? null : bucket.getName())
                .weightPct(exposure.getWeightPct())
                .build();
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

    private InvestmentInstrumentDTO toInstrumentDto(InvestmentInstrument instrument,
            Map<Long, CatalogOptionDTO> countries,
            Map<Long, CatalogOptionDTO> regions,
            Map<Long, CatalogOptionDTO> sectors,
            Map<Long, CatalogOptionDTO> industries) {
        CatalogOptionDTO country = countries.get(instrument.getCountryId());
        CatalogOptionDTO region = regions.get(instrument.getRegionId());
        CatalogOptionDTO sector = sectors.get(instrument.getSectorId());
        CatalogOptionDTO industry = industries.get(instrument.getIndustryId());

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
                .scraperUrl(instrument.getScraperUrl())
                .countryId(instrument.getCountryId())
                .countryCode(country == null ? null : country.getCode())
                .countryName(country == null ? null : country.getName())
                .regionId(instrument.getRegionId())
                .regionCode(region == null ? null : region.getCode())
                .regionName(region == null ? null : region.getName())
                .sectorId(instrument.getSectorId())
                .sectorCode(sector == null ? null : sector.getCode())
                .sectorName(sector == null ? null : sector.getName())
                .industryId(instrument.getIndustryId())
                .industryCode(industry == null ? null : industry.getCode())
                .industryName(industry == null ? null : industry.getName())
                .build();
    }

    private CatalogOptionDTO toCatalogOption(InvestmentCountryCatalog country) {
        return CatalogOptionDTO.builder()
                .id(country.getId())
                .code(country.getCode())
                .name(country.getName())
                .build();
    }

    private CatalogOptionDTO toCatalogOption(InvestmentRegionCatalog region) {
        return CatalogOptionDTO.builder()
                .id(region.getId())
                .code(region.getCode())
                .name(region.getName())
                .build();
    }

    private CatalogOptionDTO toCatalogOption(InvestmentSectorCatalog sector) {
        return CatalogOptionDTO.builder()
                .id(sector.getId())
                .code(sector.getCode())
                .name(sector.getName())
                .build();
    }

    private CatalogOptionDTO toCatalogOption(InvestmentIndustryCatalog industry) {
        return CatalogOptionDTO.builder()
                .id(industry.getId())
                .code(industry.getCode())
                .name(industry.getName())
                .build();
    }

    private Map<Long, CatalogOptionDTO> loadCountryCatalogMap() {
        return toCatalogMap(investmentCountryCatalogRepository.findAllByOrderByNameAsc(), this::toCatalogOption);
    }

    private Map<Long, CatalogOptionDTO> loadRegionCatalogMap() {
        return toCatalogMap(investmentRegionCatalogRepository.findAllByOrderByNameAsc(), this::toCatalogOption);
    }

    private Map<Long, CatalogOptionDTO> loadSectorCatalogMap() {
        return toCatalogMap(investmentSectorCatalogRepository.findAllByOrderByNameAsc(), this::toCatalogOption);
    }

    private Map<Long, CatalogOptionDTO> loadIndustryCatalogMap() {
        return toCatalogMap(investmentIndustryCatalogRepository.findAllByOrderByNameAsc(), this::toCatalogOption);
    }

    private <T> Map<Long, CatalogOptionDTO> toCatalogMap(List<T> items, Function<T, CatalogOptionDTO> mapper) {
        return items.stream()
                .map(mapper)
                .collect(Collectors.toMap(CatalogOptionDTO::getId, Function.identity()));
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

    private String normalizeCodeWithLength(String value, String fieldName, int maxLength) {
        String normalized = trimRequired(value, fieldName).toUpperCase(Locale.ROOT);
        if (normalized.length() > maxLength) {
            throw new InvestmentValidationException(fieldName + " max length is " + maxLength);
        }
        return normalized;
    }

    private Long validateCatalogId(Long id, Function<Long, Boolean> existsFn, String fieldName) {
        if (id == null) {
            return null;
        }
        if (id <= 0) {
            throw new InvestmentValidationException(fieldName + " must be > 0");
        }
        if (!existsFn.apply(id)) {
            throw new InvestmentValidationException("Unknown " + fieldName + ": " + id);
        }
        return id;
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

    private String summarizeInstrumentRequest(InvestmentInstrumentDTO request) {
        if (request == null) {
            return "request=null";
        }
        return String.format(
                "typeId=%s, code=%s, symbol=%s, name=%s, market=%s, currency=%s, lastPrice=%s, lastPriceSource=%s, lastPriceAt=%s, scraperUrl=%s, countryId=%s, regionId=%s, sectorId=%s, industryId=%s",
                request.getTypeId(),
                request.getCode(),
                request.getSymbol(),
                request.getName(),
                request.getMarket(),
                request.getCurrency(),
                request.getLastPrice(),
                request.getLastPriceSource(),
                request.getLastPriceAt(),
                request.getScraperUrl(),
                request.getCountryId(),
                request.getRegionId(),
                request.getSectorId(),
                request.getIndustryId());
    }
}
