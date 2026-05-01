package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.model.api.PriceRefreshResultDTO;
import es.triana.company.investments.model.api.PriceUpdateRequestDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentPrice;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentPriceRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class PriceRefreshService {

    private static final Logger LOG = LoggerFactory.getLogger(PriceRefreshService.class);

    private final InvestmentInstrumentRepository investmentInstrumentRepository;
    private final InvestmentPriceRepository investmentPriceRepository;
    private final InvestmentRepository investmentRepository;
    private final MarketPriceClient marketPriceClient;

    public PriceRefreshService(
            InvestmentInstrumentRepository investmentInstrumentRepository,
            InvestmentPriceRepository investmentPriceRepository,
            InvestmentRepository investmentRepository,
            MarketPriceClient marketPriceClient) {
        this.investmentInstrumentRepository = investmentInstrumentRepository;
        this.investmentPriceRepository = investmentPriceRepository;
        this.investmentRepository = investmentRepository;
        this.marketPriceClient = marketPriceClient;
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
                    .orElseThrow(() -> new InvestmentValidationException(
                            "Unknown instrumentId: " + update.getInstrumentId()));

            LocalDateTime asOf = update.getAsOf() != null ? update.getAsOf() : LocalDateTime.now();
            String source = trimOrDefault(update.getSource(), "MANUAL");
            String currency = normalizeCurrency(update.getCurrency(), instrument.getCurrency());

            archivePreviousPrice(instrument, asOf);
            updateCurrentPrice(instrument, update.getPrice(), source, asOf, currency);
            int affectedPositions = recalculateCurrentValues(instrument.getId(), update.getPrice());
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

    @Transactional
    public PriceRefreshResultDTO refreshPricesAutomaticallyNow() {
        List<InvestmentInstrument> instruments = investmentInstrumentRepository.findAll();
        LOG.info("Starting automatic price refresh for {} instruments", instruments.size());

        int recalculatedPositions = 0;
        List<Long> updatedInstrumentIds = new ArrayList<>();
        int skippedNoQuote = 0;

        LocalDateTime now = LocalDateTime.now();

        for (InvestmentInstrument instrument : instruments) {
            try {
                Optional<MarketPriceClient.MarketQuote> fetched = marketPriceClient.fetchLatestQuote(instrument);
                if (fetched.isEmpty()) {
                    skippedNoQuote++;
                    LOG.debug(
                            "Auto refresh skipped instrumentId={} symbol={} market={} (no quote)",
                            instrument.getId(),
                            instrument.getSymbol(),
                            instrument.getMarket());
                    continue;
                }

                MarketPriceClient.MarketQuote quote = fetched.get();
                BigDecimal nextPrice = quote.price();
                String source = quote.source();
                String currency = normalizeCurrency(quote.currency(), instrument.getCurrency());

                archivePreviousPrice(instrument, now);
                updateCurrentPrice(instrument, nextPrice, source, now, currency);
                int affectedPositions = recalculateCurrentValues(instrument.getId(), nextPrice);
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
                "Automatic price refresh finished. updatedInstruments={} recalculatedPositions={} skippedNoQuote={}",
                updatedInstrumentIds.size(),
                recalculatedPositions,
                skippedNoQuote);

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

    private void updateCurrentPrice(
            InvestmentInstrument instrument,
            BigDecimal newPrice,
            String source,
            LocalDateTime asOf,
            String currency) {
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

    private int recalculateCurrentValues(Long instrumentId, BigDecimal price) {
        List<Investment> positions = investmentRepository.findByInstrumentId(instrumentId);

        int updated = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Investment position : positions) {
            if (position.getQuantity() == null) {
                continue;
            }

            BigDecimal calculated = position.getQuantity()
                    .multiply(price)
                    .setScale(2, RoundingMode.HALF_UP);

            position.setCurrentValueCalculated(calculated);
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
}
