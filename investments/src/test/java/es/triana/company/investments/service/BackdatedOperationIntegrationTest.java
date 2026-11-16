package es.triana.company.investments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import es.triana.company.investments.client.EcbExchangeRateClient;
import es.triana.company.investments.client.MarketPriceClient;
import es.triana.company.investments.model.api.CreateOperationRequest;
import es.triana.company.investments.model.db.Investment;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentTypeCatalog;
import es.triana.company.investments.model.db.OperationType;
import es.triana.company.investments.repository.InvestmentInstrumentRepository;
import es.triana.company.investments.repository.InvestmentOperationRepository;
import es.triana.company.investments.repository.InvestmentRepository;
import es.triana.company.investments.repository.InvestmentTypeCatalogRepository;
import es.triana.company.investments.service.exception.InvestmentValidationException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:backdated-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS investments",
        "spring.jpa.properties.hibernate.default_schema=investments"
})
class BackdatedOperationIntegrationTest {
    @Autowired private OperationService service;
    @Autowired private InvestmentRepository investments;
    @Autowired private InvestmentInstrumentRepository instruments;
    @Autowired private InvestmentTypeCatalogRepository types;
    @Autowired private InvestmentOperationRepository operations;
    @MockitoBean private EcbExchangeRateClient ecb;
    @MockitoBean private MarketPriceClient marketPrices;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void persistsRebuiltLotsAndRollsBackAnInvalidHistoricalSale() {
        var type = types.saveAndFlush(InvestmentTypeCatalog.builder().code("CRYPTO_TEST").name("Crypto").build());
        var instrument = instruments.saveAndFlush(InvestmentInstrument.builder().typeId(type.getId()).code("BTC_TEST")
                .symbol("BTC_TEST").name("Bitcoin").currency("EUR").lastPrice(new BigDecimal("300")).build());
        LocalDateTime now = LocalDateTime.now();
        var position = investments.saveAndFlush(Investment.builder().tenantId(99L).typeId(type.getId()).instrumentId(instrument.getId())
                .name("Bitcoin").currency("EUR").quantity(BigDecimal.ZERO).investedAmount(BigDecimal.ZERO)
                .currentValueCalculated(BigDecimal.ZERO).createdAt(now).updatedAt(now).build());
        service.registerOperation(request(position.getId(), OperationType.BUY, "2024-01-01", "10", "100"));
        service.registerOperation(request(position.getId(), OperationType.BUY, "2024-03-01", "10", "200"));
        var laterSale = service.registerOperation(request(position.getId(), OperationType.SELL, "2024-04-01", "10", "300"));
        var earlierSale = service.registerOperation(request(position.getId(), OperationType.SELL, "2024-02-01", "5", "150"));

        var all = service.getByInvestment(position.getId(), 99L);
        var rebuiltLater = all.stream().filter(operation -> operation.id().equals(laterSale.id())).findFirst().orElseThrow();
        assertThat(earlierSale.fifoLots()).hasSize(1);
        assertThat(rebuiltLater.fifoLots()).hasSize(2);
        var stored = investments.findById(position.getId()).orElseThrow();
        assertThat(stored.getQuantity()).isEqualByComparingTo("5");
        assertThat(stored.getInvestedAmount()).isEqualByComparingTo("833.33");
        assertThat(stored.getCurrentValueCalculated()).isEqualByComparingTo("1500.00");
        assertThatThrownBy(() -> service.registerOperation(request(position.getId(), OperationType.SELL, "2023-12-01", "1", "150")))
                .isInstanceOf(InvestmentValidationException.class);
        assertThat(operations.findByInvestmentIdOrderByOperationDateAscIdAsc(position.getId())).hasSize(4);
        assertThat(service.getByInvestment(position.getId(), 99L).stream().filter(operation -> operation.id().equals(laterSale.id()))
                .findFirst().orElseThrow().fifoLots()).hasSize(2);
    }

    private CreateOperationRequest request(Long investmentId, OperationType type, String date, String quantity, String price) {
        return new CreateOperationRequest(investmentId, 99L, type, LocalDate.parse(date),
                new BigDecimal(quantity), new BigDecimal(price), BigDecimal.ZERO, "EUR", null);
    }
}