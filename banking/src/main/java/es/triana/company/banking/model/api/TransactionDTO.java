package es.triana.company.banking.model.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class TransactionDTO {
    private Long id;
    private Long sourceAccountId;
    private Long destinationAccountId;
    private LocalDateTime bookingDate;
    private LocalDateTime valueDate;
    private Double amount;
    private String currency;
    private String description;
    private Long merchantId;
    private String merchantName;
    private Long categoryId;
    private List<Long> tagIds;
    private String externalId;
    private Long statusId;
    private Long typeId;
    private Long linkedTransactionId;
    private LocalDate createdAt;
    private LocalDate updatedAt;
}
