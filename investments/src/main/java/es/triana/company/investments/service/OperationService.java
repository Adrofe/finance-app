package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.model.api.CreateOperationRequest;
import es.triana.company.investments.model.api.FifoRebuildResultDTO;
import es.triana.company.investments.model.api.OperationDTO;
import es.triana.company.investments.model.api.TaxSummaryDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentOperation;
import es.triana.company.investments.model.db.OperationType;
import es.triana.company.investments.model.db.OperationFifoLot;
import es.triana.company.investments.repository.ExchangeRateRepository;
import es.triana.company.investments.repository.InvestmentOperationRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.OperationFifoLotRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;
import es.triana.company.investments.service.exception.InvalidQuantityException;

@Service
public class OperationService {

    private static final Logger LOG = LoggerFactory.getLogger(OperationService.class);
    private static final String EUR = "EUR";
    private static final int SCALE = 10;

    private final InvestmentOperationRepository operationRepository;
    private final OperationFifoLotRepository fifoLotRepository;
    private final InvestmentRepository investmentRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final es.triana.company.investments.service.mapper.OperationMapper operationMapper;

        public OperationService(InvestmentOperationRepository operationRepository, OperationFifoLotRepository fifoLotRepository, InvestmentRepository investmentRepository, ExchangeRateRepository exchangeRateRepository, es.triana.company.investments.service.mapper.OperationMapper operationMapper) {
                this.operationRepository = operationRepository;
                this.fifoLotRepository = fifoLotRepository;
                this.investmentRepository = investmentRepository;
                this.exchangeRateRepository = exchangeRateRepository;
                this.operationMapper = operationMapper;
        }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public OperationDTO registerOperation(CreateOperationRequest req) {
        // Validate operation quantities before any other work
        validateOperationQuantities(req);

        // Lock the Investment row for update to serialise concurrent position changes
        // (BUY+BUY, BUY+SELL, or SELL+SELL on the same investment).
        Investment investment = findAndLockInvestment(req.getInvestmentId(), req.getTenantId());

        // For SELL: validate FIFO coverage BEFORE any other work so nothing is
        // persisted and no external calls are made if stock is insufficient.
        if (OperationType.SELL.equals(req.getType())) {
            validateFifoCoverage(req.getQuantity(), investment);
        }

        BigDecimal fees = req.getFees() != null ? req.getFees() : BigDecimal.ZERO;
        BigDecimal totalAmount = computeTotalAmount(req.getType(), req.getQuantity(), req.getUnitPrice(), fees);

        BigDecimal eurRate = resolveEurRate(req.getCurrency(), req.getOperationDate());
        BigDecimal totalAmountEur = totalAmount.divide(eurRate, 4, RoundingMode.HALF_UP);

        LocalDateTime now = LocalDateTime.now();
        InvestmentOperation op = operationMapper.toEntity(req, totalAmount, eurRate, totalAmountEur, now);

        op = operationRepository.save(op);
        LOG.info("Registered {} operation id={} investment={} quantity={} totalEur={}",
                op.getType(), op.getId(), op.getInvestmentId(), op.getQuantity(), op.getTotalAmountEur());

        List<OperationFifoLot> lots = new ArrayList<>();
        if (OperationType.SELL.equals(req.getType())) {
            lots = applyFifo(op, investment);
        }

        updateInvestmentPosition(investment, req.getType(), req.getQuantity(), totalAmount);

        return operationMapper.toDto(op, lots);
    }

    public List<OperationDTO> getByInvestment(Long investmentId, Long tenantId) {
        findInvestment(investmentId, tenantId); // validate ownership
        return operationRepository
                .findByInvestmentIdOrderByOperationDateAscIdAsc(investmentId)
                .stream()
                .map(op -> operationMapper.toDto(op, fifoLotRepository.findBySellOperationId(op.getId())))
                .toList();
    }

    public List<OperationDTO> getByTenant(Long tenantId) {
        return operationRepository
                .findByTenantIdOrderByOperationDateDescIdDesc(tenantId)
                .stream()
                .map(op -> operationMapper.toDto(op, fifoLotRepository.findBySellOperationId(op.getId())))
                .toList();
    }

