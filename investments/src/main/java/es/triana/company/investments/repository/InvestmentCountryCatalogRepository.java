package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentCountryCatalog;

public interface InvestmentCountryCatalogRepository extends JpaRepository<InvestmentCountryCatalog, Long> {

    List<InvestmentCountryCatalog> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
