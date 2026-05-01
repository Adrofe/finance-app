package es.triana.company.investments.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.triana.company.investments.model.api.CreateOperationRequest;
import es.triana.company.investments.model.api.OperationDTO;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentOperation;
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

    public OperationService(InvestmentOperationRepository operationRepository,
                            OperationFifoLotRepository fifoLotRepository,
                            InvestmentRepository investmentRepository,
                            ExchangeRateRepository exchangeRateRepository) {
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
        Investment investment = findInvestment(req.investmentId(), req.tenantId());

        BigDecimal fees = req.fees() != null ? req.fees() : BigDecimal.ZERO;
        BigDecimal totalAmount = computeTotalAmount(req.type(), req.quantity(), req.unitPrice(), fees);

        BigDecimal eurRate = resolveEurRate(req.currency(), req.operationDate());
        BigDecimal totalAmountEur = totalAmount.divide(eurRate, 4, RoundingMode.HALF_UP);

        LocalDateTime now = LocalDateTime.now();
        InvestmentOperation op = InvestmentOperation.builder()
                .investmentId(req.investmentId())
                .tenantId(req.tenantId())
                .type(req.type())
                .operationDate(req.operationDate())
                .quantity(req.quantity())
                .unitPrice(req.unitPrice())
                .fees(fees)
                .totalAmount(totalAmount)
                .currency(req.currency())
                .eurExchangeRate(eurRate)
                .totalAmountEur(totalAmountEur)
                .notes(req.notes())
                .createdAt(now)
                .updatedAt(now)
                .build();

        op = operationRepository.save(op);
        LOG.info("Registered {} operation id={} investment={} quantity={} totalEur={}",
                op.getType(), op.getId(), op.getInvestmentId(), op.getQuantity(), op.getTotalAmountEur());

        List<OperationFifoLot> lots = new ArrayList<>();
        if ("SELL".equals(req.type())) {
            lots = applyFifo(op, investment);
        }

        updateInvestmentPosition(investment, req.type(), req.quantity(), totalAmount);

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

    // -------------------------------------------------------------------------
    // FIFO matching
    // -------------------------------------------------------------------------

    /**
     * Matches a SELL operation against the oldest BUY lots of the same investment
     * (FIFO). For Spain AEAT the criterion is per instrument + tenant across all
     * platforms, so we use findBuysByInstrumentAndTenantFifo.
     */
    private List<OperationFifoLot> applyFifo(InvestmentOperation sell, Investment investment) {
        List<InvestmentOperation> buyLots = operationRepository
                .findBuysByInstrumentAndTenantFifo(investment.getInstrumentId(), sell.getTenantId());

        // Compute already-consumed quantities per buy lot from previous sells
        List<OperationFifoLot> allExistingLots = fifoLotRepository.findAll(); // small table, acceptable
        java.util.Map<Long, BigDecimal> consumed = new java.util.HashMap<>();
        for (OperationFifoLot lot : allExistingLots) {
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
            LOG.warn("SELL operation id={} has {} units without matching BUY lots (position may be partially imported)",
                    sell.getId(), remaining);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Position update
    // -------------------------------------------------------------------------

    private void updateInvestmentPosition(Investment inv, String type, BigDecimal quantity, BigDecimal totalAmount) {
        if ("BUY".equals(type)) {
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

    private BigDecimal computeTotalAmount(String type, BigDecimal qty, BigDecimal unitPrice, BigDecimal fees) {
        BigDecimal gross = qty.multiply(unitPrice);
        return "BUY".equals(type)
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

    private Investment findInvestment(Long investmentId, Long tenantId) {
        return investmentRepository.findByIdAndTenantId(investmentId, tenantId)
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
