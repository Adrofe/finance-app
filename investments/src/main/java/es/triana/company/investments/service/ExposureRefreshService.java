package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.client.FinectExposureClient;
import es.triana.company.investments.model.api.ExposureRefreshResultDTO;
import es.triana.company.investments.model.api.ExposureSuggestionDTO;
import es.triana.company.investments.model.api.InvestmentInstrumentExposureDTO;
import es.triana.company.investments.model.db.InvestmentCountryAlias;
import es.triana.company.investments.model.db.InvestmentCountryCatalog;
import es.triana.company.investments.model.db.InvestmentIndustryCatalog;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentInstrumentExposure;
import es.triana.company.investments.model.db.InvestmentMarketRegimeCatalog;
import es.triana.company.investments.model.db.InvestmentRegionAlias;
import es.triana.company.investments.model.db.InvestmentRegionCatalog;
import es.triana.company.investments.model.db.InvestmentSectorCatalog;
import es.triana.company.investments.repository.InvestmentCountryAliasRepository;
import es.triana.company.investments.repository.InvestmentCountryCatalogRepository;
import es.triana.company.investments.repository.InvestmentIndustryCatalogRepository;
import es.triana.company.investments.repository.InvestmentInstrumentExposureRepository;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentMarketRegimeCatalogRepository;
import es.triana.company.investments.repository.InvestmentRegionAliasRepository;
import es.triana.company.investments.repository.InvestmentRegionCatalogRepository;
import es.triana.company.investments.repository.InvestmentSectorCatalogRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;

