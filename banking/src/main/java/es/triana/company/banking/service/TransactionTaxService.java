package es.triana.company.banking.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import es.triana.company.banking.model.api.TaxTypeDTO;
import es.triana.company.banking.model.api.TransactionTaxDTO;
import es.triana.company.banking.model.api.TransactionTaxRequest;
import es.triana.company.banking.model.db.TaxType;
import es.triana.company.banking.model.db.Transaction;
import es.triana.company.banking.model.db.TransactionTax;
import es.triana.company.banking.repository.TaxTypeRepository;
import es.triana.company.banking.repository.TransactionRepository;
import es.triana.company.banking.repository.TransactionTaxRepository;
import es.triana.company.banking.service.exception.TransactionNotFoundException;
import es.triana.company.banking.service.exception.TransactionTaxNotFoundException;
import es.triana.company.banking.service.exception.TransactionTaxValidationException;

@Service
public class TransactionTaxService {

    private final TransactionTaxRepository transactionTaxRepository;
    private final TransactionRepository transactionRepository;
    private final TaxTypeRepository taxTypeRepository;

    public TransactionTaxService(TransactionTaxRepository transactionTaxRepository, TransactionRepository transactionRepository, TaxTypeRepository taxTypeRepository) {
        this.transactionTaxRepository = transactionTaxRepository;
        this.transactionRepository = transactionRepository;
        this.taxTypeRepository = taxTypeRepository;
    }

    // ── Tax types catalog ─────────────────────────────────────────────────────

    public List<TaxTypeDTO> getAllTaxTypes() {
        return taxTypeRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toTaxTypeDto)
                .toList();
    }

    // ── Transaction tax CRUD ──────────────────────────────────────────────────

    public TransactionTaxDTO getTaxForTransaction(Long transactionId, Long tenantId) {
        return transactionTaxRepository
                .findByTransactionIdAndTenantId(transactionId, tenantId)
                .map(this::toDto)
                .orElseThrow(() -> new TransactionTaxNotFoundException(
                        "No tax record for transaction: " + transactionId));
    }

    /**
     * Creates or replaces the tax record for a transaction (upsert).
     */
    public TransactionTaxDTO saveTaxForTransaction(Long transactionId, TransactionTaxRequest request, Long tenantId) {
        if (request.getGrossAmount() == null) {
            throw new TransactionTaxValidationException("grossAmount is required");
        }
        if (request.getTaxAmount() == null) {
            throw new TransactionTaxValidationException("taxAmount is required");
        }
        if (request.getTaxTypeId() == null) {
            throw new TransactionTaxValidationException("taxTypeId is required");
        }

        Transaction transaction = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

        TaxType taxType = taxTypeRepository.findById(request.getTaxTypeId())
                .orElseThrow(() -> new TransactionTaxValidationException(
                        "Tax type not found: " + request.getTaxTypeId()));

        OffsetDateTime now = OffsetDateTime.now();

        TransactionTax tax = transactionTaxRepository
                .findByTransactionIdAndTenantId(transactionId, tenantId)
                .orElse(null);

        if (tax == null) {
            tax = TransactionTax.builder()
                    .tenantId(tenantId)
                    .transaction(transaction)
                    .grossAmount(request.getGrossAmount())
                    .taxAmount(request.getTaxAmount())
                    .taxType(taxType)
                    .notes(request.getNotes())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            tax.setGrossAmount(request.getGrossAmount());
            tax.setTaxAmount(request.getTaxAmount());
            tax.setTaxType(taxType);
            tax.setNotes(request.getNotes());
            tax.setUpdatedAt(now);
        }

        return toDto(transactionTaxRepository.save(tax));
    }

    public void deleteTaxForTransaction(Long transactionId, Long tenantId) {
        TransactionTax tax = transactionTaxRepository
                .findByTransactionIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new TransactionTaxNotFoundException(
                        "No tax record for transaction: " + transactionId));
        transactionTaxRepository.delete(tax);
    }

    /**
     * Returns all tax records for the tenant, used in the tax report.
     */
    public List<TransactionTaxDTO> getAllTaxesForTenant(Long tenantId) {
        return transactionTaxRepository
                .findAllByTenantIdOrderByTransaction_BookingDateDescIdDesc(tenantId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private TransactionTaxDTO toDto(TransactionTax t) {
        return TransactionTaxDTO.builder()
                .id(t.getId())
                .transactionId(t.getTransaction().getId())
                .grossAmount(t.getGrossAmount())
                .taxAmount(t.getTaxAmount())
                .taxType(toTaxTypeDto(t.getTaxType()))
                .notes(t.getNotes())
                .bookingDate(t.getTransaction().getBookingDate())
                .transactionDescription(t.getTransaction().getDescriptionRaw())
                .currency(t.getTransaction().getCurrency())
                .build();
    }

    private TaxTypeDTO toTaxTypeDto(TaxType tt) {
        return TaxTypeDTO.builder()
                .id(tt.getId())
                .code(tt.getCode())
                .name(tt.getName())
                .description(tt.getDescription())
                .build();
    }
}
