package es.triana.company.banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.TransactionType;

public interface TransactionTypeRepository extends JpaRepository<TransactionType, Long> {

    Optional<TransactionType> findByName(String name);
}