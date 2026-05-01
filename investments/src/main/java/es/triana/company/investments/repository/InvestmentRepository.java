package es.triana.company.investments.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.Investment;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    List<Investment> findByTenantIdOrderByUpdatedAtDescIdDesc(Long tenantId);

    List<Investment> findByInstrumentId(Long instrumentId);

    Optional<Investment> findByIdAndTenantId(Long id, Long tenantId);
}
