package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import es.triana.company.investments.model.api.ExposureOverviewBucketDTO;
import es.triana.company.investments.model.api.ExposureOverviewDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentCountryCatalog;
import es.triana.company.investments.model.db.InvestmentIndustryCatalog;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentInstrumentExposure;
import es.triana.company.investments.model.db.InvestmentMarketRegimeCatalog;
import es.triana.company.investments.model.db.InvestmentRegionCatalog;
import es.triana.company.investments.model.db.InvestmentSectorCatalog;
import es.triana.company.investments.repository.InvestmentCountryCatalogRepository;
import es.triana.company.investments.repository.InvestmentIndustryCatalogRepository;
import es.triana.company.investments.repository.InvestmentInstrumentExposureRepository;
import es.triana.company.investments.repository.InvestmentMarketRegimeCatalogRepository;
import es.triana.company.investments.repository.InvestmentRegionCatalogRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.InvestmentSectorCatalogRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class ExposureOverviewService {

    private final InvestmentRepository investmentRepository;
    private final InvestmentInstrumentExposureRepository investmentInstrumentExposureRepository;
    private final InvestmentCountryCatalogRepository investmentCountryCatalogRepository;
    private final InvestmentRegionCatalogRepository investmentRegionCatalogRepository;
    private final InvestmentSectorCatalogRepository investmentSectorCatalogRepository;
    private final InvestmentIndustryCatalogRepository investmentIndustryCatalogRepository;
    private final InvestmentMarketRegimeCatalogRepository investmentMarketRegimeCatalogRepository;

    public ExposureOverviewService(
            InvestmentRepository investmentRepository,
            InvestmentInstrumentExposureRepository investmentInstrumentExposureRepository,
            InvestmentCountryCatalogRepository investmentCountryCatalogRepository,
            InvestmentRegionCatalogRepository investmentRegionCatalogRepository,
            InvestmentSectorCatalogRepository investmentSectorCatalogRepository,
            InvestmentIndustryCatalogRepository investmentIndustryCatalogRepository,
            InvestmentMarketRegimeCatalogRepository investmentMarketRegimeCatalogRepository) {
        this.investmentRepository = investmentRepository;
        this.investmentInstrumentExposureRepository = investmentInstrumentExposureRepository;
        this.investmentCountryCatalogRepository = investmentCountryCatalogRepository;
        this.investmentRegionCatalogRepository = investmentRegionCatalogRepository;
        this.investmentSectorCatalogRepository = investmentSectorCatalogRepository;
        this.investmentIndustryCatalogRepository = investmentIndustryCatalogRepository;
        this.investmentMarketRegimeCatalogRepository = investmentMarketRegimeCatalogRepository;
    }

    public ExposureOverviewDTO getOverview(Long tenantId, List<String> typeCodes) {
        validateTenant(tenantId);
        Set<String> normalizedTypeCodes = normalizeTypeCodes(typeCodes);

        List<Investment> allInvestments = investmentRepository.findByTenantIdOrderByUpdatedAtDescIdDesc(tenantId);
        List<Investment> filteredInvestments = allInvestments.stream()
                .filter(this::hasActiveQuantity)
                .filter(item -> resolveCurrentValue(item).compareTo(BigDecimal.ZERO) > 0)
                .filter(item -> matchesTypeFilter(item, normalizedTypeCodes))
                .toList();

        BigDecimal totalCurrentValue = filteredInvestments.stream()
                .map(this::resolveCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (filteredInvestments.isEmpty() || totalCurrentValue.compareTo(BigDecimal.ZERO) <= 0) {
            return emptyOverview(normalizedTypeCodes, totalCurrentValue);
        }

        List<Long> instrumentIds = filteredInvestments.stream()
                .map(Investment::getInstrumentId)
                .distinct()
                .toList();

        Map<Long, List<InvestmentInstrumentExposure>> exposuresByInstrument = instrumentIds.isEmpty()
                ? Map.of()
                : investmentInstrumentExposureRepository.findAllWithBucketsByInstrumentIdIn(instrumentIds).stream()
                    .collect(Collectors.groupingBy(item -> item.getInstrument().getId()));

        Map<InvestmentInstrumentExposure.Dimension, Map<String, BucketAccumulator>> accumulators =
                new EnumMap<>(InvestmentInstrumentExposure.Dimension.class);

        Map<Long, InvestmentCountryCatalog> countriesById = loadCountriesById(filteredInvestments, exposuresByInstrument);
        Map<Long, InvestmentRegionCatalog> regionsById = loadRegionsById(filteredInvestments, exposuresByInstrument);
        Map<Long, InvestmentSectorCatalog> sectorsById = loadSectorsById(filteredInvestments, exposuresByInstrument);
        Map<Long, InvestmentIndustryCatalog> industriesById = loadIndustriesById(filteredInvestments, exposuresByInstrument);
        Map<Long, InvestmentMarketRegimeCatalog> marketRegimesById = loadMarketRegimesById(exposuresByInstrument);

        for (Investment investment : filteredInvestments) {
            BigDecimal positionValue = resolveCurrentValue(investment);
            List<InvestmentInstrumentExposure> rows = exposuresByInstrument.getOrDefault(investment.getInstrumentId(), List.of());

            if (!rows.isEmpty()) {
                for (InvestmentInstrumentExposure row : rows) {
                    if (row.getWeightPct() == null || row.getWeightPct().compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BucketRef bucket = resolveBucket(row, countriesById, regionsById, sectorsById, industriesById, marketRegimesById);
                    if (bucket == null) {
                        continue;
                    }
                    BigDecimal value = positionValue
                            .multiply(row.getWeightPct())
                            .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                    add(accumulators, row.getDimension(), bucket.key(), bucket.code(), bucket.name(), value);
                }
                continue;
            }

            InvestmentInstrument instrument = investment.getInstrument();
            if (instrument == null) {
                continue;
            }

            if (instrument.getCountryId() != null) {
                InvestmentCountryCatalog country = countriesById.get(instrument.getCountryId());
                if (country != null) {
                    add(accumulators, InvestmentInstrumentExposure.Dimension.COUNTRY,
                            key("COUNTRY", country.getId()), country.getCode(), country.getName(), positionValue);
                }
            }
            if (instrument.getRegionId() != null) {
                InvestmentRegionCatalog region = regionsById.get(instrument.getRegionId());
                if (region != null) {
                    add(accumulators, InvestmentInstrumentExposure.Dimension.REGION,
                            key("REGION", region.getId()), region.getCode(), region.getName(), positionValue);
                }
            }
            if (instrument.getSectorId() != null) {
                InvestmentSectorCatalog sector = sectorsById.get(instrument.getSectorId());
                if (sector != null) {
                    add(accumulators, InvestmentInstrumentExposure.Dimension.SECTOR,
                            key("SECTOR", sector.getId()), sector.getCode(), sector.getName(), positionValue);
                }
            }
            if (instrument.getIndustryId() != null) {
                InvestmentIndustryCatalog industry = industriesById.get(instrument.getIndustryId());
                if (industry != null) {
                    add(accumulators, InvestmentInstrumentExposure.Dimension.INDUSTRY,
                            key("INDUSTRY", industry.getId()), industry.getCode(), industry.getName(), positionValue);
                }
            }
        }

        return ExposureOverviewDTO.builder()
                .totalCurrentValue(totalCurrentValue.setScale(2, RoundingMode.HALF_UP))
                .appliedTypeCodes(new ArrayList<>(normalizedTypeCodes))
                .countries(toBuckets(accumulators.get(InvestmentInstrumentExposure.Dimension.COUNTRY), totalCurrentValue))
                .regions(toBuckets(accumulators.get(InvestmentInstrumentExposure.Dimension.REGION), totalCurrentValue))
                .sectors(toBuckets(accumulators.get(InvestmentInstrumentExposure.Dimension.SECTOR), totalCurrentValue))
                .industries(toBuckets(accumulators.get(InvestmentInstrumentExposure.Dimension.INDUSTRY), totalCurrentValue))
                .marketRegimes(toBuckets(accumulators.get(InvestmentInstrumentExposure.Dimension.MARKET_REGIME), totalCurrentValue))
                .build();
    }

    private ExposureOverviewDTO emptyOverview(Set<String> normalizedTypeCodes, BigDecimal totalCurrentValue) {
        BigDecimal safeTotal = totalCurrentValue == null ? BigDecimal.ZERO : totalCurrentValue.setScale(2, RoundingMode.HALF_UP);
        return ExposureOverviewDTO.builder()
                .totalCurrentValue(safeTotal)
                .appliedTypeCodes(new ArrayList<>(normalizedTypeCodes))
                .countries(List.of())
                .regions(List.of())
                .sectors(List.of())
                .industries(List.of())
                .marketRegimes(List.of())
                .build();
    }

    private void validateTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new InvestmentValidationException("tenantId is required and must be > 0");
        }
    }

    private Set<String> normalizeTypeCodes(List<String> typeCodes) {
        if (typeCodes == null) {
            return Set.of();
        }
        return typeCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private boolean matchesTypeFilter(Investment investment, Set<String> typeCodes) {
        if (typeCodes.isEmpty()) {
            return true;
        }
        if (investment.getType() == null || investment.getType().getCode() == null) {
            return false;
        }
        return typeCodes.contains(investment.getType().getCode().trim().toUpperCase(Locale.ROOT));
    }

    private boolean hasActiveQuantity(Investment investment) {
        return investment.getQuantity() != null && investment.getQuantity().compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal resolveCurrentValue(Investment investment) {
        if (investment.getCurrentValueCalculated() != null) {
            return investment.getCurrentValueCalculated();
        }
        if (investment.getCurrentValueManual() != null) {
            return investment.getCurrentValueManual();
        }
        return BigDecimal.ZERO;
    }

    private Map<Long, InvestmentCountryCatalog> loadCountriesById(
            List<Investment> investments,
            Map<Long, List<InvestmentInstrumentExposure>> exposuresByInstrument) {
        Set<Long> ids = investments.stream()
                .filter(item -> exposuresByInstrument.getOrDefault(item.getInstrumentId(), List.of()).isEmpty())
                .map(Investment::getInstrument)
                .filter(instrument -> instrument != null && instrument.getCountryId() != null)
                .map(InvestmentInstrument::getCountryId)
                .collect(Collectors.toSet());

        exposuresByInstrument.values().stream()
                .flatMap(List::stream)
                .map(InvestmentInstrumentExposure::getCountry)
                .filter(country -> country != null && country.getId() != null)
                .map(InvestmentCountryCatalog::getId)
                .forEach(ids::add);

        return investmentCountryCatalogRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InvestmentCountryCatalog::getId, item -> item));
    }

    private Map<Long, InvestmentRegionCatalog> loadRegionsById(
            List<Investment> investments,
            Map<Long, List<InvestmentInstrumentExposure>> exposuresByInstrument) {
        Set<Long> ids = investments.stream()
                .filter(item -> exposuresByInstrument.getOrDefault(item.getInstrumentId(), List.of()).isEmpty())
                .map(Investment::getInstrument)
                .filter(instrument -> instrument != null && instrument.getRegionId() != null)
                .map(InvestmentInstrument::getRegionId)
                .collect(Collectors.toSet());

        exposuresByInstrument.values().stream()
                .flatMap(List::stream)
                .map(InvestmentInstrumentExposure::getRegion)
                .filter(region -> region != null && region.getId() != null)
                .map(InvestmentRegionCatalog::getId)
                .forEach(ids::add);

        return investmentRegionCatalogRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InvestmentRegionCatalog::getId, item -> item));
    }

    private Map<Long, InvestmentSectorCatalog> loadSectorsById(
            List<Investment> investments,
            Map<Long, List<InvestmentInstrumentExposure>> exposuresByInstrument) {
        Set<Long> ids = investments.stream()
                .filter(item -> exposuresByInstrument.getOrDefault(item.getInstrumentId(), List.of()).isEmpty())
                .map(Investment::getInstrument)
                .filter(instrument -> instrument != null && instrument.getSectorId() != null)
                .map(InvestmentInstrument::getSectorId)
                .collect(Collectors.toSet());

        exposuresByInstrument.values().stream()
                .flatMap(List::stream)
                .map(InvestmentInstrumentExposure::getSector)
                .filter(sector -> sector != null && sector.getId() != null)
                .map(InvestmentSectorCatalog::getId)
                .forEach(ids::add);

        return investmentSectorCatalogRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InvestmentSectorCatalog::getId, item -> item));
    }

    private Map<Long, InvestmentIndustryCatalog> loadIndustriesById(
            List<Investment> investments,
            Map<Long, List<InvestmentInstrumentExposure>> exposuresByInstrument) {
        Set<Long> ids = investments.stream()
                .filter(item -> exposuresByInstrument.getOrDefault(item.getInstrumentId(), List.of()).isEmpty())
                .map(Investment::getInstrument)
                .filter(instrument -> instrument != null && instrument.getIndustryId() != null)
                .map(InvestmentInstrument::getIndustryId)
                .collect(Collectors.toSet());

        exposuresByInstrument.values().stream()
                .flatMap(List::stream)
                .map(InvestmentInstrumentExposure::getIndustry)
                .filter(industry -> industry != null && industry.getId() != null)
                .map(InvestmentIndustryCatalog::getId)
                .forEach(ids::add);

        return investmentIndustryCatalogRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InvestmentIndustryCatalog::getId, item -> item));
    }

    private Map<Long, InvestmentMarketRegimeCatalog> loadMarketRegimesById(
            Map<Long, List<InvestmentInstrumentExposure>> exposuresByInstrument) {
        Set<Long> ids = exposuresByInstrument.values().stream()
                .flatMap(List::stream)
                .map(InvestmentInstrumentExposure::getMarketRegime)
                .filter(item -> item != null && item.getId() != null)
                .map(InvestmentMarketRegimeCatalog::getId)
                .collect(Collectors.toSet());

        return investmentMarketRegimeCatalogRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(InvestmentMarketRegimeCatalog::getId, item -> item));
    }

    private BucketRef resolveBucket(
            InvestmentInstrumentExposure exposure,
            Map<Long, InvestmentCountryCatalog> countriesById,
            Map<Long, InvestmentRegionCatalog> regionsById,
            Map<Long, InvestmentSectorCatalog> sectorsById,
            Map<Long, InvestmentIndustryCatalog> industriesById,
            Map<Long, InvestmentMarketRegimeCatalog> marketRegimesById) {
        return switch (exposure.getDimension()) {
            case COUNTRY -> {
                InvestmentCountryCatalog country = exposure.getCountry();
                if (country != null && country.getId() != null) {
                    country = countriesById.getOrDefault(country.getId(), country);
                    yield new BucketRef(key("COUNTRY", country.getId()), country.getCode(), country.getName());
                }
                yield null;
            }
            case REGION -> {
                InvestmentRegionCatalog region = exposure.getRegion();
                if (region != null && region.getId() != null) {
                    region = regionsById.getOrDefault(region.getId(), region);
                    yield new BucketRef(key("REGION", region.getId()), region.getCode(), region.getName());
                }
                yield null;
            }
            case SECTOR -> {
                InvestmentSectorCatalog sector = exposure.getSector();
                if (sector != null && sector.getId() != null) {
                    sector = sectorsById.getOrDefault(sector.getId(), sector);
                    yield new BucketRef(key("SECTOR", sector.getId()), sector.getCode(), sector.getName());
                }
                yield null;
            }
            case INDUSTRY -> {
                InvestmentIndustryCatalog industry = exposure.getIndustry();
                if (industry != null && industry.getId() != null) {
                    industry = industriesById.getOrDefault(industry.getId(), industry);
                    yield new BucketRef(key("INDUSTRY", industry.getId()), industry.getCode(), industry.getName());
                }
                yield null;
            }
            case MARKET_REGIME -> {
                InvestmentMarketRegimeCatalog marketRegime = exposure.getMarketRegime();
                if (marketRegime != null && marketRegime.getId() != null) {
                    marketRegime = marketRegimesById.getOrDefault(marketRegime.getId(), marketRegime);
                    yield new BucketRef(key("MARKET_REGIME", marketRegime.getId()), marketRegime.getCode(), marketRegime.getName());
                }
                yield null;
            }
        };
    }

    private void add(
            Map<InvestmentInstrumentExposure.Dimension, Map<String, BucketAccumulator>> accumulators,
            InvestmentInstrumentExposure.Dimension dimension,
            String key,
            String code,
            String name,
            BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<String, BucketAccumulator> byDimension = accumulators.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>());
        BucketAccumulator bucket = byDimension.computeIfAbsent(key, ignored -> new BucketAccumulator(code, name));
        bucket.currentValue = bucket.currentValue.add(value);
    }

    private List<ExposureOverviewBucketDTO> toBuckets(Map<String, BucketAccumulator> map, BigDecimal totalCurrentValue) {
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        return map.values().stream()
                .map(item -> {
                    BigDecimal currentValue = item.currentValue.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal sharePct = totalCurrentValue.compareTo(BigDecimal.ZERO) > 0
                            ? item.currentValue
                                    .divide(totalCurrentValue, 8, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100))
                                    .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return ExposureOverviewBucketDTO.builder()
                            .code(item.code)
                            .name(item.name)
                            .currentValue(currentValue)
                            .sharePct(sharePct)
                            .build();
                })
                .sorted(Comparator.comparing(ExposureOverviewBucketDTO::getCurrentValue, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String key(String prefix, Long id) {
        return prefix + ":" + id;
    }

    private static class BucketAccumulator {
        private final String code;
        private final String name;
        private BigDecimal currentValue;

        private BucketAccumulator(String code, String name) {
            this.code = code;
            this.name = name;
            this.currentValue = BigDecimal.ZERO;
        }
    }

    private record BucketRef(String key, String code, String name) {
    }
}