@Service
public class ExposureRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(ExposureRefreshService.class);
    private static final Set<String> COMPOUND_TYPE_CODES = Set.of("ETF", "FUND");

    private final InvestmentInstrumentRepository investmentInstrumentRepository;
    private final InvestmentTypeCatalogRepository investmentTypeCatalogRepository;
    private final InvestmentInstrumentExposureRepository investmentInstrumentExposureRepository;
    private final InvestmentCountryAliasRepository countryAliasRepository;
    private final InvestmentRegionAliasRepository regionAliasRepository;
    private final InvestmentCountryCatalogRepository countryCatalogRepository;
    private final InvestmentRegionCatalogRepository regionCatalogRepository;
    private final InvestmentSectorCatalogRepository sectorCatalogRepository;
    private final InvestmentIndustryCatalogRepository industryCatalogRepository;
    private final InvestmentMarketRegimeCatalogRepository marketRegimeCatalogRepository;
    private final FinectExposureClient finectExposureClient;

    @Value("${investments.exposures.auto-refresh-cron:0 0 3 * * *}")
    private String autoRefreshCron;

    public ExposureRefreshService(
            InvestmentInstrumentRepository investmentInstrumentRepository,
            InvestmentTypeCatalogRepository investmentTypeCatalogRepository,
            InvestmentInstrumentExposureRepository investmentInstrumentExposureRepository,
            InvestmentCountryAliasRepository countryAliasRepository,
            InvestmentRegionAliasRepository regionAliasRepository,
            InvestmentCountryCatalogRepository countryCatalogRepository,
            InvestmentRegionCatalogRepository regionCatalogRepository,
            InvestmentSectorCatalogRepository sectorCatalogRepository,
            InvestmentIndustryCatalogRepository industryCatalogRepository,
            InvestmentMarketRegimeCatalogRepository marketRegimeCatalogRepository,
            FinectExposureClient finectExposureClient) {
        this.investmentInstrumentRepository = investmentInstrumentRepository;
        this.investmentTypeCatalogRepository = investmentTypeCatalogRepository;
        this.investmentInstrumentExposureRepository = investmentInstrumentExposureRepository;
        this.countryAliasRepository = countryAliasRepository;
        this.regionAliasRepository = regionAliasRepository;
        this.countryCatalogRepository = countryCatalogRepository;
        this.regionCatalogRepository = regionCatalogRepository;
        this.sectorCatalogRepository = sectorCatalogRepository;
        this.industryCatalogRepository = industryCatalogRepository;
        this.marketRegimeCatalogRepository = marketRegimeCatalogRepository;
        this.finectExposureClient = finectExposureClient;
    }

    @Transactional
    public ExposureRefreshResultDTO refreshCompoundExposuresNow() {
        List<InvestmentInstrument> instruments = loadCompoundInstruments();
        List<InvestmentCountryCatalog> countriesAll = countryCatalogRepository.findAll();
        List<InvestmentRegionCatalog> regionsAll = regionCatalogRepository.findAll();
        Map<String, InvestmentCountryCatalog> countries = loadCountryMap(countriesAll);
        Map<String, InvestmentRegionCatalog> regions = loadRegionMap(regionsAll);
        Map<Long, InvestmentCountryCatalog> countriesById = countriesAll.stream()
            .collect(Collectors.toMap(InvestmentCountryCatalog::getId, Function.identity(), (left, right) -> left));
        Map<Long, InvestmentRegionCatalog> regionsById = regionsAll.stream()
            .collect(Collectors.toMap(InvestmentRegionCatalog::getId, Function.identity(), (left, right) -> left));
        Map<String, Long> countryAliases = loadCountryAliasMap();
        Map<String, Long> regionAliases = loadRegionAliasMap();
        Map<String, InvestmentSectorCatalog> sectors = loadSectorMap();
        Map<String, InvestmentIndustryCatalog> industries = loadIndustryMap();
        Map<String, InvestmentMarketRegimeCatalog> marketRegimes = loadMarketRegimeMap();

        int updatedInstruments = 0;
        int updatedExposures = 0;
        int skippedNoData = 0;
        List<Long> updatedIds = new ArrayList<>();
        Map<String, Integer> suggestedRegions = new LinkedHashMap<>();
        Map<String, Integer> suggestedCountries = new LinkedHashMap<>();
        Map<String, Integer> suggestedMarketRegimes = new LinkedHashMap<>();

        for (InvestmentInstrument instrument : instruments) {
            try {
                List<InvestmentInstrumentExposureDTO> fetched = finectExposureClient.fetchExposures(instrument);
                if (fetched.isEmpty()) {
                    skippedNoData++;
                    continue;
                }

                List<InvestmentInstrumentExposure> resolved = fetched.stream()
                    .map(dto -> mapExposure(
                            instrument,
                            dto,
                            countries,
                            countriesById,
                            countryAliases,
                            regions,
                            regionsById,
                            regionAliases,
                            sectors,
                            industries,
                            marketRegimes,
                            suggestedRegions,
                            suggestedCountries,
                            suggestedMarketRegimes))
                        .filter(item -> item != null)
                        .toList();

                if (resolved.isEmpty()) {
                    skippedNoData++;
                    continue;
                }

                investmentInstrumentExposureRepository.deleteByInstrumentId(instrument.getId());
                investmentInstrumentExposureRepository.saveAll(resolved);
                updatedInstruments++;
                updatedExposures += resolved.size();
                updatedIds.add(instrument.getId());

                LOG.info("Refreshed Finect compound exposures for instrumentId={} symbol={} rows={}",
                        instrument.getId(), instrument.getSymbol(), resolved.size());
            } catch (Exception ex) {
                LOG.warn("Finect exposure refresh failed for instrumentId={} symbol={} cause={}",
                        instrument.getId(), instrument.getSymbol(), ex.getMessage());
            }
        }

        return ExposureRefreshResultDTO.builder()
                .updatedInstruments(updatedInstruments)
                .updatedExposures(updatedExposures)
                .skippedNoData(skippedNoData)
                .instrumentIds(updatedIds)
                .mode("finect")
                .suggestedRegions(suggestedRegions.entrySet().stream()
                    .map(entry -> ExposureSuggestionDTO.builder()
                        .name(entry.getKey())
                        .occurrences(entry.getValue())
                        .build())
                    .toList())
                .suggestedCountries(suggestedCountries.entrySet().stream()
                    .map(entry -> ExposureSuggestionDTO.builder()
                        .name(entry.getKey())
                        .occurrences(entry.getValue())
                        .build())
                    .toList())
                .suggestedMarketRegimes(suggestedMarketRegimes.entrySet().stream()
                    .map(entry -> ExposureSuggestionDTO.builder()
                        .name(entry.getKey())
                        .occurrences(entry.getValue())
                        .build())
                    .toList())
                .build();
    }

    @Scheduled(cron = "${investments.exposures.auto-refresh-cron:0 0 3 * * *}")
    @Transactional
    public void scheduledExposureRefresh() {
        LOG.info("Scheduled Finect exposure refresh triggered");
        refreshCompoundExposuresNow();
    }

    private List<InvestmentInstrument> loadCompoundInstruments() {
        Map<Long, String> typeCodeById = investmentTypeCatalogRepository.findAll().stream()
                .collect(Collectors.toMap(item -> item.getId(), item -> item.getCode(), (a, b) -> a));

        return investmentInstrumentRepository.findAllByOrderByNameAsc().stream()
                .filter(instrument -> {
                    String typeCode = typeCodeById.get(instrument.getTypeId());
                    return typeCode != null && COMPOUND_TYPE_CODES.contains(typeCode.trim().toUpperCase(Locale.ROOT));
                })
                .toList();
    }

    private Map<String, InvestmentCountryCatalog> loadCountryMap(List<InvestmentCountryCatalog> countries) {
        Map<String, InvestmentCountryCatalog> result = new LinkedHashMap<>();
        for (InvestmentCountryCatalog country : countries) {
            if (country.getName() != null) {
                result.putIfAbsent(normalize(country.getName()), country);
            }
            if (country.getCode() != null) {
                result.putIfAbsent(normalize(country.getCode()), country);
            }
            result.putIfAbsent(normalize(country.getCode() + " " + country.getName()), country);
        }
        return result;
    }

    private Map<String, InvestmentRegionCatalog> loadRegionMap(List<InvestmentRegionCatalog> regions) {
        Map<String, InvestmentRegionCatalog> result = new LinkedHashMap<>();
        for (InvestmentRegionCatalog region : regions) {
            if (region.getName() != null) {
                result.putIfAbsent(normalize(region.getName()), region);
            }
            if (region.getCode() != null) {
                result.putIfAbsent(normalize(region.getCode()), region);
            }
            result.putIfAbsent(normalize(region.getCode() + " " + region.getName()), region);
        }
        return result;
    }

    private Map<String, Long> loadCountryAliasMap() {
        return countryAliasRepository.findAll().stream()
                .collect(Collectors.toMap(
                        InvestmentCountryAlias::getNormalizedSourceName,
                        alias -> alias.getCountry().getId(),
                        (left, right) -> left));
    }

    private Map<String, Long> loadRegionAliasMap() {
        return regionAliasRepository.findAll().stream()
                .collect(Collectors.toMap(
                        InvestmentRegionAlias::getNormalizedSourceName,
                        alias -> alias.getRegion().getId(),
                        (left, right) -> left));
    }

    private Map<String, InvestmentSectorCatalog> loadSectorMap() {
        return sectorCatalogRepository.findAll().stream().collect(Collectors.toMap(
                item -> normalize(item.getCode() + " " + item.getName()),
                Function.identity(),
                (left, right) -> left));
    }

    private Map<String, InvestmentIndustryCatalog> loadIndustryMap() {
        return industryCatalogRepository.findAll().stream().collect(Collectors.toMap(
                item -> normalize(item.getCode() + " " + item.getName()),
                Function.identity(),
                (left, right) -> left));
    }

    private Map<String, InvestmentMarketRegimeCatalog> loadMarketRegimeMap() {
        Map<String, InvestmentMarketRegimeCatalog> result = new LinkedHashMap<>();
        for (InvestmentMarketRegimeCatalog marketRegime : marketRegimeCatalogRepository.findAll()) {
            if (marketRegime.getName() != null) {
                result.putIfAbsent(normalize(marketRegime.getName()), marketRegime);
            }
            if (marketRegime.getCode() != null) {
                result.putIfAbsent(normalize(marketRegime.getCode()), marketRegime);
            }
            result.putIfAbsent(normalize(marketRegime.getCode() + " " + marketRegime.getName()), marketRegime);
        }
        return result;
    }

    private InvestmentInstrumentExposure mapExposure(
            InvestmentInstrument instrument,
            InvestmentInstrumentExposureDTO dto,
            Map<String, InvestmentCountryCatalog> countries,
            Map<Long, InvestmentCountryCatalog> countriesById,
            Map<String, Long> countryAliases,
            Map<String, InvestmentRegionCatalog> regions,
            Map<Long, InvestmentRegionCatalog> regionsById,
            Map<String, Long> regionAliases,
            Map<String, InvestmentSectorCatalog> sectors,
            Map<String, InvestmentIndustryCatalog> industries,
            Map<String, InvestmentMarketRegimeCatalog> marketRegimes,
            Map<String, Integer> suggestedRegions,
            Map<String, Integer> suggestedCountries,
            Map<String, Integer> suggestedMarketRegimes) {

        if (dto.getDimension() == null || dto.getBucketName() == null || dto.getWeightPct() == null) {
            return null;
        }
        if (dto.getWeightPct().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        String bucketKey = normalize(dto.getBucketName());
        InvestmentInstrumentExposure exposure = InvestmentInstrumentExposure.builder()
                .instrument(instrument)
                .dimension(dto.getDimension())
                .weightPct(dto.getWeightPct().setScale(4, java.math.RoundingMode.HALF_UP))
                .build();

        switch (dto.getDimension()) {
            case COUNTRY, REGION -> {
                InvestmentCountryCatalog matchedCountry = countries.get(bucketKey);
                if (matchedCountry == null) {
                    Long aliasCountryId = countryAliases.get(bucketKey);
                    if (aliasCountryId != null) {
                        matchedCountry = countriesById.get(aliasCountryId);
                    }
                }

                InvestmentRegionCatalog matchedRegion = regions.get(bucketKey);
                if (matchedRegion == null) {
                    Long aliasRegionId = regionAliases.get(bucketKey);
                    if (aliasRegionId != null) {
                        matchedRegion = regionsById.get(aliasRegionId);
                    }
                }

                InvestmentMarketRegimeCatalog matchedMarketRegime = marketRegimes.get(bucketKey);

                // Finect can mix countries and regions in the same geographic block.
                // Prefer the dimension requested by the source when both match,
                // but allow automatic cross-dimension mapping when only one exists.
                if (dto.getDimension() == InvestmentInstrumentExposure.Dimension.REGION) {
                    if (matchedMarketRegime != null) {
                        exposure.setDimension(InvestmentInstrumentExposure.Dimension.MARKET_REGIME);
                        exposure.setMarketRegime(matchedMarketRegime);
                        break;
                    }
                    if (matchedRegion != null) {
                        exposure.setRegion(matchedRegion);
                        break;
                    }
                    if (matchedCountry != null) {
                        exposure.setDimension(InvestmentInstrumentExposure.Dimension.COUNTRY);
                        exposure.setCountry(matchedCountry);
                        break;
                    }
                    if (suggestedRegions != null && dto.getBucketName() != null && !dto.getBucketName().isBlank()) {
                        suggestedRegions.merge(dto.getBucketName().trim(), 1, Integer::sum);
                    }
                    return null;
                }

                if (matchedCountry != null) {
                    exposure.setCountry(matchedCountry);
                    break;
                }
                if (matchedMarketRegime != null) {
                    exposure.setDimension(InvestmentInstrumentExposure.Dimension.MARKET_REGIME);
                    exposure.setMarketRegime(matchedMarketRegime);
                    break;
                }
                if (matchedRegion != null) {
                    exposure.setDimension(InvestmentInstrumentExposure.Dimension.REGION);
                    exposure.setRegion(matchedRegion);
                    break;
                }
                if (looksLikeMarketRegime(bucketKey) && !looksLikeGeographicArea(bucketKey)) {
                    if (suggestedMarketRegimes != null && dto.getBucketName() != null && !dto.getBucketName().isBlank()) {
                        suggestedMarketRegimes.merge(dto.getBucketName().trim(), 1, Integer::sum);
                    }
                    return null;
                }
                if (suggestedCountries != null && dto.getBucketName() != null && !dto.getBucketName().isBlank()) {
                    suggestedCountries.merge(dto.getBucketName().trim(), 1, Integer::sum);
                }
                return null;
            }
            case SECTOR -> {
                InvestmentSectorCatalog sector = sectors.get(bucketKey);
                if (sector == null) {
                    return null;
                }
                exposure.setSector(sector);
            }
            case INDUSTRY -> {
                InvestmentIndustryCatalog industry = industries.get(bucketKey);
                if (industry == null) {
                    return null;
                }
                exposure.setIndustry(industry);
            }
            case MARKET_REGIME -> {
                InvestmentMarketRegimeCatalog marketRegime = marketRegimes.get(bucketKey);
                if (marketRegime == null) {
                    if (suggestedMarketRegimes != null && dto.getBucketName() != null && !dto.getBucketName().isBlank()) {
                        suggestedMarketRegimes.merge(dto.getBucketName().trim(), 1, Integer::sum);
                    }
                    return null;
                }
                exposure.setMarketRegime(marketRegime);
            }
        }

        return exposure;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private boolean looksLikeMarketRegime(String normalizedBucket) {
        if (normalizedBucket == null || normalizedBucket.isBlank()) {
            return false;
        }
        return normalizedBucket.contains("emerg")
                || normalizedBucket.contains("develop")
                || normalizedBucket.contains("desarroll")
                || normalizedBucket.contains("mercado");
    }

    private boolean looksLikeGeographicArea(String normalizedBucket) {
        if (normalizedBucket == null || normalizedBucket.isBlank()) {
            return false;
        }
        return normalizedBucket.contains("asia")
                || normalizedBucket.contains("europa")
                || normalizedBucket.contains("america")
                || normalizedBucket.contains("africa")
                || normalizedBucket.contains("pacific")
                || normalizedBucket.contains("latam")
                || normalizedBucket.contains("nordic")
                || normalizedBucket.contains("global")
                || normalizedBucket.contains("world");
    }
}