package es.triana.company.banking.service.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Transaction;

@Component
public class TransactionMapper {

	public String normalizeDescription(String description) {
		if (description == null) {
			return null;
		}

		String normalizedDescription = description.trim();
		return normalizedDescription.isEmpty() ? null : normalizedDescription;
	}

	public String normalizeExternalId(String externalId) {
		if (externalId == null) {
			return null;
		}

		String normalizedExternalId = externalId.trim();
		return normalizedExternalId.isEmpty() ? null : normalizedExternalId;
	}

	public Transaction toEntity(
			TransactionDTO transactionDTO,
			Long tenantId,
			String normalizedCurrency,
			LocalDateTime timestamp) {
		return Transaction.builder()
				.tenantId(tenantId)
				.sourceAccountId(transactionDTO.getSourceAccountId())
				.destinationAccountId(transactionDTO.getDestinationAccountId())
				.bookingDate(transactionDTO.getBookingDate().toLocalDate())
				.valueDate(transactionDTO.getValueDate() != null ? transactionDTO.getValueDate().toLocalDate() : null)
				.amount(BigDecimal.valueOf(transactionDTO.getAmount()))
				.currency(normalizedCurrency)
				.descriptionRaw(normalizeDescription(transactionDTO.getDescription()))
				.merchantId(transactionDTO.getMerchantId())
				.categoryId(transactionDTO.getCategoryId())
				.externalTxId(normalizeExternalId(transactionDTO.getExternalId()))
				.statusId(transactionDTO.getStatusId() != null ? transactionDTO.getStatusId() : 1L)
				.transactionType(transactionDTO.getTypeId())
				.createdAt(timestamp)
				.updatedAt(timestamp)
				.build();
	}

	public TransactionDTO toDto(Transaction transaction) {
		return TransactionDTO.builder()
				.tenantId(transaction.getTenantId())
				.sourceAccountId(transaction.getSourceAccountId())
				.destinationAccountId(transaction.getDestinationAccountId())
				.bookingDate(transaction.getBookingDate() != null ? transaction.getBookingDate().atStartOfDay() : null)
				.valueDate(transaction.getValueDate() != null ? transaction.getValueDate().atStartOfDay() : null)
				.amount(transaction.getAmount() != null ? transaction.getAmount().doubleValue() : null)
				.currency(transaction.getCurrency())
				.description(transaction.getDescriptionRaw())
				.merchantId(transaction.getMerchantId())
				.categoryId(transaction.getCategoryId())
				.externalId(transaction.getExternalTxId())
				.statusId(transaction.getStatusId())
				.typeId(transaction.getTransactionType())
				.createdAt(transaction.getCreatedAt() != null ? transaction.getCreatedAt().toLocalDate() : null)
				.updatedAt(transaction.getUpdatedAt() != null ? transaction.getUpdatedAt().toLocalDate() : null)
				.build();
	}

	public void updateEntity(
			Transaction transaction,
			TransactionDTO transactionDTO,
			Long tenantId,
			String normalizedCurrency,
			LocalDateTime timestamp) {
		transaction.setTenantId(tenantId);
		transaction.setSourceAccountId(transactionDTO.getSourceAccountId());
		transaction.setDestinationAccountId(transactionDTO.getDestinationAccountId());
		transaction.setBookingDate(transactionDTO.getBookingDate().toLocalDate());
		transaction.setValueDate(transactionDTO.getValueDate() != null ? transactionDTO.getValueDate().toLocalDate() : null);
		transaction.setAmount(BigDecimal.valueOf(transactionDTO.getAmount()));
		transaction.setCurrency(normalizedCurrency);
		transaction.setDescriptionRaw(normalizeDescription(transactionDTO.getDescription()));
		transaction.setMerchantId(transactionDTO.getMerchantId());
		transaction.setCategoryId(transactionDTO.getCategoryId());
		transaction.setExternalTxId(normalizeExternalId(transactionDTO.getExternalId()));
		transaction.setStatusId(transactionDTO.getStatusId() != null ? transactionDTO.getStatusId() : 1L);
		transaction.setTransactionType(transactionDTO.getTypeId());
		transaction.setUpdatedAt(timestamp);
	}
}
