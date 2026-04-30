package es.triana.company.investments.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.investments.model.db.InvestmentPrice;

public interface InvestmentPriceRepository extends JpaRepository<InvestmentPrice, Long> {

    @Query("select p from InvestmentPrice p where p.instrumentId = :instrumentId order by p.asOf desc limit 1")
    Optional<InvestmentPrice> findLatestByInstrumentId(@Param("instrumentId") Long instrumentId);
}
