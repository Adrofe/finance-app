package es.triana.company.investments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import es.triana.company.investments.model.api.CreateOperationRequest;
import es.triana.company.investments.model.api.FifoRebuildResultDTO;
import es.triana.company.investments.model.api.OperationDTO;
import es.triana.company.investments.model.api.TaxSummaryDTO;
import es.triana.company.investments.model.db.ExchangeRate;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentOperation;
import es.triana.company.investments.model.db.OperationFifoLot;
import es.triana.company.investments.repository.ExchangeRateRepository;
import es.triana.company.investments.repository.InvestmentOperationRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.OperationFifoLotRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;

/**
 * Unit tests for OperationService covering DB state changes for BUY and SELL.
 *
 * Scenario:
 *   Instrument AAPL (USD), investment_id=1, tenant_id=99, instrument_id=10
 *
 *   BUY 1  — 2024-01-15 — 10 shares @ $150.00, fees $5.00, EUR/USD=1.08
 *     → total_amount = 10*150 + 5 = $1505.00
 *     → total_amount_eur = 1505 / 1.08 = €1393.52
 *
 *   BUY 2  — 2024-03-10 —  5 shares @ $160.00, fees $3.00, EUR/USD=1.09
 *     → total_amount = 5*160 + 3 = $803.00
 *     → total_amount_eur = 803 / 1.09 = €736.70
 *
 *   SELL   — 2024-06-20 —  8 shares @ $180.00, fees $4.00, EUR/USD=1.12
 *     → total_amount = 8*180 - 4 = $1436.00
 *     → total_amount_eur = 1436 / 1.12 = €1282.14
 *     → FIFO: the 8 units are fully covered by BUY1 (10 available, 8 needed)
 *       buy_unit_price_eur  = 150 / 1.08 = €138.8889
 *       sell_unit_price_eur = 180 / 1.12 = €160.7143
 *       gain_loss_eur = (160.7143 - 138.8889) * 8 = €174.60
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationServiceTest {

    @Mock InvestmentOperationRepository operationRepository;
    @Mock OperationFifoLotRepository fifoLotRepository;
    @Mock InvestmentRepository investmentRepository;
    @Mock ExchangeRateRepository exchangeRateRepository;

    @InjectMocks OperationService operationService;

    // ---- shared fixtures ----
    static final Long INVESTMENT_ID  = 1L;
    static final Long TENANT_ID      = 99L;
    static final Long INSTRUMENT_ID  = 10L;

    static final LocalDate DATE_BUY1 = LocalDate.of(2024, 1, 15);
    static final LocalDate DATE_BUY2 = LocalDate.of(2024, 3, 10);
    static final LocalDate DATE_SELL = LocalDate.of(2024, 6, 20);

    Investment investment;

    @BeforeEach
    void setUp() {
        investment = Investment.builder()
                .id(INVESTMENT_ID)
                .tenantId(TENANT_ID)
                .instrumentId(INSTRUMENT_ID)
                .name("Apple Inc.")
                .currency("USD")
                .quantity(BigDecimal.ZERO)
                .investedAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(investmentRepository.findByIdAndTenantId(INVESTMENT_ID, TENANT_ID))
                .thenReturn(Optional.of(investment));
        when(investmentRepository.findByIdAndTenantIdForUpdate(INVESTMENT_ID, TENANT_ID))
                .thenReturn(Optional.of(investment));
    }

    // =========================================================================
    // BUY operations
    // =========================================================================

    @Nested
    @DisplayName("BUY operations")
    class BuyOperations {

        @Test
        @DisplayName("BUY inserts a row in investment_operations with correct totals in original currency and EUR")
        void buy_insertsOperationWithCorrectAmounts() {
            stubEurRate("USD", DATE_BUY1, "1.08");
            stubSaveOperation(savedOp(1L, "BUY", DATE_BUY1, bd("10"), bd("150"), bd("5"),
                    bd("1505.0000"), "USD", bd("1.08"), bd("1393.5185")));

            CreateOperationRequest req = buy(DATE_BUY1, "10", "150", "5");
            operationService.registerOperation(req);

            ArgumentCaptor<InvestmentOperation> captor = ArgumentCaptor.forClass(InvestmentOperation.class);
            verify(operationRepository).save(captor.capture());
            InvestmentOperation saved = captor.getValue();

            assertThat(saved.getType()).isEqualTo("BUY");
            assertThat(saved.getQuantity()).isEqualByComparingTo("10");
            assertThat(saved.getUnitPrice()).isEqualByComparingTo("150");
            assertThat(saved.getFees()).isEqualByComparingTo("5");
            // total_amount = 10*150 + 5 = 1505
            assertThat(saved.getTotalAmount()).isEqualByComparingTo("1505.0000");
            assertThat(saved.getCurrency()).isEqualTo("USD");
            assertThat(saved.getEurExchangeRate()).isEqualByComparingTo("1.08");
            // total_amount_eur = 1505 / 1.08 ≈ 1393.52
            assertThat(saved.getTotalAmountEur()).isEqualByComparingTo("1393.5185");
        }

        @Test
        @DisplayName("BUY increments investment.quantity and investment.invested_amount")
        void buy_updatesInvestmentQuantityAndInvestedAmount() {
            stubEurRate("USD", DATE_BUY1, "1.08");
            stubSaveOperation(savedOp(1L, "BUY", DATE_BUY1, bd("10"), bd("150"), bd("5"),
                    bd("1505.0000"), "USD", bd("1.08"), bd("1393.5185")));

            operationService.registerOperation(buy(DATE_BUY1, "10", "150", "5"));

            ArgumentCaptor<Investment> captor = ArgumentCaptor.forClass(Investment.class);
            verify(investmentRepository).save(captor.capture());
            Investment updated = captor.getValue();

            // quantity: 0 → 10
            assertThat(updated.getQuantity()).isEqualByComparingTo("10");
            // invested_amount: 0 → 1505
            assertThat(updated.getInvestedAmount()).isEqualByComparingTo("1505.00");
        }

        @Test
        @DisplayName("Two BUYs accumulate quantity and invested_amount correctly")
        void buy_twoBuysAccumulatePosition() {
            // BUY 1
            stubEurRate("USD", DATE_BUY1, "1.08");
            stubSaveOperation(savedOp(1L, "BUY", DATE_BUY1, bd("10"), bd("150"), bd("5"),
                    bd("1505.0000"), "USD", bd("1.08"), bd("1393.5185")));
            operationService.registerOperation(buy(DATE_BUY1, "10", "150", "5"));

            // Simulate investment state after BUY1 (investment is the same object, mutated)
            assertThat(investment.getQuantity()).isEqualByComparingTo("10");
            assertThat(investment.getInvestedAmount()).isEqualByComparingTo("1505.00");

            // BUY 2
            stubEurRate("USD", DATE_BUY2, "1.09");
            stubSaveOperation(savedOp(2L, "BUY", DATE_BUY2, bd("5"), bd("160"), bd("3"),
                    bd("803.0000"), "USD", bd("1.09"), bd("736.6972")));
            operationService.registerOperation(buy(DATE_BUY2, "5", "160", "3"));

            // quantity: 10 → 15
            assertThat(investment.getQuantity()).isEqualByComparingTo("15");
            // invested_amount: 1505 + 803 = 2308
            assertThat(investment.getInvestedAmount()).isEqualByComparingTo("2308.00");
        }

        @Test
        @DisplayName("BUY with EUR currency uses exchange rate 1 and total_amount_eur equals total_amount")
        void buy_eurCurrencyRateIsOne() {
            Investment eurInvestment = Investment.builder()
                    .id(2L).tenantId(TENANT_ID).instrumentId(INSTRUMENT_ID)
                    .currency("EUR").quantity(BigDecimal.ZERO).investedAmount(BigDecimal.ZERO)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            when(investmentRepository.findByIdAndTenantId(2L, TENANT_ID))
                    .thenReturn(Optional.of(eurInvestment));
            when(investmentRepository.findByIdAndTenantIdForUpdate(2L, TENANT_ID))
                    .thenReturn(Optional.of(eurInvestment));

            InvestmentOperation op = savedOp(10L, "BUY", DATE_BUY1, bd("100"), bd("50"), bd("2"),
                    bd("5002.0000"), "EUR", BigDecimal.ONE, bd("5002.0000"));
            when(operationRepository.save(any())).thenReturn(op);

            CreateOperationRequest req = new CreateOperationRequest(
                    2L, TENANT_ID, "BUY", DATE_BUY1, bd("100"), bd("50"), bd("2"), "EUR", null);
            OperationDTO result = operationService.registerOperation(req);

            assertThat(result.eurExchangeRate()).isEqualByComparingTo("1");
            assertThat(result.totalAmount()).isEqualByComparingTo(result.totalAmountEur());
        }

        @Test
        @DisplayName("BUY does NOT insert anything in operation_fifo_lots")
        void buy_noFifoLotsCreated() {
            stubEurRate("USD", DATE_BUY1, "1.08");
            stubSaveOperation(savedOp(1L, "BUY", DATE_BUY1, bd("10"), bd("150"), bd("5"),
                    bd("1505.0000"), "USD", bd("1.08"), bd("1393.5185")));

            operationService.registerOperation(buy(DATE_BUY1, "10", "150", "5"));

            verify(fifoLotRepository, times(0)).save(any());
        }
    }

    // =========================================================================
    // SELL operations
    // =========================================================================

    @Nested
    @DisplayName("SELL operations")
    class SellOperations {

        InvestmentOperation buy1;
        InvestmentOperation buy2;

        @BeforeEach
        void setUpPosition() {
            // Position after BUY1 + BUY2 already applied
            investment.setQuantity(bd("15"));
            investment.setInvestedAmount(bd("2308.00"));

            buy1 = savedOp(1L, "BUY", DATE_BUY1, bd("10"), bd("150"), bd("5"),
                    bd("1505.0000"), "USD", bd("1.08"), bd("1393.5185"));
            buy2 = savedOp(2L, "BUY", DATE_BUY2, bd("5"), bd("160"), bd("3"),
                    bd("803.0000"), "USD", bd("1.09"), bd("736.6972"));
        }

        @Test
        @DisplayName("SELL inserts a row in investment_operations with correct totals (gross - fees)")
        void sell_insertsOperationWithCorrectAmounts() {
            stubEurRate("USD", DATE_SELL, "1.12");
            stubSellOperation(savedOp(3L, "SELL", DATE_SELL, bd("8"), bd("180"), bd("4"),
                    bd("1436.0000"), "USD", bd("1.12"), bd("1282.1429")));
            when(operationRepository.findBuysByInstrumentAndTenantFifo(INSTRUMENT_ID, TENANT_ID))
                    .thenReturn(List.of(buy1, buy2));
            when(fifoLotRepository.findByBuyOperationIdIn(any())).thenReturn(List.of());
            when(fifoLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            operationService.registerOperation(sell(DATE_SELL, "8", "180", "4"));

            ArgumentCaptor<InvestmentOperation> captor = ArgumentCaptor.forClass(InvestmentOperation.class);
            verify(operationRepository).save(captor.capture());
            InvestmentOperation saved = captor.getValue();

            assertThat(saved.getType()).isEqualTo("SELL");
            assertThat(saved.getQuantity()).isEqualByComparingTo("8");
            // total_amount = 8*180 - 4 = 1436 (fees subtracted on sell)
            assertThat(saved.getTotalAmount()).isEqualByComparingTo("1436.0000");
            // total_amount_eur = 1436 / 1.12 ≈ 1282.14
            assertThat(saved.getTotalAmountEur()).isEqualByComparingTo("1282.1429");
        }

        @Test
        @DisplayName("SELL reduces investment.quantity; invested_amount is NOT changed")
        void sell_reducesQuantityButNotInvestedAmount() {
            stubEurRate("USD", DATE_SELL, "1.12");
            stubSellOperation(savedOp(3L, "SELL", DATE_SELL, bd("8"), bd("180"), bd("4"),
                    bd("1436.0000"), "USD", bd("1.12"), bd("1282.1429")));
            when(operationRepository.findBuysByInstrumentAndTenantFifo(INSTRUMENT_ID, TENANT_ID))
                    .thenReturn(List.of(buy1, buy2));
            when(fifoLotRepository.findByBuyOperationIdIn(any())).thenReturn(List.of());
            when(fifoLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            operationService.registerOperation(sell(DATE_SELL, "8", "180", "4"));

            ArgumentCaptor<Investment> captor = ArgumentCaptor.forClass(Investment.class);
            verify(investmentRepository).save(captor.capture());
            Investment updated = captor.getValue();

            // quantity: 15 → 7
            assertThat(updated.getQuantity()).isEqualByComparingTo("7");
            // invested_amount unchanged — reflects total capital deployed, not current holding
            assertThat(updated.getInvestedAmount()).isEqualByComparingTo("2308.00");
        }

        @Test
        @DisplayName("SELL applies FIFO: consumes oldest BUY first and inserts correct fifo_lot")
        void sell_fifoConsumesOldestBuyFirst() {
            stubEurRate("USD", DATE_SELL, "1.12");
            stubSellOperation(savedOp(3L, "SELL", DATE_SELL, bd("8"), bd("180"), bd("4"),
                    bd("1436.0000"), "USD", bd("1.12"), bd("1282.1429")));
            when(operationRepository.findBuysByInstrumentAndTenantFifo(INSTRUMENT_ID, TENANT_ID))
                    .thenReturn(List.of(buy1, buy2));
            when(fifoLotRepository.findByBuyOperationIdIn(any())).thenReturn(List.of());
            when(fifoLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OperationDTO result = operationService.registerOperation(sell(DATE_SELL, "8", "180", "4"));

            // Exactly one FIFO lot: sell 8 units fully covered by BUY1 (10 available)
            assertThat(result.fifoLots()).hasSize(1);

            OperationDTO.FifoLotDTO lot = result.fifoLots().getFirst();
            assertThat(lot.buyOperationId()).isEqualTo(buy1.getId());
            assertThat(lot.quantity()).isEqualByComparingTo("8");

            // buy_unit_price_eur = 150 / 1.08 = 138.8889
            assertThat(lot.buyUnitPriceEur())
                    .usingComparator(BigDecimal::compareTo)
                    .isCloseTo(bd("138.8889"), within(bd("0.0001")));

            // sell_unit_price_eur = 180 / 1.12 = 160.7143
            assertThat(lot.sellUnitPriceEur())
                    .usingComparator(BigDecimal::compareTo)
                    .isCloseTo(bd("160.7143"), within(bd("0.0001")));

            // gain_loss = (160.7143 - 138.8889) * 8 = 174.60
            assertThat(lot.gainLossEur())
                    .usingComparator(BigDecimal::compareTo)
                    .isCloseTo(bd("174.60"), within(bd("0.02")));
        }

        @Test
        @DisplayName("SELL consuming two BUY lots inserts two fifo_lot rows")
        void sell_fifoSpansTwoBuyLots() {
            // Selling 12 shares: first exhausts BUY1 (10) then takes 2 from BUY2 (5)
            investment.setQuantity(bd("15"));
            stubEurRate("USD", DATE_SELL, "1.12");
            stubSellOperation(savedOp(3L, "SELL", DATE_SELL, bd("12"), bd("180"), bd("4"),
                    bd("2156.0000"), "USD", bd("1.12"), bd("1925.0000")));
            when(operationRepository.findBuysByInstrumentAndTenantFifo(INSTRUMENT_ID, TENANT_ID))
                    .thenReturn(List.of(buy1, buy2));
            when(fifoLotRepository.findByBuyOperationIdIn(any())).thenReturn(List.of());
            when(fifoLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OperationDTO result = operationService.registerOperation(sell(DATE_SELL, "12", "180", "4"));

            assertThat(result.fifoLots()).hasSize(2);

            OperationDTO.FifoLotDTO lot1 = result.fifoLots().getFirst();
            assertThat(lot1.buyOperationId()).isEqualTo(buy1.getId());
            // BUY1 fully consumed: 10 units
            assertThat(lot1.quantity()).isEqualByComparingTo("10");

            OperationDTO.FifoLotDTO lot2 = result.fifoLots().get(1);
            assertThat(lot2.buyOperationId()).isEqualTo(buy2.getId());
            // BUY2 partially consumed: 2 units
            assertThat(lot2.quantity()).isEqualByComparingTo("2");

            // buy2 unit price in EUR = 160 / 1.09 = 146.7890
            assertThat(lot2.buyUnitPriceEur())
                    .usingComparator(BigDecimal::compareTo)
                    .isCloseTo(bd("146.7890"), within(bd("0.0001")));
        }

        @Test
        @DisplayName("SELL respects already-consumed quantities from prior sells (partial lot)")
        void sell_fifoRespectsPreviouslyConsumedLot() {
            // A previous SELL already consumed 6 units from BUY1, leaving 4 available
            OperationFifoLot previousLot = OperationFifoLot.builder()
                    .id(99L)
                    .sellOperationId(5L)
                    .buyOperationId(buy1.getId())
                    .quantity(bd("6"))
                    .buyUnitPriceEur(bd("138.8889"))
                    .sellUnitPriceEur(bd("150.0000"))
                    .gainLossEur(bd("66.67"))
                    .build();

            stubEurRate("USD", DATE_SELL, "1.12");
            // Now we sell 6 shares: 4 from BUY1 remainder + 2 from BUY2
            stubSellOperation(savedOp(3L, "SELL", DATE_SELL, bd("6"), bd("180"), bd("4"),
                    bd("1076.0000"), "USD", bd("1.12"), bd("960.7143")));
            when(operationRepository.findBuysByInstrumentAndTenantFifo(INSTRUMENT_ID, TENANT_ID))
                    .thenReturn(List.of(buy1, buy2));
            when(fifoLotRepository.findByBuyOperationIdIn(any())).thenReturn(List.of(previousLot));
            when(fifoLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OperationDTO result = operationService.registerOperation(sell(DATE_SELL, "6", "180", "4"));

            assertThat(result.fifoLots()).hasSize(2);
            // First lot: 4 remaining from BUY1 (10 - 6 already consumed)
            assertThat(result.fifoLots().getFirst().buyOperationId()).isEqualTo(buy1.getId());
            assertThat(result.fifoLots().getFirst().quantity()).isEqualByComparingTo("4");
            // Second lot: 2 from BUY2
            assertThat(result.fifoLots().get(1).buyOperationId()).isEqualTo(buy2.getId());
            assertThat(result.fifoLots().get(1).quantity()).isEqualByComparingTo("2");
        }
    }

    // =========================================================================
    // Error cases
    // =========================================================================

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        @DisplayName("Investment not found throws InvestmentValidationException")
        void invalidInvestment_throws() {
            when(investmentRepository.findByIdAndTenantIdForUpdate(999L, TENANT_ID)).thenReturn(Optional.empty());

            CreateOperationRequest req = new CreateOperationRequest(
                    999L, TENANT_ID, "BUY", DATE_BUY1, bd("10"), bd("150"), null, "USD", null);

            assertThatThrownBy(() -> operationService.registerOperation(req))
                    .isInstanceOf(InvestmentValidationException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("No exchange rate available throws InvestmentValidationException")
        void noExchangeRate_throws() {
            when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndAsOf(
                    eq("EUR"), eq("USD"), any())).thenReturn(Optional.empty());
            when(exchangeRateRepository.findFirstByFromCurrencyAndToCurrencyOrderByAsOfDesc(
                    eq("EUR"), eq("USD"))).thenReturn(Optional.empty());

            CreateOperationRequest req = buy(DATE_BUY1, "10", "150", "5");

            assertThatThrownBy(() -> operationService.registerOperation(req))
                    .isInstanceOf(InvestmentValidationException.class)
                    .hasMessageContaining("EUR/USD");
        }

        @Test
        @DisplayName("SELL with more units than available BUY stock throws before persisting anything")
        void sell_insufficientStock_throwsAndNothingPersisted() {
            // Only 5 units available (one BUY lot of 5), trying to sell 10
            investment.setQuantity(bd("5"));
            InvestmentOperation smallBuy = savedOp(1L, "BUY", DATE_BUY1, bd("5"), bd("150"), bd("0"),
                    bd("750.0000"), "USD", bd("1.08"), bd("694.4444"));

            when(operationRepository.findBuysByInstrumentAndTenantFifo(INSTRUMENT_ID, TENANT_ID))
                    .thenReturn(List.of(smallBuy));
            when(fifoLotRepository.findByBuyOperationIdIn(any())).thenReturn(List.of());

            // Note: validation runs before resolveEurRate, so no exchange rate stub needed
            CreateOperationRequest req = sell(DATE_SELL, "10", "180", "4");

            assertThatThrownBy(() -> operationService.registerOperation(req))
                    .isInstanceOf(InvestmentValidationException.class)
                    .hasMessageContaining("Insufficient stock");

            // Nothing should be written to DB
            verify(operationRepository, times(0)).save(any());
            verify(fifoLotRepository, times(0)).save(any());
            verify(investmentRepository, times(0)).save(any());
        }

        @Test
        @DisplayName("SELL with exact available stock succeeds")
        void sell_exactStock_succeeds() {
            investment.setQuantity(bd("10"));
            InvestmentOperation buyLot = savedOp(1L, "BUY", DATE_BUY1, bd("10"), bd("150"), bd("5"),
                    bd("1505.0000"), "USD", bd("1.08"), bd("1393.5185"));

            stubEurRate("USD", DATE_SELL, "1.12");
            stubSellOperation(savedOp(3L, "SELL", DATE_SELL, bd("10"), bd("180"), bd("4"),
                    bd("1796.0000"), "USD", bd("1.12"), bd("1603.5714")));
            when(operationRepository.findBuysByInstrumentAndTenantFifo(INSTRUMENT_ID, TENANT_ID))
                    .thenReturn(List.of(buyLot));
            when(fifoLotRepository.findByBuyOperationIdIn(any())).thenReturn(List.of());
            when(fifoLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            OperationDTO result = operationService.registerOperation(sell(DATE_SELL, "10", "180", "4"));
            assertThat(result.fifoLots()).hasSize(1);
            assertThat(result.fifoLots().getFirst().quantity()).isEqualByComparingTo("10");
        }
    }

    // =========================================================================
    // FIFO rebuild command
    // =========================================================================

    @Nested
    @DisplayName("FIFO rebuild")
    class FifoRebuild {

        @Test
        @DisplayName("Rebuild FIFO from scratch for instrument+tenant in temporal order")
        void rebuild_backdatedOperations_rebuildsLotsInOrder() {
            InvestmentOperation buy1 = savedOp(101L, "BUY", LocalDate.of(2024, 1, 10),
                    bd("10"), bd("100"), bd("0"), bd("1000.0000"), "USD", bd("1.10"), bd("909.0909"));
            InvestmentOperation sell1 = savedOp(102L, "SELL", LocalDate.of(2024, 1, 20),
                    bd("4"), bd("120"), bd("0"), bd("480.0000"), "USD", bd("1.20"), bd("400.0000"));
            // Backdated BUY inserted before an existing later SELL
            InvestmentOperation buy2 = savedOp(103L, "BUY", LocalDate.of(2024, 2, 1),
                    bd("5"), bd("90"), bd("0"), bd("450.0000"), "USD", bd("1.08"), bd("416.6667"));
            InvestmentOperation sell2 = savedOp(104L, "SELL", LocalDate.of(2024, 2, 10),
                    bd("5"), bd("110"), bd("0"), bd("550.0000"), "USD", bd("1.10"), bd("500.0000"));

            when(operationRepository.findByInstrumentAndTenantOrderByOperationDateAscIdAscForUpdate(
                    INSTRUMENT_ID, TENANT_ID)).thenReturn(List.of(buy1, sell1, buy2, sell2));
            when(fifoLotRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            FifoRebuildResultDTO result = operationService.rebuildFifoForInstrumentTenant(INSTRUMENT_ID, TENANT_ID);

            verify(fifoLotRepository).deleteBySellOperationIdIn(List.of(102L, 104L));
            ArgumentCaptor<OperationFifoLot> captor = ArgumentCaptor.forClass(OperationFifoLot.class);
            verify(fifoLotRepository, times(2)).save(captor.capture());

            List<OperationFifoLot> savedLots = captor.getAllValues();
            // SELL1 consumes 4 from BUY1
            assertThat(savedLots.get(0).getSellOperationId()).isEqualTo(102L);
            assertThat(savedLots.get(0).getBuyOperationId()).isEqualTo(101L);
            assertThat(savedLots.get(0).getQuantity()).isEqualByComparingTo("4");

            // SELL2 consumes remaining 5 from BUY1 first (10 - 4)
            assertThat(savedLots.get(1).getSellOperationId()).isEqualTo(104L);
            assertThat(savedLots.get(1).getBuyOperationId()).isEqualTo(101L);
            assertThat(savedLots.get(1).getQuantity()).isEqualByComparingTo("5");

            assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
            assertThat(result.tenantId()).isEqualTo(TENANT_ID);
            assertThat(result.operationsProcessed()).isEqualTo(4);
            assertThat(result.sellsRebuilt()).isEqualTo(2);
                        assertThat(result.lotsCreated()).isEqualTo(2);
        }

        @Test
        @DisplayName("Rebuild FIFO throws when historical SELL cannot be covered")
        void rebuild_insufficientHistoricalStock_throws() {
            InvestmentOperation sellOnly = savedOp(202L, "SELL", LocalDate.of(2024, 4, 10),
                    bd("5"), bd("150"), bd("0"), bd("750.0000"), "USD", bd("1.15"), bd("652.1739"));

            when(operationRepository.findByInstrumentAndTenantOrderByOperationDateAscIdAscForUpdate(
                    INSTRUMENT_ID, TENANT_ID)).thenReturn(List.of(sellOnly));

            assertThatThrownBy(() -> operationService.rebuildFifoForInstrumentTenant(INSTRUMENT_ID, TENANT_ID))
                    .isInstanceOf(InvestmentValidationException.class)
                    .hasMessageContaining("Cannot rebuild FIFO")
                    .hasMessageContaining("short by");

            verify(fifoLotRepository).deleteBySellOperationIdIn(List.of(202L));
            verify(fifoLotRepository, times(0)).save(any());
        }
    }

        // =========================================================================
        // Tax summary
        // =========================================================================

        @Nested
        @DisplayName("Tax summary")
        class TaxSummary {

                @Test
                @DisplayName("Returns annual realised gain/loss with breakdowns by instrument and currency")
                void taxSummary_returnsAggregatedData() {
                        when(fifoLotRepository.sumGainLossByTenantAndSellDateBetween(
                                        eq(TENANT_ID), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31))))
                                        .thenReturn(bd("321.98765"));

                        when(fifoLotRepository.sumGainLossByInstrumentAndSellDateBetween(
                                        eq(TENANT_ID), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31))))
                                        .thenReturn(List.of(
                                                        new Object[] {10L, "AAPL", "AAPL", "Apple Inc.", bd("250.11111")},
                                                        new Object[] {20L, "MSFT", "MSFT", "Microsoft", bd("71.87654")}));

                        when(fifoLotRepository.sumGainLossByCurrencyAndSellDateBetween(
                                        eq(TENANT_ID), eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31))))
                                        .thenReturn(List.of(
                                                        new Object[] {"USD", bd("300.10001")},
                                                        new Object[] {"EUR", bd("21.88764")}));

                        TaxSummaryDTO result = operationService.getTaxSummary(TENANT_ID, 2024);

                        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
                        assertThat(result.year()).isEqualTo(2024);
                        assertThat(result.realizedGainLossEur()).isEqualByComparingTo("321.9877");

                        assertThat(result.byInstrument()).hasSize(2);
                        assertThat(result.byInstrument().get(0).instrumentId()).isEqualTo(10L);
                        assertThat(result.byInstrument().get(0).instrumentCode()).isEqualTo("AAPL");
                        assertThat(result.byInstrument().get(0).realizedGainLossEur()).isEqualByComparingTo("250.1111");

                        assertThat(result.byCurrency()).hasSize(2);
                        assertThat(result.byCurrency().get(0).currency()).isEqualTo("USD");
                        assertThat(result.byCurrency().get(0).realizedGainLossEur()).isEqualByComparingTo("300.1000");
                }

                @Test
                @DisplayName("Invalid year throws validation error")
                void taxSummary_invalidYear_throws() {
                        assertThatThrownBy(() -> operationService.getTaxSummary(TENANT_ID, 1800))
                                        .isInstanceOf(InvestmentValidationException.class)
                                        .hasMessageContaining("year");
                }
        }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CreateOperationRequest buy(LocalDate date, String qty, String unitPrice, String fees) {
        return new CreateOperationRequest(
                INVESTMENT_ID, TENANT_ID, "BUY", date,
                bd(qty), bd(unitPrice), bd(fees), "USD", null);
    }

    private CreateOperationRequest sell(LocalDate date, String qty, String unitPrice, String fees) {
        return new CreateOperationRequest(
                INVESTMENT_ID, TENANT_ID, "SELL", date,
                bd(qty), bd(unitPrice), bd(fees), "USD", null);
    }

    private void stubEurRate(String currency, LocalDate date, String rate) {
        ExchangeRate er = ExchangeRate.builder()
                .fromCurrency("EUR").toCurrency(currency)
                .rate(bd(rate)).asOf(date).source("ECB").build();
        when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndAsOf(
                eq("EUR"), eq(currency), eq(date)))
                .thenReturn(Optional.of(er));
    }

    /** Stub: operationRepository.save() returns the pre-built saved operation */
    private void stubSaveOperation(InvestmentOperation op) {
        when(operationRepository.save(any())).thenReturn(op);
    }

    /** For SELL: save() is called once for the operation then once per fifo lot via fifoLotRepository */
    private void stubSellOperation(InvestmentOperation op) {
        when(operationRepository.save(any())).thenReturn(op);
    }

    private InvestmentOperation savedOp(Long id, String type, LocalDate date,
                                        BigDecimal qty, BigDecimal unitPrice, BigDecimal fees,
                                        BigDecimal totalAmount, String currency,
                                        BigDecimal eurRate, BigDecimal totalAmountEur) {
        return InvestmentOperation.builder()
                .id(id)
                .investmentId(INVESTMENT_ID)
                .tenantId(TENANT_ID)
                .type(type)
                .operationDate(date)
                .quantity(qty)
                .unitPrice(unitPrice)
                .fees(fees)
                .totalAmount(totalAmount)
                .currency(currency)
                .eurExchangeRate(eurRate)
                .totalAmountEur(totalAmountEur)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}

