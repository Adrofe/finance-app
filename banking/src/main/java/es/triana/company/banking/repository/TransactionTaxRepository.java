package es.triana.company.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import es.triana.company.banking.model.db.TransactionTax;

public interface TransactionTaxRepository extends JpaRepository<TransactionTax, Long> {

    Optional<TransactionTax> findByTransactionIdAndTenantId(Long transactionId, Long tenantId);

    List<TransactionTax> findAllByTenantIdOrderByTransaction_BookingDateDescIdDesc(Long tenantId);
}
