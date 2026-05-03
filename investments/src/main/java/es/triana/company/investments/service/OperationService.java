package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

@Service
public class OperationService {

    private static final Logger LOG = LoggerFactory.getLogger(OperationService.class);
    private static final String EUR = "EUR";
    private static final int SCALE = 10;

    private final InvestmentOperationRepository operationRepository;
    private final OperationFifoLotRepository fifoLotRepository;
    private final InvestmentRepository investmentRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public OperationService(InvestmentOperationRepository operationRepository, OperationFifoLotRepository fifoLotRepository, InvestmentRepository investmentRepository, ExchangeRateRepository exchangeRateRepository) {
        this.operationRepository = operationRepository;
        this.fifoLotRepository = fifoLotRepository;
        this.investmentRepository = investmentRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Transactional
    public OperationDTO registerOperation(CreateOperationRequest req) {
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
        InvestmentOperation op = InvestmentOperation.builder()
                .investmentId(req.getInvestmentId())
                .tenantId(req.getTenantId())
                .type(req.getType())
                .operationDate(req.getOperationDate())
                .quantity(req.getQuantity())
                .unitPrice(req.getUnitPrice())
                .fees(fees)
                .totalAmount(totalAmount)
                .currency(req.getCurrency())
                .eurExchangeRate(eurRate)
                .totalAmountEur(totalAmountEur)
                .notes(req.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .build();

        op = operationRepository.save(op);
        LOG.info("Registered {} operation id={} investment={} quantity={} totalEur={}",
                op.getType(), op.getId(), op.getInvestmentId(), op.getQuantity(), op.getTotalAmountEur());

        List<OperationFifoLot> lots = new ArrayList<>();
        if (OperationType.SELL.equals(req.getType())) {
            lots = applyFifo(op, investment);
        }

        updateInvestmentPosition(investment, req.getType(), req.getQuantity(), totalAmount);

        return toDTO(op, lots);
    }

    public List<OperationDTO> getByInvestment(Long investmentId, Long tenantId) {
        findInvestment(investmentId, tenantId); // validate ownership
        return operationRepository
                .findByInvestmentIdOrderByOperationDateAscIdAsc(investmentId)
                .stream()
                .map(op -> toDTO(op, fifoLotRepository.findBySellOperationId(op.getId())))
                .toList();
    }

    public List<OperationDTO> getByTenant(Long tenantId) {
        return operationRepository
                .findByTenantIdOrderByOperationDateDescIdDesc(tenantId)
                .stream()
                .map(op -> toDTO(op, fifoLotRepository.findBySellOperationId(op.getId())))
                .toList();
    }

    public TaxSummaryDTO getTaxSummary(Long tenantId, int year) {
        if (tenantId == null || tenantId <= 0) {
            throw new InvestmentValidationException("tenantId is required and must be > 0");
        }
        if (year < 1900 || year > 3000) {
            throw new InvestmentValidationException("year is required and must be between 1900 and 3000");
        }

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        BigDecimal total = fifoLotRepository
                .sumGainLossByTenantAndSellDateBetween(tenantId, startDate, endDate)
                .setScale(4, RoundingMode.HALF_UP);

        List<TaxSummaryDTO.ByInstrument> byInstrument = fifoLotRepository
                .sumGainLossByInstrumentAndSellDateBetween(tenantId, startDate, endDate)
                .stream()
                .map(row -> new TaxSummaryDTO.ByInstrument(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        ((BigDecimal) row[4]).setScale(4, RoundingMode.HALF_UP)))
                .toList();

        List<TaxSummaryDTO.ByCurrency> byCurrency = fifoLotRepository
                .sumGainLossByCurrencyAndSellDateBetween(tenantId, startDate, endDate)
                .stream()
                .map(row -> new TaxSummaryDTO.ByCurrency(
                        (String) row[0],
                        ((BigDecimal) row[1]).setScale(4, RoundingMode.HALF_UP)))
                .toList();

        return new TaxSummaryDTO(tenantId, year, total, byInstrument, byCurrency);
    }

        /**
         * Rebuilds FIFO matching from scratch for one instrument+tenant.
         * Existing lots for SELL operations are deleted and recalculated in temporal order.
         */
        @Transactional
        public FifoRebuildResultDTO rebuildFifoForInstrumentTenant(Long instrumentId, Long tenantId) {
                List<InvestmentOperation> operations = operationRepository
                                .findByInstrumentAndTenantOrderByOperationDateAscIdAscForUpdate(instrumentId, tenantId);

                if (operations.isEmpty()) {
                        return new FifoRebuildResultDTO(instrumentId, tenantId, 0, 0, 0);
                }

                List<Long> sellIds = operations.stream()
                                .filter(op -> OperationType.SELL.equals(op.getType()))
                                .map(InvestmentOperation::getId)
                                .toList();
                if (!sellIds.isEmpty()) {
                        fifoLotRepository.deleteBySellOperationIdIn(sellIds);
                }

                List<InvestmentOperation> buyQueue = new ArrayList<>();
                Map<Long, BigDecimal> consumed = new HashMap<>();
                int lotsCreated = 0;
                int sellsRebuilt = 0;

                for (InvestmentOperation op : operations) {
                        if (OperationType.BUY.equals(op.getType())) {
                                buyQueue.add(op);
                                continue;
                        }

                        if (!OperationType.SELL.equals(op.getType())) {
                                continue;
                        }

                        sellsRebuilt++;
                        BigDecimal sellUnitPriceEur = op.getUnitPrice()
                                        .divide(op.getEurExchangeRate(), SCALE, RoundingMode.HALF_UP);

                        BigDecimal remaining = op.getQuantity();
                        for (InvestmentOperation buy : buyQueue) {
                                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                                        break;
                                }

                                BigDecimal alreadyConsumed = consumed.getOrDefault(buy.getId(), BigDecimal.ZERO);
                                BigDecimal available = buy.getQuantity().subtract(alreadyConsumed);
                                if (available.compareTo(BigDecimal.ZERO) <= 0) {
                                        continue;
                                }

                                BigDecimal lotQty = remaining.min(available);
                                BigDecimal buyUnitPriceEur = buy.getUnitPrice()
                                                .divide(buy.getEurExchangeRate(), SCALE, RoundingMode.HALF_UP);
                                BigDecimal gainLoss = sellUnitPriceEur.subtract(buyUnitPriceEur)
                                                .multiply(lotQty)
                                                .setScale(4, RoundingMode.HALF_UP);

                                OperationFifoLot lot = OperationFifoLot.builder()
                                                .sellOperationId(op.getId())
                                                .buyOperationId(buy.getId())
                                                .quantity(lotQty)
                                                .buyUnitPriceEur(buyUnitPriceEur)
                                                .sellUnitPriceEur(sellUnitPriceEur)
                                                .gainLossEur(gainLoss)
                                                .build();

                                fifoLotRepository.save(lot);
                                consumed.merge(buy.getId(), lotQty, BigDecimal::add);
                                remaining = remaining.subtract(lotQty);
                                lotsCreated++;
                        }

                        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                                throw new InvestmentValidationException(
                                                "Cannot rebuild FIFO for instrument " + instrumentId
                                                + " and tenant " + tenantId
                                                + ": SELL operation " + op.getId()
                                                + " is short by " + remaining.stripTrailingZeros().toPlainString() + " units.");
                        }
                }

                LOG.info("Rebuilt FIFO for instrument={} tenant={} operations={} sells={} lots={}",
                                instrumentId, tenantId, operations.size(), sellsRebuilt, lotsCreated);

                return new FifoRebuildResultDTO(instrumentId, tenantId, operations.size(), sellsRebuilt, lotsCreated);
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
        // findBuysByInstrumentAndTenantFifo acquires PESSIMISTIC_WRITE — concurrent
        // SELLs on the same instrument+tenant will block here until this tx commits.
        List<InvestmentOperation> buyLots = operationRepository
                .findBuysByInstrumentAndTenantFifo(investment.getInstrumentId(), investment.getTenantId());

        List<Long> buyIds = buyLots.stream().map(InvestmentOperation::getId).toList();
        Map<Long, BigDecimal> consumed = new HashMap<>();
        for (OperationFifoLot lot : fifoLotRepository.findByBuyOperationIdIn(buyIds)) {
            consumed.merge(lot.getBuyOperationId(), lot.getQuantity(), BigDecimal::add);
        }

        BigDecimal available = buyLots.stream()
                .map(b -> b.getQuantity().subtract(consumed.getOrDefault(b.getId(), BigDecimal.ZERO)))
                .filter(a -> a.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (available.compareTo(sellQty) < 0) {
            throw new InvestmentValidationException(
                    "Insufficient stock: trying to sell " + sellQty.stripTrailingZeros().toPlainString()
                    + " units but only " + available.stripTrailingZeros().toPlainString()
                    + " are available for instrument " + investment.getInstrumentId() + ".");
        }
    }

    /**
     * Matches a SELL operation against the oldest BUY lots of the same investment
     * (FIFO). For Spain AEAT the criterion is per instrument + tenant across all
     * platforms, so we use findBuysByInstrumentAndTenantFifo.
     */
    private List<OperationFifoLot> applyFifo(InvestmentOperation sell, Investment investment) {
        List<InvestmentOperation> buyLots = operationRepository
                .findBuysByInstrumentAndTenantFifo(investment.getInstrumentId(), sell.getTenantId());

        // Compute already-consumed quantities per buy lot from previous sells.
        // Use a scoped query instead of findAll() — BUY rows are already locked.
        List<Long> buyIds = buyLots.stream().map(InvestmentOperation::getId).toList();
        Map<Long, BigDecimal> consumed = new HashMap<>();
        for (OperationFifoLot lot : fifoLotRepository.findByBuyOperationIdIn(buyIds)) {
            consumed.merge(lot.getBuyOperationId(), lot.getQuantity(), BigDecimal::add);
        }

        BigDecimal sellUnitPriceEur = sell.getUnitPrice()
                .divide(sell.getEurExchangeRate(), SCALE, RoundingMode.HALF_UP);

        BigDecimal remaining = sell.getQuantity();
        List<OperationFifoLot> result = new ArrayList<>();

        for (InvestmentOperation buy : buyLots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal alreadyConsumed = consumed.getOrDefault(buy.getId(), BigDecimal.ZERO);
            BigDecimal available = buy.getQuantity().subtract(alreadyConsumed);
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal lotQty = remaining.min(available);
            BigDecimal buyUnitPriceEur = buy.getUnitPrice()
                    .divide(buy.getEurExchangeRate(), SCALE, RoundingMode.HALF_UP);
            BigDecimal gainLoss = sellUnitPriceEur.subtract(buyUnitPriceEur)
                    .multiply(lotQty)
                    .setScale(4, RoundingMode.HALF_UP);

            OperationFifoLot lot = OperationFifoLot.builder()
                    .sellOperationId(sell.getId())
                    .buyOperationId(buy.getId())
                    .quantity(lotQty)
                    .buyUnitPriceEur(buyUnitPriceEur)
                    .sellUnitPriceEur(sellUnitPriceEur)
                    .gainLossEur(gainLoss)
                    .build();

            fifoLotRepository.save(lot);
            result.add(lot);
            remaining = remaining.subtract(lotQty);

            LOG.info("FIFO lot: sell={} buy={} qty={} gainLossEur={}",
                    sell.getId(), buy.getId(), lotQty, gainLoss);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw new InvestmentValidationException(
                    "Insufficient stock to sell: " + remaining.stripTrailingZeros().toPlainString()
                    + " units of investment " + sell.getInvestmentId() + " have no matching BUY lots.");
        }

        return result;
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
        if (EUR.equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        // Try exact date first
        return exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndAsOf(EUR, currency.toUpperCase(), date)
                .map(r -> r.getRate())
                .orElseGet(() -> {
                    LOG.warn("No ECB rate for EUR/{} on {}. Falling back to most recent available.", currency, date);
                    return exchangeRateRepository
                            .findFirstByFromCurrencyAndToCurrencyOrderByAsOfDesc(EUR, currency.toUpperCase())
                            .map(r -> r.getRate())
                            .orElseThrow(() -> new InvestmentValidationException(
                                    "No EUR/" + currency + " exchange rate available. Populate exchange_rates first."));
                });
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

    private OperationDTO toDTO(InvestmentOperation op, List<OperationFifoLot> lots) {
        List<OperationDTO.FifoLotDTO> lotDTOs = lots.stream()
                .map(l -> new OperationDTO.FifoLotDTO(
                        l.getBuyOperationId(),
                        l.getQuantity(),
                        l.getBuyUnitPriceEur(),
                        l.getSellUnitPriceEur(),
                        l.getGainLossEur()))
                .toList();

        return new OperationDTO(
                op.getId(),
                op.getInvestmentId(),
                op.getTenantId(),
                op.getType(),
                op.getOperationDate(),
                op.getQuantity(),
                op.getUnitPrice(),
                op.getFees(),
                op.getTotalAmount(),
                op.getCurrency(),
                op.getEurExchangeRate(),
                op.getTotalAmountEur(),
                op.getNotes(),
                op.getCreatedAt(),
                lotDTOs);
    }
}
