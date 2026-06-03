package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentSectorCatalog;

public interface InvestmentSectorCatalogRepository extends JpaRepository<InvestmentSectorCatalog, Long> {

    List<InvestmentSectorCatalog> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
