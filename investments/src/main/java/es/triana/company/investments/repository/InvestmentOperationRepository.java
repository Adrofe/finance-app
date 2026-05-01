package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.investments.model.db.InvestmentOperation;
import jakarta.persistence.LockModeType;

public interface InvestmentOperationRepository extends JpaRepository<InvestmentOperation, Long> {

    List<InvestmentOperation> findByInvestmentIdOrderByOperationDateAscIdAsc(Long investmentId);

    List<InvestmentOperation> findByTenantIdOrderByOperationDateDescIdDesc(Long tenantId);

    /**
     * Returns BUY lots for a given investment ordered FIFO (oldest first).
     * Used during sell processing to consume lots in order.
     */
    @Query("""
            SELECT o FROM InvestmentOperation o
            WHERE o.investmentId = :investmentId
              AND o.type = 'BUY'
            ORDER BY o.operationDate ASC, o.id ASC
            """)
    List<InvestmentOperation> findBuysByInvestmentFifo(@Param("investmentId") Long investmentId);

    /**
     * Returns BUY lots across all investments of the same instrument for a tenant,
     * ordered FIFO. Used when FIFO must be applied globally per instrument
     * (AEAT criterion for Spanish tax purposes).
     * <p>
     * Acquires a PESSIMISTIC_WRITE (SELECT … FOR UPDATE) lock so that two
     * concurrent SELL operations on the same instrument+tenant are serialised:
     * the second transaction will block until the first commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM InvestmentOperation o
            JOIN Investment inv ON inv.id = o.investmentId
            WHERE inv.instrumentId = :instrumentId
              AND o.tenantId = :tenantId
              AND o.type = 'BUY'
            ORDER BY o.operationDate ASC, o.id ASC
            """)
    List<InvestmentOperation> findBuysByInstrumentAndTenantFifo(
            @Param("instrumentId") Long instrumentId,
            @Param("tenantId") Long tenantId);
}
