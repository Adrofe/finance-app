package es.triana.company.investments.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentCountryAlias;

public interface InvestmentCountryAliasRepository extends JpaRepository<InvestmentCountryAlias, Long> {

    Optional<InvestmentCountryAlias> findByNormalizedSourceName(String normalizedSourceName);
}