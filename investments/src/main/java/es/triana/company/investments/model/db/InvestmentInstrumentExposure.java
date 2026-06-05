package es.triana.company.investments.model.db;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "investment_instrument_exposures", schema = "investments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentInstrumentExposure {

    public enum Dimension {
        COUNTRY,
        REGION,
        SECTOR,
        INDUSTRY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    private InvestmentInstrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "dimension", nullable = false, length = 20)
    private Dimension dimension;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private InvestmentCountryCatalog country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private InvestmentRegionCatalog region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sector_id")
    private InvestmentSectorCatalog sector;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "industry_id")
    private InvestmentIndustryCatalog industry;

    @Column(name = "weight_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal weightPct;
}
