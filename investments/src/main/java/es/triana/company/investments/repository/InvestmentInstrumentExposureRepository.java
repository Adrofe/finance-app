package es.triana.company.investments.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentInstrumentExposure;

public interface InvestmentInstrumentExposureRepository extends JpaRepository<InvestmentInstrumentExposure, Long> {

    List<InvestmentInstrumentExposure> findAllByInstrumentIdOrderByDimensionAscIdAsc(Long instrumentId);

    void deleteByInstrumentId(Long instrumentId);
}
