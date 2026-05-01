package es.triana.company.investments.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentPrice;

public interface InvestmentPriceRepository extends JpaRepository<InvestmentPrice, Long> {

    Optional<InvestmentPrice> findFirstByInstrumentIdOrderByAsOfDesc(Long instrumentId);
}
