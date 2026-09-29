package es.triana.company.investments.repository;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentPrice;

public interface InvestmentPriceRepository extends JpaRepository<InvestmentPrice, Long> {

    Optional<InvestmentPrice> findFirstByInstrumentIdOrderByAsOfDesc(Long instrumentId);

        Optional<InvestmentPrice> findFirstByInstrumentIdOrderByAsOfAsc(Long instrumentId);

        Optional<InvestmentPrice> findFirstByInstrumentIdAndAsOfBeforeOrderByAsOfDescIdDesc(
            Long instrumentId, LocalDateTime asOf);

        List<InvestmentPrice> findByInstrumentIdAndAsOfBetweenOrderByAsOfAscIdAsc(
            Long instrumentId, LocalDateTime from, LocalDateTime to);
}