    public TaxSummaryDTO getTaxSummary(Long tenantId, int year) {
        validateTaxSummaryInput(tenantId, year);

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        BigDecimal total = loadTaxSummaryTotal(tenantId, startDate, endDate);
        List<TaxSummaryDTO.ByInstrument> byInstrument = loadTaxSummaryByInstrument(tenantId, startDate, endDate);
        List<TaxSummaryDTO.ByCurrency> byCurrency = loadTaxSummaryByCurrency(tenantId, startDate, endDate);

        return new TaxSummaryDTO(tenantId, year, total, byInstrument, byCurrency);
    }

    private void validateTaxSummaryInput(Long tenantId, int year) {
            if (tenantId == null || tenantId <= 0) {
                    throw new InvestmentValidationException("tenantId is required and must be > 0");
            }
            if (year < 1900 || year > 3000) {
                    throw new InvestmentValidationException("year is required and must be between 1900 and 3000");
            }
    }

    private void validateOperationQuantities(CreateOperationRequest req) {
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidQuantityException(
                    "Operation quantity must be greater than 0, but got: " 
                    + (req.getQuantity() != null ? req.getQuantity() : "null"));
        }

        if (req.getUnitPrice() == null || req.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidQuantityException(
                    "Operation unitPrice must be greater than 0, but got: " 
                    + (req.getUnitPrice() != null ? req.getUnitPrice() : "null"));
        }

