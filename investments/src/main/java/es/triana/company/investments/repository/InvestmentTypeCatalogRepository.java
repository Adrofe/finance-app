package es.triana.company.investments.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentTypeCatalog;

public interface InvestmentTypeCatalogRepository extends JpaRepository<InvestmentTypeCatalog, Long> {
}