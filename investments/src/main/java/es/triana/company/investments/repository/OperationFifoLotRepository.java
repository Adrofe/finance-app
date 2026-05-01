package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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

    /** Total gain/loss in EUR for a given sell operation (sum of all matched lots) */
    @Query("SELECT COALESCE(SUM(l.gainLossEur), 0) FROM OperationFifoLot l WHERE l.sellOperationId = :sellId")
    java.math.BigDecimal sumGainLossBySellOperation(@Param("sellId") Long sellId);
}
