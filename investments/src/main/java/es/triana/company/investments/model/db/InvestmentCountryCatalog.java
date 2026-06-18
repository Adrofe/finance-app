package es.triana.company.investments.model.db;

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
@Table(name = "investment_countries", schema = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentCountryCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 2, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;
}
