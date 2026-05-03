package es.triana.company.investments.model.db;

import java.math.BigDecimal;
import java.time.LocalDate;

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
@Table(name = "exchange_rates", schema = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    /** How many toCurrency units equal 1 fromCurrency. E.g. EUR->USD = 1.0850 */
    @Column(name = "rate", nullable = false, precision = 18, scale = 10)
    private BigDecimal rate;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;
}
