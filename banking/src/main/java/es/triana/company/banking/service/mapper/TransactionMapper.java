package es.triana.company.banking.service.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import es.triana.company.banking.model.api.TransactionDTO;
import es.triana.company.banking.model.db.Account;
import es.triana.company.banking.model.db.Category;
import es.triana.company.banking.model.db.Merchant;
import es.triana.company.banking.model.db.Tag;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.model.db.TransactionStatus;
import es.triana.company.banking.model.db.TransactionType;

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
			Account sourceAccount,
			Account destinationAccount,
			Merchant merchant,
			Category category,
			TransactionStatus status,
			TransactionType transactionType,
			Set<Tag> tags,
			Long tenantId,
			String normalizedCurrency,
			LocalDateTime timestamp) {
		return Transaction.builder()
				.tenantId(tenantId)
				.sourceAccount(sourceAccount)
				.destinationAccount(destinationAccount)
				.bookingDate(transactionDTO.getBookingDate().toLocalDate())
				.valueDate(transactionDTO.getValueDate() != null ? transactionDTO.getValueDate().toLocalDate() : null)
				.amount(BigDecimal.valueOf(transactionDTO.getAmount()))
				.currency(normalizedCurrency)
				.descriptionRaw(normalizeDescription(transactionDTO.getDescription()))
				.merchant(merchant)
				.category(category)
				.tags(tags)
				.externalTxId(normalizeExternalId(transactionDTO.getExternalId()))
				.status(status)
				.transactionType(transactionType)
				.createdAt(timestamp)
				.updatedAt(timestamp)
				.build();
	}

	public TransactionDTO toDto(Transaction transaction) {
		return TransactionDTO.builder()
				.tenantId(transaction.getTenantId())
				.sourceAccountId(transaction.getSourceAccount() != null ? transaction.getSourceAccount().getId() : null)
				.destinationAccountId(transaction.getDestinationAccount() != null ? transaction.getDestinationAccount().getId() : null)
				.bookingDate(transaction.getBookingDate() != null ? transaction.getBookingDate().atStartOfDay() : null)
				.valueDate(transaction.getValueDate() != null ? transaction.getValueDate().atStartOfDay() : null)
				.amount(transaction.getAmount() != null ? transaction.getAmount().doubleValue() : null)
				.currency(transaction.getCurrency())
				.description(transaction.getDescriptionRaw())
				.merchantId(transaction.getMerchant() != null ? transaction.getMerchant().getId() : null)
				.categoryId(transaction.getCategory() != null ? transaction.getCategory().getId() : null)
				.tagIds(mapTagIds(transaction.getTags()))
				.externalId(transaction.getExternalTxId())
				.statusId(transaction.getStatus() != null ? transaction.getStatus().getId() : null)
				.typeId(transaction.getTransactionType() != null ? transaction.getTransactionType().getId() : null)
				.createdAt(transaction.getCreatedAt() != null ? transaction.getCreatedAt().toLocalDate() : null)
				.updatedAt(transaction.getUpdatedAt() != null ? transaction.getUpdatedAt().toLocalDate() : null)
				.build();
	}

	public void updateEntity(
			Transaction transaction,
			TransactionDTO transactionDTO,
			Account sourceAccount,
			Account destinationAccount,
			Merchant merchant,
			Category category,
			TransactionStatus status,
			TransactionType transactionType,
			Set<Tag> tags,
			Long tenantId,
			String normalizedCurrency,
			LocalDateTime timestamp) {
		transaction.setTenantId(tenantId);
		transaction.setSourceAccount(sourceAccount);
		transaction.setDestinationAccount(destinationAccount);
		transaction.setBookingDate(transactionDTO.getBookingDate().toLocalDate());
		transaction.setValueDate(transactionDTO.getValueDate() != null ? transactionDTO.getValueDate().toLocalDate() : null);
		transaction.setAmount(BigDecimal.valueOf(transactionDTO.getAmount()));
		transaction.setCurrency(normalizedCurrency);
		transaction.setDescriptionRaw(normalizeDescription(transactionDTO.getDescription()));
		transaction.setMerchant(merchant);
		transaction.setCategory(category);
		transaction.setTags(tags);
		transaction.setExternalTxId(normalizeExternalId(transactionDTO.getExternalId()));
		transaction.setStatus(status);
		transaction.setTransactionType(transactionType);
		transaction.setUpdatedAt(timestamp);
	}

	private List<Long> mapTagIds(Set<Tag> tags) {
		if (tags == null || tags.isEmpty()) {
			return List.of();
		}

		return tags.stream()
				.map(Tag::getId)
				.sorted()
				.toList();
	}
}
