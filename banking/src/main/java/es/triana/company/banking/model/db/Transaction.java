package es.triana.company.banking.model.db;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transactions", schema = "banking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "destination_account_id")
    private Long destinationAccountId;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private Double amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description_raw")
    private String descriptionRaw;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "external_tx_id", length = 128)
    private String externalTxId;

    @Column(name = "status_id")
    private Long statusId;

    @Column(name = "transaction_type")
    private Long transactionType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
