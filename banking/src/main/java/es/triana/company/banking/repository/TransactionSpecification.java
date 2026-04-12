package es.triana.company.banking.repository;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;

import es.triana.company.banking.model.api.TransactionFilterRequest;
import es.triana.company.banking.model.db.Tag;
import es.triana.company.banking.model.db.Transaction;

public class TransactionSpecification implements Specification<Transaction> {

    private final TransactionFilterRequest filter;
    private final Long tenantId;

    public TransactionSpecification(TransactionFilterRequest filter, Long tenantId) {
        this.filter = filter;
        this.tenantId = tenantId;
    }

    @Override
    public Predicate toPredicate(Root<Transaction> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();

        // Tenant isolation — always enforced
        predicates.add(cb.equal(root.get("tenantId"), tenantId));

        // Account (source OR destination)
        if (filter.getAccountId() != null) {
            Predicate isSource = cb.equal(root.get("sourceAccount").get("id"), filter.getAccountId());
            Predicate isDestination = cb.equal(root.get("destinationAccount").get("id"), filter.getAccountId());
            predicates.add(cb.or(isSource, isDestination));
        }

        if (filter.getCategoryId() != null) {
            predicates.add(cb.equal(root.get("category").get("id"), filter.getCategoryId()));
        }

        if (filter.getMerchantId() != null) {
            predicates.add(cb.equal(root.get("merchant").get("id"), filter.getMerchantId()));
        }

        if (filter.getStatusId() != null) {
            predicates.add(cb.equal(root.get("status").get("id"), filter.getStatusId()));
        }

        if (filter.getTypeId() != null) {
            predicates.add(cb.equal(root.get("transactionType").get("id"), filter.getTypeId()));
        }

        if (filter.getCurrency() != null && !filter.getCurrency().isBlank()) {
            predicates.add(cb.equal(root.get("currency"), filter.getCurrency().trim().toUpperCase()));
        }

        if (filter.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("bookingDate"), filter.getStartDate()));
        }

        if (filter.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("bookingDate"), filter.getEndDate()));
        }

        if (filter.getMinAmount() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.getMinAmount()));
        }

        if (filter.getMaxAmount() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.getMaxAmount()));
        }

        if (filter.getDescription() != null && !filter.getDescription().isBlank()) {
            predicates.add(cb.like(
                    cb.lower(root.get("descriptionRaw")),
                    "%" + filter.getDescription().trim().toLowerCase() + "%"));
        }

        // Tags — transaction must have at least one of the supplied tag ids
        if (filter.getTagIds() != null && !filter.getTagIds().isEmpty()) {
            Join<Transaction, Tag> tagJoin = root.join("tags", JoinType.INNER);
            predicates.add(tagJoin.get("id").in(filter.getTagIds()));
            query.distinct(true);
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }
}
