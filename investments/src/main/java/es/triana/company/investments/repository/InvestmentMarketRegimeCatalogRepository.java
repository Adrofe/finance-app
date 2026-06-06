package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentMarketRegimeCatalog;

public interface InvestmentMarketRegimeCatalogRepository extends JpaRepository<InvestmentMarketRegimeCatalog, Long> {

    List<InvestmentMarketRegimeCatalog> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}