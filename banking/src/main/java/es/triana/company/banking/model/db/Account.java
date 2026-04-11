package es.triana.company.banking.model.db;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "accounts", schema = "banking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "iban")
    private String iban;

    @ManyToOne
    @JoinColumn(name = "type")
    private AccountType accountType;

    @Column(name = "currency")
    private String currency;

    @Column(name = "last_balance_real", precision = 18, scale = 2)
    private BigDecimal lastBalanceReal;

    @Column(name = "last_balance_real_date")
    private LocalDateTime lastBalanceRealDate;

    @Column(name = "last_balance_available", precision = 18, scale = 2)
    private BigDecimal lastBalanceAvailable;

    @Column(name = "last_balance_available_date")
    private LocalDateTime lastBalanceAvailableDate;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
