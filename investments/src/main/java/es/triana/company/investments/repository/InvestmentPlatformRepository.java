package es.triana.company.investments.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentPlatform;

public interface InvestmentPlatformRepository extends JpaRepository<InvestmentPlatform, Long> {
}