package es.triana.company.investments.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentInstrument;

public interface InvestmentInstrumentRepository extends JpaRepository<InvestmentInstrument, Long> {

	List<InvestmentInstrument> findByLastPriceIsNotNull();

	List<InvestmentInstrument> findAllByOrderByNameAsc();

	Optional<InvestmentInstrument> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
