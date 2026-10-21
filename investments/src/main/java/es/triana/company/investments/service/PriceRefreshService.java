package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.client.MarketPriceClient;
import es.triana.company.investments.model.api.PriceRefreshResultDTO;
import es.triana.company.investments.model.api.PriceQuotePreviewDTO;
import es.triana.company.investments.model.api.PriceUpdateRequestDTO;
import es.triana.company.investments.model.api.HistoricalPriceUpdateRequestDTO;
import java.time.LocalDate;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentPrice;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentPriceRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class PriceRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(PriceRefreshService.class);

    private final InvestmentInstrumentRepository investmentInstrumentRepository;
    private final InvestmentPriceRepository investmentPriceRepository;
    private final InvestmentRepository investmentRepository;
    private final InvestmentTypeCatalogRepository investmentTypeCatalogRepository;
    private final MarketPriceClient marketPriceClient;
    private final EurConversionService eurConversionService;

    @Value("${investments.prices.auto-refresh-excluded-type-codes:FUND,ETF}")
    private String autoRefreshExcludedTypeCodes;

    public PriceRefreshService(InvestmentInstrumentRepository investmentInstrumentRepository, InvestmentPriceRepository investmentPriceRepository,InvestmentRepository investmentRepository, InvestmentTypeCatalogRepository investmentTypeCatalogRepository, MarketPriceClient marketPriceClient, EurConversionService eurConversionService) {
        this.investmentInstrumentRepository = investmentInstrumentRepository;
        this.investmentPriceRepository = investmentPriceRepository;
        this.investmentRepository = investmentRepository;
        this.investmentTypeCatalogRepository = investmentTypeCatalogRepository;
        this.marketPriceClient = marketPriceClient;
        this.eurConversionService = eurConversionService;
    }

    @Transactional
    public PriceRefreshResultDTO refreshPricesOnDemand(List<PriceUpdateRequestDTO> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new InvestmentValidationException("updates are required");
        }

        LOG.info("Starting on-demand price refresh for {} updates", updates.size());

        int recalculatedPositions = 0;
        List<Long> updatedInstrumentIds = new ArrayList<>();

        for (PriceUpdateRequestDTO update : updates) {
            validateUpdate(update);

            InvestmentInstrument instrument = investmentInstrumentRepository.findById(update.getInstrumentId())
                    .orElseThrow(() -> new InvestmentValidationException("Unknown instrumentId: " + update.getInstrumentId()));

            LocalDateTime asOf = update.getAsOf() != null ? update.getAsOf() : LocalDateTime.now();
            String source = trimOrDefault(update.getSource(), "MANUAL");
            String currency = normalizeCurrency(update.getCurrency(), instrument.getCurrency());

            archivePreviousPrice(instrument, asOf);
            updateCurrentPrice(instrument, update.getPrice(), source, asOf, currency);
            int affectedPositions = recalculateCurrentValues(instrument.getId(), update.getPrice(), currency);
            recalculatedPositions += affectedPositions;
            updatedInstrumentIds.add(instrument.getId());

            LOG.info(
                    "On-demand price updated for instrumentId={} symbol={} price={} source={} affectedPositions={}",
                    instrument.getId(),
                    instrument.getSymbol(),
                    update.getPrice(),
                    source,
                    affectedPositions);
        }

        LOG.info(
                "On-demand price refresh finished. updatedInstruments={} recalculatedPositions={}",
                updatedInstrumentIds.size(),
                recalculatedPositions);

        return PriceRefreshResultDTO.builder()
                .updatedInstruments(updatedInstrumentIds.size())
                .recalculatedPositions(recalculatedPositions)
                .instrumentIds(updatedInstrumentIds)
                .mode("on-demand")
                .build();
    }

            @Transactional(readOnly = true)
            public PriceQuotePreviewDTO previewInstrumentPrice(Long instrumentId) {
            if (instrumentId == null || instrumentId <= 0) {
                throw new InvestmentValidationException("instrumentId is required and must be > 0");
            }

            InvestmentInstrument instrument = investmentInstrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new InvestmentValidationException("Unknown instrumentId: " + instrumentId));
            MarketPriceClient.MarketQuote quote = marketPriceClient.fetchLatestQuote(instrument)
                .orElseThrow(() -> new InvestmentValidationException(
                    "No price quote could be retrieved for " + instrument.getSymbol()));

            return new PriceQuotePreviewDTO(
                instrument.getId(),
                instrument.getSymbol(),
                quote.price(),
                quote.source(),
                normalizeCurrency(quote.currency(), instrument.getCurrency()),
                LocalDateTime.now());
            }

            @Transactional
            public PriceRefreshResultDTO updateHistoricalPrice(HistoricalPriceUpdateRequestDTO request) {
            if (request == null || request.instrumentId() == null || request.instrumentId() <= 0
                || request.date() == null || request.price() == null || request.price().signum() <= 0) {
                throw new InvestmentValidationException("instrumentId, date and a positive price are required");
            }

            InvestmentInstrument instrument = investmentInstrumentRepository.findById(request.instrumentId())
                .orElseThrow(() -> new InvestmentValidationException("Unknown instrumentId: " + request.instrumentId()));
            LocalDateTime dayStart = request.date().atStartOfDay();
            LocalDateTime dayEnd = request.date().plusDays(1).atStartOfDay().minusNanos(1);
            List<InvestmentPrice> dayPrices = investmentPriceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
                instrument.getId(), dayStart, dayEnd);
            InvestmentPrice snapshot = dayPrices.isEmpty()
                ? InvestmentPrice.builder()
                    .instrumentId(instrument.getId())
                    .asOf(request.date().atTime(12, 0))
                    .build()
                : dayPrices.get(dayPrices.size() - 1);

            boolean isCurrentSnapshot = investmentPriceRepository.findFirstByInstrumentIdOrderByAsOfDescIdDesc(instrument.getId())
                .map(latest -> latest.getAsOf().toLocalDate().isBefore(request.date())
                    || (latest.getAsOf().toLocalDate().equals(request.date())
                        && latest.getId().equals(snapshot.getId())))
                .orElse(true);

            snapshot.setInstrumentId(instrument.getId());
            snapshot.setPrice(request.price());
            snapshot.setSource("MANUAL_UI");
            snapshot.setCurrency(normalizeCurrency(instrument.getCurrency(), "EUR"));
            investmentPriceRepository.save(snapshot);

            int recalculatedPositions = 0;
            if (isCurrentSnapshot) {
                instrument.setLastPrice(request.price());
                instrument.setLastPriceSource("MANUAL_UI");
                instrument.setLastPriceAt(snapshot.getAsOf());
                investmentInstrumentRepository.save(instrument);
                recalculatedPositions = recalculateCurrentValues(
                    instrument.getId(), request.price(), snapshot.getCurrency());
            }

            return PriceRefreshResultDTO.builder()
                .updatedInstruments(1)
                .recalculatedPositions(recalculatedPositions)
                .instrumentIds(List.of(instrument.getId()))
                .mode("history-edit")
                .build();
            }

    @Transactional
    public PriceRefreshResultDTO refreshPricesAutomaticallyNow() {
        List<Long> activeInstrumentIds = investmentRepository.findDistinctActiveInstrumentIds();
        List<InvestmentInstrument> allActiveInstruments = activeInstrumentIds.isEmpty()
            ? List.of()
            : investmentInstrumentRepository.findAllById(activeInstrumentIds);
        Set<String> excludedTypeCodes = parseExcludedTypeCodes();
        Map<Long, String> typeCodeById = investmentTypeCatalogRepository.findAll().stream()
                .collect(Collectors.toMap(type -> type.getId(), type -> type.getCode(), (a, b) -> a));
        List<InvestmentInstrument> instruments = allActiveInstruments.stream()
                .filter(instrument -> {
                    String typeCode = typeCodeById.get(instrument.getTypeId());
                    if (typeCode == null) {
                        return true;
                    }
                    return !excludedTypeCodes.contains(typeCode.trim().toUpperCase(Locale.ROOT));
                })
                .toList();
        int skippedByType = allActiveInstruments.size() - instruments.size();

        LOG.info(
            "Starting automatic price refresh for {} active instruments ({} total configured, skippedByType={}, excludedTypes={})",
            instruments.size(),
            investmentInstrumentRepository.count(),
            skippedByType,
            excludedTypeCodes);

        int recalculatedPositions = 0;
        List<Long> updatedInstrumentIds = new ArrayList<>();
        int skippedNoQuote = 0;

        LocalDateTime now = LocalDateTime.now();
        Map<Long, MarketPriceClient.MarketQuote> quotesByInstrumentId = marketPriceClient.fetchLatestQuotes(instruments);

        for (InvestmentInstrument instrument : instruments) {
            try {
            MarketPriceClient.MarketQuote quote = quotesByInstrumentId.get(instrument.getId());
            if (quote == null) {
                    skippedNoQuote++;
                    LOG.debug(
                            "Auto refresh skipped instrumentId={} symbol={} market={} (no quote)",
                            instrument.getId(),
                            instrument.getSymbol(),
                            instrument.getMarket());
                    continue;
                }

                BigDecimal nextPrice = quote.price();
                String source = quote.source();
                String currency = normalizeCurrency(quote.currency(), instrument.getCurrency());

                archivePreviousPrice(instrument, now);
                updateCurrentPrice(instrument, nextPrice, source, now, currency);
                int affectedPositions = recalculateCurrentValues(instrument.getId(), nextPrice, currency);
                recalculatedPositions += affectedPositions;
                updatedInstrumentIds.add(instrument.getId());

                LOG.info(
                        "Auto price updated for instrumentId={} symbol={} price={} source={} affectedPositions={}",
                        instrument.getId(),
                        instrument.getSymbol(),
                        nextPrice,
                        source,
                        affectedPositions);
            } catch (Exception ex) {
                LOG.warn(
                        "Auto refresh failed for instrumentId={} symbol={} market={}. Cause={}",
                        instrument.getId(),
                        instrument.getSymbol(),
                        instrument.getMarket(),
                        ex.getMessage());
            }
        }

        LOG.info(
            "Automatic price refresh finished. updatedInstruments={} recalculatedPositions={} skippedNoQuote={} skippedByType={}",
                updatedInstrumentIds.size(),
                recalculatedPositions,
            skippedNoQuote,
            skippedByType);

        return PriceRefreshResultDTO.builder()
                .updatedInstruments(updatedInstrumentIds.size())
                .recalculatedPositions(recalculatedPositions)
                .instrumentIds(updatedInstrumentIds)
                .mode("auto")
                .build();
    }

    @Scheduled(cron = "${investments.prices.auto-refresh-cron:0 0 */12 * * *}")
    @Transactional
    public void scheduledAutoRefresh() {
        LOG.info("Scheduled price refresh triggered");
        refreshPricesAutomaticallyNow();
    }

    private void validateUpdate(PriceUpdateRequestDTO update) {
        if (update == null) {
            throw new InvestmentValidationException("update item cannot be null");
        }
        if (update.getInstrumentId() == null || update.getInstrumentId() <= 0) {
            throw new InvestmentValidationException("instrumentId is required and must be > 0");
        }
        if (update.getPrice() == null || update.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvestmentValidationException("price is required and must be > 0");
        }
    }

    private void archivePreviousPrice(InvestmentInstrument instrument, LocalDateTime fallbackAsOf) {
        if (instrument.getLastPrice() == null) {
            return;
        }

        InvestmentPrice previousSnapshot = InvestmentPrice.builder()
                .instrumentId(instrument.getId())
                .price(instrument.getLastPrice())
                .source(trimOrDefault(instrument.getLastPriceSource(), "PREVIOUS"))
                .asOf(instrument.getLastPriceAt() != null ? instrument.getLastPriceAt() : fallbackAsOf)
                .currency(instrument.getCurrency())
                .build();

        investmentPriceRepository.save(previousSnapshot);
    }

    private void updateCurrentPrice(InvestmentInstrument instrument, BigDecimal newPrice, String source, LocalDateTime asOf, String currency) {
        InvestmentPrice currentSnapshot = InvestmentPrice.builder()
                .instrumentId(instrument.getId())
                .price(newPrice)
                .source(source)
                .asOf(asOf)
                .currency(currency)
                .build();

        investmentPriceRepository.save(currentSnapshot);

        instrument.setLastPrice(newPrice);
        instrument.setLastPriceSource(source);
        instrument.setLastPriceAt(asOf);
        instrument.setCurrency(currency);
        investmentInstrumentRepository.save(instrument);
    }

    // Position values are stored in EUR (the base currency), whatever the quote currency is.
    private int recalculateCurrentValues(Long instrumentId, BigDecimal price, String priceCurrency) {
        List<Investment> positions = investmentRepository.findByInstrumentId(instrumentId);

        int updated = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Investment position : positions) {
            if (position.getQuantity() == null) {
                continue;
            }

            BigDecimal calculated = eurConversionService
                    .toEur(position.getQuantity().multiply(price), priceCurrency)
                    .setScale(2, RoundingMode.HALF_UP);

            position.setCurrentValueCalculated(calculated);
            position.setCurrency(EurConversionService.EUR);
            position.setUpdatedAt(now);
            updated++;
        }

        if (!positions.isEmpty()) {
            investmentRepository.saveAll(positions);
        }

        return updated;
    }

    private String normalizeCurrency(String requestedCurrency, String fallbackCurrency) {
        String value = trimOrDefault(requestedCurrency, fallbackCurrency);
        if (value == null) {
            return "EUR";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);

        if ("USDT".equals(normalized) || "USDC".equals(normalized) || "BUSD".equals(normalized)
                || "DAI".equals(normalized)) {
            return "USD";
        }

        if (normalized.length() != 3) {
            throw new InvestmentValidationException("currency must have 3 letters");
        }
        return normalized;
    }

    private String trimOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed;
    }

    private Set<String> parseExcludedTypeCodes() {
        if (autoRefreshExcludedTypeCodes == null || autoRefreshExcludedTypeCodes.isBlank()) {
            return Set.of();
        }
        return List.of(autoRefreshExcludedTypeCodes.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
