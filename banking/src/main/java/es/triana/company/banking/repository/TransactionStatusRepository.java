package es.triana.company.banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.TransactionStatus;

public interface TransactionStatusRepository extends JpaRepository<TransactionStatus, Long> {

    Optional<TransactionStatus> findByCode(String code);
}