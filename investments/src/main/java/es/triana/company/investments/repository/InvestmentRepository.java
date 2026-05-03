package es.triana.company.investments.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.investments.model.db.Investment;
import jakarta.persistence.LockModeType;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

        @Query("SELECT i FROM Investment i "
            + "LEFT JOIN FETCH i.type t "
            + "LEFT JOIN FETCH i.instrument inst "
            + "LEFT JOIN FETCH i.platform p "
            + "WHERE i.tenantId = :tenantId "
            + "ORDER BY i.updatedAt DESC, i.id DESC")
        List<Investment> findByTenantIdOrderByUpdatedAtDescIdDesc(@Param("tenantId") Long tenantId);

    List<Investment> findByInstrumentId(Long instrumentId);

    Optional<Investment> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * Same lookup but acquires a PESSIMISTIC_WRITE (SELECT … FOR UPDATE) lock.
     * Use during write operations (registerOperation) to serialise concurrent
     * position updates on the same Investment row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Investment i WHERE i.id = :id AND i.tenantId = :tenantId")
    Optional<Investment> findByIdAndTenantIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
