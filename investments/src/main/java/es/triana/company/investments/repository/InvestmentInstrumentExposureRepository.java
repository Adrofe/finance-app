package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.investments.model.db.InvestmentInstrumentExposure;

public interface InvestmentInstrumentExposureRepository extends JpaRepository<InvestmentInstrumentExposure, Long> {

    List<InvestmentInstrumentExposure> findAllByInstrumentIdOrderByDimensionAscIdAsc(Long instrumentId);

    @Query("""
            SELECT e
            FROM InvestmentInstrumentExposure e
            LEFT JOIN FETCH e.country
            LEFT JOIN FETCH e.region
            LEFT JOIN FETCH e.sector
            LEFT JOIN FETCH e.industry
                LEFT JOIN FETCH e.marketRegime
            WHERE e.instrument.id = :instrumentId
            ORDER BY e.dimension ASC, e.id ASC
            """)
    List<InvestmentInstrumentExposure> findAllWithBucketsByInstrumentId(@Param("instrumentId") Long instrumentId);

            boolean existsByMarketRegimeId(Long marketRegimeId);

    void deleteByInstrumentId(Long instrumentId);
}
