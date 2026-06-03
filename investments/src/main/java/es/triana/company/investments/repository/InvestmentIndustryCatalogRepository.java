package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentIndustryCatalog;

public interface InvestmentIndustryCatalogRepository extends JpaRepository<InvestmentIndustryCatalog, Long> {

    List<InvestmentIndustryCatalog> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
