package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentRegionCatalog;

public interface InvestmentRegionCatalogRepository extends JpaRepository<InvestmentRegionCatalog, Long> {

    List<InvestmentRegionCatalog> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
