package es.triana.company.banking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.Institution;

public interface InstitutionRepository extends JpaRepository<Institution, Long> {
}
