package es.triana.company.banking.model.db;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
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

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "iban")
    private String iban;

    @Column(name = "type")
    private Long type;

    @Column(name = "currency")
    private String currency;

    @Column(name = "last_balance_real")
    private Double lastBalanceReal;

    @Column(name = "last_balance_real_date")
    private LocalDateTime lastBalanceRealDate;

    @Column(name = "last_balance_available")
    private Double lastBalanceAvailable;

    @Column(name = "last_balance_available_date")
    private LocalDateTime lastBalanceAvailableDate;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