        BigDecimal fees = req.getFees() != null ? req.getFees() : BigDecimal.ZERO;
        if (fees.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidQuantityException(
                    "Operation fees must not be negative, but got: " + fees);
        }
    }

    private BigDecimal loadTaxSummaryTotal(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return scaleTaxValue(fifoLotRepository.sumGainLossByTenantAndSellDateBetween(tenantId, startDate, endDate));
    }

    private List<TaxSummaryDTO.ByInstrument> loadTaxSummaryByInstrument(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return fifoLotRepository
                        .sumGainLossByInstrumentAndSellDateBetween(tenantId, startDate, endDate)
                        .stream()
                        .map(this::mapTaxByInstrumentRow)
                        .toList();
    }

    private List<TaxSummaryDTO.ByCurrency> loadTaxSummaryByCurrency(Long tenantId, LocalDate startDate, LocalDate endDate) {
        return fifoLotRepository
                        .sumGainLossByCurrencyAndSellDateBetween(tenantId, startDate, endDate)
                        .stream()
                        .map(this::mapTaxByCurrencyRow)
                        .toList();
    }

    private TaxSummaryDTO.ByInstrument mapTaxByInstrumentRow(Object[] row) {
            return new TaxSummaryDTO.ByInstrument(
                            (Long) row[0],
                            (String) row[1],
                            (String) row[2],
                            (String) row[3],
                            scaleTaxValue((BigDecimal) row[4]));
    }

    private TaxSummaryDTO.ByCurrency mapTaxByCurrencyRow(Object[] row) {
            return new TaxSummaryDTO.ByCurrency(
                            (String) row[0],
                            scaleTaxValue((BigDecimal) row[1]));
    }

    private BigDecimal scaleTaxValue(BigDecimal value) {
            BigDecimal amount = value != null ? value : BigDecimal.ZERO;
            return amount.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Rebuilds FIFO matching from scratch for one instrument+tenant.
     * Existing lots for SELL operations are deleted and recalculated in temporal order.
     */
    @Transactional
    public FifoRebuildResultDTO rebuildFifoForInstrumentTenant(Long instrumentId, Long tenantId) {
        List<InvestmentOperation> operations = findOperationsForFifoRebuild(instrumentId, tenantId);
        if (operations.isEmpty()) {
            return new FifoRebuildResultDTO(instrumentId, tenantId, 0, 0, 0);
        }

        deleteExistingRebuildSellLots(operations);

        RebuildCounters counters = rebuildFifoLots(operations, instrumentId, tenantId);

        LOG.info("Rebuilt FIFO for instrument={} tenant={} operations={} sells={} lots={}",
                instrumentId, tenantId, operations.size(), counters.sellsRebuilt, counters.lotsCreated);

        return new FifoRebuildResultDTO(
                instrumentId,
                tenantId,
                operations.size(),
                counters.sellsRebuilt,
                counters.lotsCreated);
    }

    private List<InvestmentOperation> findOperationsForFifoRebuild(Long instrumentId, Long tenantId) {
        return operationRepository.findByInstrumentAndTenantOrderByOperationDateAscIdAscForUpdate(instrumentId, tenantId);
    }

    private void deleteExistingRebuildSellLots(List<InvestmentOperation> operations) {
        List<Long> sellIds = operations.stream()
                .filter(op -> OperationType.SELL.equals(op.getType()))
                .map(InvestmentOperation::getId)
                .toList();

        if (!sellIds.isEmpty()) {
            fifoLotRepository.deleteBySellOperationIdIn(sellIds);
        }
    }

    private RebuildCounters rebuildFifoLots(List<InvestmentOperation> operations, Long instrumentId, Long tenantId) {
        List<InvestmentOperation> buyQueue = new ArrayList<>();
        Map<Long, BigDecimal> consumedByBuyId = new HashMap<>();
        int lotsCreated = 0;
        int sellsRebuilt = 0;

        for (InvestmentOperation operation : operations) {
            if (OperationType.BUY.equals(operation.getType())) {
                buyQueue.add(operation);
                continue;
            }
            if (!OperationType.SELL.equals(operation.getType())) {
                continue;
            }

            sellsRebuilt++;
            lotsCreated += rebuildSellOperationLots(operation, buyQueue, consumedByBuyId, instrumentId, tenantId);
        }

        return new RebuildCounters(sellsRebuilt, lotsCreated);
    }

    private int rebuildSellOperationLots(InvestmentOperation sellOperation, List<InvestmentOperation> buyQueue, Map<Long, BigDecimal> consumedByBuyId, Long instrumentId, Long tenantId) {

        BigDecimal sellUnitPriceEur = calculateUnitPriceEur(sellOperation);
        BigDecimal remaining = sellOperation.getQuantity();
        int lotsCreated = 0;

        for (InvestmentOperation buyOperation : buyQueue) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal available = availableBuyQuantity(buyOperation, consumedByBuyId);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal lotQty = remaining.min(available);
            OperationFifoLot lot = buildRebuildLot(sellOperation, buyOperation, lotQty, sellUnitPriceEur);
            fifoLotRepository.save(lot);

            consumedByBuyId.merge(buyOperation.getId(), lotQty, BigDecimal::add);
            remaining = remaining.subtract(lotQty);
            lotsCreated++;
        }

        ensureRebuildSellCovered(remaining, sellOperation.getId(), instrumentId, tenantId);
        return lotsCreated;
    }

    private OperationFifoLot buildRebuildLot(InvestmentOperation sellOperation, InvestmentOperation buyOperation, BigDecimal lotQty, BigDecimal sellUnitPriceEur) {
        BigDecimal buyUnitPriceEur = calculateUnitPriceEur(buyOperation);
        BigDecimal gainLoss = sellUnitPriceEur.subtract(buyUnitPriceEur)
                .multiply(lotQty)
                .setScale(4, RoundingMode.HALF_UP);

        return OperationFifoLot.builder()
                .sellOperationId(sellOperation.getId())
                .buyOperationId(buyOperation.getId())
                .quantity(lotQty)
                .buyUnitPriceEur(buyUnitPriceEur)
                .sellUnitPriceEur(sellUnitPriceEur)
                .gainLossEur(gainLoss)
                .build();
    }

    private BigDecimal calculateUnitPriceEur(InvestmentOperation operation) {
        return operation.getUnitPrice().divide(operation.getEurExchangeRate(), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal availableBuyQuantity(InvestmentOperation buyOperation, Map<Long, BigDecimal> consumedByBuyId) {
        BigDecimal alreadyConsumed = consumedByBuyId.getOrDefault(buyOperation.getId(), BigDecimal.ZERO);
        return buyOperation.getQuantity().subtract(alreadyConsumed);
    }

    private void ensureRebuildSellCovered(BigDecimal remaining, Long sellOperationId, Long instrumentId, Long tenantId) {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        throw new InvestmentValidationException(
                "Cannot rebuild FIFO for instrument " + instrumentId
                        + " and tenant " + tenantId
                        + ": SELL operation " + sellOperationId
                        + " is short by " + remaining.stripTrailingZeros().toPlainString() + " units.");
    }

    private static final class RebuildCounters {
        private final int sellsRebuilt;
        private final int lotsCreated;

        private RebuildCounters(int sellsRebuilt, int lotsCreated) {
            this.sellsRebuilt = sellsRebuilt;
            this.lotsCreated = lotsCreated;
        }
    }

    // -------------------------------------------------------------------------
    // FIFO matching
    // -------------------------------------------------------------------------

    /**
     * Dry-run check: verifies that enough unconsumed BUY lots exist to cover the
     * requested sell quantity. Throws InvestmentValidationException if not.
     * Called before any DB write so nothing is persisted on failure.
     */
    private void validateFifoCoverage(BigDecimal sellQty, Investment investment) {
        List<InvestmentOperation> buyLots = findBuyLotsForFifo(investment.getInstrumentId(), investment.getTenantId());
        Map<Long, BigDecimal> consumedByBuyId = loadConsumedByBuyOperationId(buyLots);
        BigDecimal availableQty = calculateAvailableQuantity(buyLots, consumedByBuyId);

        ensureSufficientStock(sellQty, availableQty, investment.getInstrumentId());
    }

    /**
     * Matches a SELL operation against the oldest BUY lots of the same investment
     * (FIFO). For Spain AEAT the criterion is per instrument + tenant across all
     * platforms, so we use findBuysByInstrumentAndTenantFifo.
     */
    private List<OperationFifoLot> applyFifo(InvestmentOperation sell, Investment investment) {
        List<InvestmentOperation> buyLots = findBuyLotsForFifo(investment.getInstrumentId(), sell.getTenantId());
        Map<Long, BigDecimal> consumedByBuyId = loadConsumedByBuyOperationId(buyLots);

        BigDecimal sellUnitPriceEur = calculateUnitPriceEur(sell);
        BigDecimal remaining = sell.getQuantity();
        List<OperationFifoLot> result = new ArrayList<>();

        for (InvestmentOperation buy : buyLots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal available = availableBuyQuantity(buy, consumedByBuyId);
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal lotQty = remaining.min(available);
            OperationFifoLot lot = buildApplyFifoLot(sell, buy, lotQty, sellUnitPriceEur);
            fifoLotRepository.save(lot);

            result.add(lot);
            consumedByBuyId.merge(buy.getId(), lotQty, BigDecimal::add);
            remaining = remaining.subtract(lotQty);

            LOG.info("FIFO lot: sell={} buy={} qty={} gainLossEur={}",
                    sell.getId(), buy.getId(), lotQty, lot.getGainLossEur());
        }

        ensureApplyFifoCovered(remaining, sell.getInvestmentId());
        return result;
    }

    private OperationFifoLot buildApplyFifoLot(InvestmentOperation sell, InvestmentOperation buy, BigDecimal lotQty, BigDecimal sellUnitPriceEur) {
        BigDecimal buyUnitPriceEur = calculateUnitPriceEur(buy);
        BigDecimal gainLoss = sellUnitPriceEur.subtract(buyUnitPriceEur)
                .multiply(lotQty)
                .setScale(4, RoundingMode.HALF_UP);

        return OperationFifoLot.builder()
                .sellOperationId(sell.getId())
                .buyOperationId(buy.getId())
                .quantity(lotQty)
                .buyUnitPriceEur(buyUnitPriceEur)
                .sellUnitPriceEur(sellUnitPriceEur)
                .gainLossEur(gainLoss)
                .build();
    }

    private void ensureApplyFifoCovered(BigDecimal remaining, Long investmentId) {
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        throw new InvestmentValidationException(
                "Insufficient stock to sell: " + remaining.stripTrailingZeros().toPlainString()
                        + " units of investment " + investmentId + " have no matching BUY lots.");
    }

    private List<InvestmentOperation> findBuyLotsForFifo(Long instrumentId, Long tenantId) {
        // findBuysByInstrumentAndTenantFifo acquires PESSIMISTIC_WRITE — concurrent
        // SELLs on the same instrument+tenant will block here until this tx commits.
        return operationRepository.findBuysByInstrumentAndTenantFifo(instrumentId, tenantId);
    }

    private Map<Long, BigDecimal> loadConsumedByBuyOperationId(List<InvestmentOperation> buyLots) {
        List<Long> buyIds = buyLots.stream()
                        .map(InvestmentOperation::getId)
                        .toList();

        if (buyIds.isEmpty()) {
                return new HashMap<>();
        }

        Map<Long, BigDecimal> consumedByBuyId = new HashMap<>();
        for (OperationFifoLot lot : fifoLotRepository.findByBuyOperationIdIn(buyIds)) {
                consumedByBuyId.merge(lot.getBuyOperationId(), lot.getQuantity(), BigDecimal::add);
        }
        return consumedByBuyId;
    }

    private BigDecimal calculateAvailableQuantity(List<InvestmentOperation> buyLots, Map<Long, BigDecimal> consumedByBuyId) {
        return buyLots.stream()
                    .map(buy -> buy.getQuantity().subtract(consumedByBuyId.getOrDefault(buy.getId(), BigDecimal.ZERO)))
                    .filter(available -> available.compareTo(BigDecimal.ZERO) > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void ensureSufficientStock(BigDecimal requestedQty, BigDecimal availableQty, Long instrumentId) {
        if (availableQty.compareTo(requestedQty) >= 0) {
            return;
        }

        throw new InvestmentValidationException(
                        "Insufficient stock: trying to sell " + requestedQty.stripTrailingZeros().toPlainString()
                                        + " units but only " + availableQty.stripTrailingZeros().toPlainString()
                                        + " are available for instrument " + instrumentId + ".");
    }

    // -------------------------------------------------------------------------
    // Position update
    // -------------------------------------------------------------------------

    private void updateInvestmentPosition(Investment inv, OperationType type, BigDecimal quantity, BigDecimal totalAmount) {
        if (OperationType.BUY.equals(type)) {
            BigDecimal newQty = (inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO).add(quantity);
            BigDecimal newInvested = (inv.getInvestedAmount() != null ? inv.getInvestedAmount() : BigDecimal.ZERO)
                    .add(totalAmount).setScale(2, RoundingMode.HALF_UP);
            inv.setQuantity(newQty);
            inv.setInvestedAmount(newInvested);
        } else {
            BigDecimal newQty = (inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO).subtract(quantity);
            if (newQty.compareTo(BigDecimal.ZERO) < 0) newQty = BigDecimal.ZERO;
            inv.setQuantity(newQty);
            // invested_amount is not reduced on sell — it reflects total capital deployed
        }
        inv.setUpdatedAt(LocalDateTime.now());
        investmentRepository.save(inv);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BigDecimal computeTotalAmount(OperationType type, BigDecimal qty, BigDecimal unitPrice, BigDecimal fees) {
        BigDecimal gross = qty.multiply(unitPrice);
        return OperationType.BUY.equals(type)
                        ? gross.add(fees).setScale(4, RoundingMode.HALF_UP)
                        : gross.subtract(fees).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Resolves the EUR exchange rate for the given currency on the given date.
     * If the currency is EUR, returns 1. If not found in the DB, logs a warning
     * and falls back to the most recent available rate.
     */
    private BigDecimal resolveEurRate(String currency, java.time.LocalDate date) {
        if (isEur(currency)) {
            return BigDecimal.ONE;
        }

        String curr = currency.toUpperCase();
        Optional<BigDecimal> exactRateOpt = findExactEurRate(curr, date);
        if (exactRateOpt.isPresent()) {
            return exactRateOpt.get();
        } else {
            LOG.warn("No exact ECB rate for EUR/{} on {}. Attempting to find most recent available rate.", currency, date);
            return findMostRecentEurRateOrThrow(curr);
        }
    }

    private boolean isEur(String currency) {
            return EUR.equalsIgnoreCase(currency);
    }

    private java.util.Optional<BigDecimal> findExactEurRate(String currency, java.time.LocalDate date) {
            return exchangeRateRepository.findByFromCurrencyAndToCurrencyAndAsOf(EUR, currency, date)
                            .map(r -> r.getRate());
    }

    private BigDecimal findMostRecentEurRateOrThrow(String currency) {
            return exchangeRateRepository
                            .findFirstByFromCurrencyAndToCurrencyOrderByAsOfDesc(EUR, currency)
                            .map(r -> r.getRate())
                            .orElseThrow(() -> new InvestmentValidationException(
                                            "No EUR/" + currency + " exchange rate available. Populate exchange_rates first."));
    }

    /** Read-only lookup — no lock. Used by getByInvestment / getByTenant. */
    private Investment findInvestment(Long investmentId, Long tenantId) {
        return investmentRepository.findByIdAndTenantId(investmentId, tenantId)
                .orElseThrow(() -> new InvestmentValidationException(
                        "Investment " + investmentId + " not found for tenant " + tenantId));
    }

    /** Write lookup — acquires PESSIMISTIC_WRITE lock. Used by registerOperation. */
    private Investment findAndLockInvestment(Long investmentId, Long tenantId) {
        return investmentRepository.findByIdAndTenantIdForUpdate(investmentId, tenantId)
                .orElseThrow(() -> new InvestmentValidationException(
                        "Investment " + investmentId + " not found for tenant " + tenantId));
    }

}
