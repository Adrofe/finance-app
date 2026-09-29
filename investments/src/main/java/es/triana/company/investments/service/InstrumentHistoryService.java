package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.model.api.InstrumentHistoryDTO;
import es.triana.company.investments.model.db.ExchangeRate;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentOperation;
import es.triana.company.investments.model.db.InvestmentPrice;
import es.triana.company.investments.model.db.OperationType;
import es.triana.company.investments.repository.ExchangeRateRepository;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentOperationRepository;
import es.triana.company.investments.repository.InvestmentPriceRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@Service
public class InstrumentHistoryService {

    private static final String EUR = "EUR";
    private static final int SCALE = 10;

    private final InvestmentInstrumentRepository instrumentRepository;
    private final InvestmentPriceRepository priceRepository;
    private final InvestmentOperationRepository operationRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public InstrumentHistoryService(
            InvestmentInstrumentRepository instrumentRepository,
            InvestmentPriceRepository priceRepository,
            InvestmentOperationRepository operationRepository,
            ExchangeRateRepository exchangeRateRepository) {
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
        this.operationRepository = operationRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Transactional(readOnly = true)
    public InstrumentHistoryDTO getHistory(Long tenantId, Long instrumentId, LocalDate from, LocalDate to) {
        if (tenantId == null) {
            throw new InvestmentValidationException("Tenant id is required");
        }
        InvestmentInstrument instrument = instrumentRepository.findById(instrumentId)
                .orElseThrow(() -> new InvestmentValidationException("Unknown instrumentId: " + instrumentId));
        List<InvestmentOperation> operations = operationRepository
                .findByInstrumentAndTenantOrderByOperationDateAscIdAsc(instrumentId, tenantId);

        LocalDate startDate = from != null ? from : getFirstAvailableDate(instrumentId, operations).orElse(LocalDate.now());
        LocalDate endDate = to != null ? to : LocalDate.now();
        if (startDate.isAfter(endDate)) {
            throw new InvestmentValidationException("from date must not be after to date");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay().minusNanos(1);
        List<InvestmentPrice> prices = new ArrayList<>();
        priceRepository.findFirstByInstrumentIdAndAsOfBeforeOrderByAsOfDescIdDesc(instrumentId, start)
                .ifPresent(prices::add);
        prices.addAll(priceRepository.findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(instrumentId, start, end));

        TreeMap<LocalDate, InvestmentPrice> dailyPrices = new TreeMap<>();
        for (InvestmentPrice price : prices) {
            dailyPrices.merge(price.getAsOf().toLocalDate(), price,
                    (left, right) -> comparePriceTimestamp(left, right) >= 0 ? left : right);
        }

        Map<String, TreeMap<LocalDate, BigDecimal>> ratesByCurrency = loadHistoricalRates(
                dailyPrices.values(), instrument.getCurrency(), startDate, endDate);
        List<InstrumentHistoryDTO.Point> points = buildPoints(
                dailyPrices, operations, ratesByCurrency, instrument.getCurrency(), startDate);

        return new InstrumentHistoryDTO(
                instrument.getId(), instrument.getSymbol(), instrument.getName(), instrument.getCurrency(), points);
    }

    private Optional<LocalDate> getFirstAvailableDate(Long instrumentId, List<InvestmentOperation> operations) {
        Optional<LocalDate> firstPrice = priceRepository.findFirstByInstrumentIdOrderByAsOfAsc(instrumentId)
                .map(price -> price.getAsOf().toLocalDate());
        Optional<LocalDate> firstOperation = operations.stream()
                .map(InvestmentOperation::getOperationDate)
                .min(Comparator.naturalOrder());
        if (firstPrice.isEmpty()) return firstOperation;
        if (firstOperation.isEmpty()) return firstPrice;
        return Optional.of(firstPrice.get().isBefore(firstOperation.get()) ? firstPrice.get() : firstOperation.get());
    }

    private int comparePriceTimestamp(InvestmentPrice left, InvestmentPrice right) {
        int byTimestamp = left.getAsOf().compareTo(right.getAsOf());
        if (byTimestamp != 0) return byTimestamp;
        return Comparator.nullsFirst(Long::compareTo).compare(left.getId(), right.getId());
    }

    private Map<String, TreeMap<LocalDate, BigDecimal>> loadHistoricalRates(
            Iterable<InvestmentPrice> prices, String instrumentCurrency, LocalDate from, LocalDate to) {
        Map<String, TreeMap<LocalDate, BigDecimal>> rates = new HashMap<>();
        addCurrencyRates(rates, instrumentCurrency, from, to);
        for (InvestmentPrice price : prices) {
            addCurrencyRates(rates, normalizeCurrency(price.getCurrency(), instrumentCurrency), from, to);
        }
        return rates;
    }

    private void addCurrencyRates(
            Map<String, TreeMap<LocalDate, BigDecimal>> rates, String currency, LocalDate from, LocalDate to) {
        String normalized = normalizeCurrency(currency, EUR);
        if (EUR.equals(normalized) || rates.containsKey(normalized)) return;

        TreeMap<LocalDate, BigDecimal> byDate = new TreeMap<>();
        exchangeRateRepository.findFirstByFromCurrencyAndToCurrencyAndAsOfLessThanEqualOrderByAsOfDesc(
                EUR, normalized, from).ifPresent(rate -> byDate.put(rate.getAsOf(), rate.getRate()));
        exchangeRateRepository.findByFromCurrencyAndToCurrencyAndAsOfBetweenOrderByAsOfAsc(
                EUR, normalized, from, to).forEach(rate -> byDate.put(rate.getAsOf(), rate.getRate()));
        rates.put(normalized, byDate);
    }

    private List<InstrumentHistoryDTO.Point> buildPoints(
            TreeMap<LocalDate, InvestmentPrice> dailyPrices,
            List<InvestmentOperation> operations,
            Map<String, TreeMap<LocalDate, BigDecimal>> ratesByCurrency,
            String instrumentCurrency,
            LocalDate from) {
        List<InstrumentHistoryDTO.Point> points = new ArrayList<>();
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal investedEur = BigDecimal.ZERO;
        int operationIndex = 0;
        boolean hasPositionHistory = false;

        for (Map.Entry<LocalDate, InvestmentPrice> entry : dailyPrices.entrySet()) {
            LocalDate date = entry.getKey();
            if (date.isBefore(from)) continue;

            while (operationIndex < operations.size()
                    && !operations.get(operationIndex).getOperationDate().isAfter(date)) {
                InvestmentOperation operation = operations.get(operationIndex++);
                hasPositionHistory = true;
                if (OperationType.BUY.equals(operation.getType())) {
                    quantity = quantity.add(operation.getQuantity());
                    investedEur = investedEur.add(operation.getTotalAmountEur());
                } else if (OperationType.SELL.equals(operation.getType())) {
                    if (quantity.signum() > 0) {
                        BigDecimal reductionRatio = operation.getQuantity().divide(quantity, SCALE, RoundingMode.HALF_UP);
                        investedEur = investedEur.subtract(investedEur.multiply(reductionRatio)).max(BigDecimal.ZERO);
                    }
                    quantity = quantity.subtract(operation.getQuantity()).max(BigDecimal.ZERO);
                }
            }

            InvestmentPrice price = entry.getValue();
            String currency = normalizeCurrency(price.getCurrency(), instrumentCurrency);
            BigDecimal valueEur = convertPriceToEur(price.getPrice(), currency, date, ratesByCurrency);
            if (valueEur != null) {
                valueEur = valueEur.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            }

            points.add(new InstrumentHistoryDTO.Point(
                    date,
                    price.getPrice(),
                    hasPositionHistory ? quantity : null,
                    hasPositionHistory ? investedEur.setScale(2, RoundingMode.HALF_UP) : null,
                    hasPositionHistory ? valueEur : null));
        }
        return points;
    }

    private BigDecimal convertPriceToEur(
            BigDecimal price,
            String currency,
            LocalDate date,
            Map<String, TreeMap<LocalDate, BigDecimal>> ratesByCurrency) {
        if (EUR.equals(currency)) return price;
        TreeMap<LocalDate, BigDecimal> rates = ratesByCurrency.get(currency);
        Map.Entry<LocalDate, BigDecimal> applicableRate = rates == null ? null : rates.floorEntry(date);
        if (applicableRate == null || applicableRate.getValue().signum() == 0) return null;
        return price.divide(applicableRate.getValue(), SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency, String fallback) {
        if (currency == null || currency.isBlank()) return fallback == null ? EUR : fallback.toUpperCase(Locale.ROOT);
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}