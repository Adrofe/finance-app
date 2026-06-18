package es.triana.company.investments.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentRegionAlias;

public interface InvestmentRegionAliasRepository extends JpaRepository<InvestmentRegionAlias, Long> {

    Optional<InvestmentRegionAlias> findByNormalizedSourceName(String normalizedSourceName);
}