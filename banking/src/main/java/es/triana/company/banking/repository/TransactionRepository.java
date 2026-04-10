package es.triana.company.banking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.banking.model.db.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	boolean existsByTenantIdAndExternalTxId(Long tenantId, String externalTxId);

	boolean existsByTenantIdAndExternalTxIdAndIdNot(Long tenantId, String externalTxId, Long id);

	Optional<Transaction> findByIdAndTenantId(Long id, Long tenantId);

	List<Transaction> findAllByTenantIdOrderByBookingDateDescIdDesc(Long tenantId);

	List<Transaction> findAllByTenantIdAndCategoryIdOrderByBookingDateDescIdDesc(Long tenantId, Long categoryId);

	List<Transaction> findAllByTenantIdAndBookingDateBetweenOrderByBookingDateDescIdDesc(Long tenantId, LocalDate startDate,
			LocalDate endDate);

	@Query("select t from Transaction t where t.tenantId = :tenantId and (t.sourceAccountId = :accountId or t.destinationAccountId = :accountId) order by t.bookingDate desc, t.id desc")
	List<Transaction> findAllByTenantIdAndAccountId(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId);
}
