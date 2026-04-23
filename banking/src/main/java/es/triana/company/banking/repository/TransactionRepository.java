package es.triana.company.banking.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import es.triana.company.banking.model.db.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

	boolean existsByTenantIdAndExternalTxId(Long tenantId, String externalTxId);

	boolean existsByTenantIdAndExternalTxIdAndIdNot(Long tenantId, String externalTxId, Long id);

	Optional<Transaction> findByIdAndTenantId(Long id, Long tenantId);

	List<Transaction> findAllByTenantIdOrderByBookingDateDescIdDesc(Long tenantId);

	List<Transaction> findAllByTenantIdAndCategory_IdOrderByBookingDateDescIdDesc(Long tenantId, Long categoryId);

	List<Transaction> findDistinctByTenantIdAndTags_IdOrderByBookingDateDescIdDesc(Long tenantId, Long tagId);

	List<Transaction> findAllByTenantIdAndBookingDateBetweenOrderByBookingDateDescIdDesc(Long tenantId, LocalDate startDate,
			LocalDate endDate);

	@Query("select t from Transaction t where t.tenantId = :tenantId and (t.sourceAccount.id = :accountId or t.destinationAccount.id = :accountId) order by t.bookingDate desc, t.id desc")
	List<Transaction> findAllByTenantIdAndAccountId(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId);

	@Query("""
			select t from Transaction t
			where t.tenantId = :tenantId
			  and (:accountId is null or t.sourceAccount.id = :accountId or t.destinationAccount.id = :accountId)
			  and (:startDate is null or t.bookingDate >= :startDate)
			  and (:endDate is null or t.bookingDate <= :endDate)
			order by t.bookingDate desc, t.id desc
			""")
	List<Transaction> findAllForExport(
			@Param("tenantId") Long tenantId,
			@Param("accountId") Long accountId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	// ── Dashboard queries ──────────────────────────────────────────────────────

	@Query("""
			select sum(t.amount) from Transaction t
			where t.tenantId = :tenantId
			  and t.bookingDate >= :startDate
			  and t.bookingDate <= :endDate
			  and (t.transactionType is null or upper(t.transactionType.name) <> 'TRANSFER')
			  and t.amount > 0
			""")
	java.math.BigDecimal sumIncomeByPeriod(
			@Param("tenantId")  Long tenantId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate")   LocalDate endDate);

	@Query("""
			select sum(t.amount) from Transaction t
			where t.tenantId = :tenantId
			  and t.bookingDate >= :startDate
			  and t.bookingDate <= :endDate
			  and (t.transactionType is null or upper(t.transactionType.name) <> 'TRANSFER')
			  and t.amount < 0
			""")
	java.math.BigDecimal sumExpensesByPeriod(
			@Param("tenantId")  Long tenantId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate")   LocalDate endDate);

	@Query("""
			select count(t) from Transaction t
			where t.tenantId = :tenantId
			  and t.bookingDate >= :startDate
			  and t.bookingDate <= :endDate
			  and (t.transactionType is null or upper(t.transactionType.name) <> 'TRANSFER')
			""")
	Long countByPeriod(
			@Param("tenantId")  Long tenantId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate")   LocalDate endDate);

	/**
	 * Returns rows: [categoryId (Long), categoryCode (String), categoryName (String),
	 *                total (BigDecimal), transactionCount (Long)]
	 * Only expenses (amount < 0) with a non-null category.
	 */
	@Query("""
			select t.category.id, t.category.code, t.category.name,
			       sum(t.amount), count(t)
			from Transaction t
			where t.tenantId = :tenantId
			  and t.bookingDate >= :startDate
			  and t.bookingDate <= :endDate
			  and (t.transactionType is null or upper(t.transactionType.name) <> 'TRANSFER')
			  and t.amount < 0
			  and t.category is not null
			group by t.category.id, t.category.code, t.category.name
			order by sum(t.amount) asc
			""")
	List<Object[]> findSpendingByCategory(
			@Param("tenantId")  Long tenantId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate")   LocalDate endDate);

	/**
	 * Returns rows: [bookingDate (LocalDate), amount (BigDecimal)]
	 * Used by the service to build the time-series grouped by DAY or MONTH.
	 */
	@Query("""
			select t.bookingDate, t.amount from Transaction t
			where t.tenantId = :tenantId
			  and t.bookingDate >= :startDate
			  and t.bookingDate <= :endDate
			  and (t.transactionType is null or upper(t.transactionType.name) <> 'TRANSFER')
			order by t.bookingDate asc
			""")
	List<Object[]> findDateAmountSeries(
			@Param("tenantId")  Long tenantId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate")   LocalDate endDate);
}
