package es.triana.company.investments.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.investments.model.db.InvestmentPlatform;

public interface InvestmentPlatformRepository extends JpaRepository<InvestmentPlatform, Long> {

	List<InvestmentPlatform> findAllByOrderByNameAsc();

	Optional<InvestmentPlatform> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}