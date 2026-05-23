package es.triana.company.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.TaxType;

public interface TaxTypeRepository extends JpaRepository<TaxType, Long> {

    Optional<TaxType> findByCode(String code);

    List<TaxType> findAllByOrderByNameAsc();
}
