package es.triana.company.banking.model.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TransactionFilterRequest {

    /** Filter by source OR destination account */
    private Long accountId;

    private Long categoryId;

    /** Transactions that have ANY of these tags */
    private List<Long> tagIds;

    private Long merchantId;

    private Long statusId;

    private Long typeId;

    /** ISO 4217, e.g. "EUR". Case-insensitive. */
    private String currency;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal minAmount;

    private BigDecimal maxAmount;

    /** Partial, case-insensitive match on description */
    private String description;

    // ── Pagination ─────────────────────────────────────────────────────────────

    private int page = 0;

    /** Max 100 */
    private int size = 20;

    /**
     * Allowed values: bookingDate, valueDate, amount, createdAt, id.
     * Defaults to bookingDate.
     */
    private String sortBy = "bookingDate";

    /** ASC or DESC. Defaults to DESC. */
    private String sortDirection = "DESC";
}
