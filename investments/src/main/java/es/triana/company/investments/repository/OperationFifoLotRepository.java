package es.triana.company.investments.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.investments.model.db.OperationFifoLot;

public interface OperationFifoLotRepository extends JpaRepository<OperationFifoLot, Long> {

    List<OperationFifoLot> findBySellOperationId(Long sellOperationId);

    List<OperationFifoLot> findByBuyOperationId(Long buyOperationId);

    /**
     * Returns all FIFO lots whose buy side is in the given set of buy-operation IDs.
     * Used to compute already-consumed quantities without a full-table scan.
     */
    List<OperationFifoLot> findByBuyOperationIdIn(java.util.Collection<Long> buyOperationIds);

    @Modifying
    void deleteBySellOperationIdIn(java.util.Collection<Long> sellOperationIds);

    /** Total gain/loss in EUR for a given sell operation (sum of all matched lots) */
    @Query("SELECT COALESCE(SUM(l.gainLossEur), 0) FROM OperationFifoLot l WHERE l.sellOperationId = :sellId")
    java.math.BigDecimal sumGainLossBySellOperation(@Param("sellId") Long sellId);

        /**
         * Total realised gain/loss in EUR for one tenant in the selected date range,
         * based on SELL operation dates.
         */
        @Query("""
            SELECT COALESCE(SUM(l.gainLossEur), 0)
            FROM OperationFifoLot l, InvestmentOperation sell
            WHERE sell.id = l.sellOperationId
              AND sell.tenantId = :tenantId
              AND sell.operationDate BETWEEN :startDate AND :endDate
            """)
        java.math.BigDecimal sumGainLossByTenantAndSellDateBetween(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

        /**
         * Realised gain/loss grouped by instrument for one tenant and period.
         * Returns rows: [instrumentId, code, symbol, name, sumGainLossEur]
         */
        @Query("""
            SELECT inv.instrumentId, ii.code, ii.symbol, ii.name, COALESCE(SUM(l.gainLossEur), 0)
            FROM OperationFifoLot l, InvestmentOperation sell, Investment inv, InvestmentInstrument ii
            WHERE sell.id = l.sellOperationId
              AND inv.id = sell.investmentId
              AND ii.id = inv.instrumentId
              AND sell.tenantId = :tenantId
              AND sell.operationDate BETWEEN :startDate AND :endDate
            GROUP BY inv.instrumentId, ii.code, ii.symbol, ii.name
            ORDER BY COALESCE(SUM(l.gainLossEur), 0) DESC, inv.instrumentId ASC
            """)
        List<Object[]> sumGainLossByInstrumentAndSellDateBetween(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

        /**
         * Realised gain/loss grouped by SELL operation currency for one tenant and period.
         * Returns rows: [currency, sumGainLossEur]
         */
        @Query("""
            SELECT sell.currency, COALESCE(SUM(l.gainLossEur), 0)
            FROM OperationFifoLot l, InvestmentOperation sell
            WHERE sell.id = l.sellOperationId
              AND sell.tenantId = :tenantId
              AND sell.operationDate BETWEEN :startDate AND :endDate
            GROUP BY sell.currency
            ORDER BY COALESCE(SUM(l.gainLossEur), 0) DESC, sell.currency ASC
            """)
        List<Object[]> sumGainLossByCurrencyAndSellDateBetween(
            @Param("tenantId") Long tenantId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
