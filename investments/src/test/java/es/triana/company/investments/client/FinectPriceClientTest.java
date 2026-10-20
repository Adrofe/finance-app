package es.triana.company.investments.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import es.triana.company.investments.model.db.InvestmentInstrument;

class FinectPriceClientTest {

    @Test
    void fetchLatestQuote_routesInstrumentsWithFinectUrlToFinect() {
        FinectPriceClient finectPriceClient = mock(FinectPriceClient.class);
        MarketPriceClient marketPriceClient = new MarketPriceClient(finectPriceClient);
        InvestmentInstrument instrument = InvestmentInstrument.builder()
                .id(7L)
                .finectUrl("https://www.finect.com/fondos-inversion/LU0114721177-example")
                .build();
        MarketPriceClient.MarketQuote expected = new MarketPriceClient.MarketQuote(
                new BigDecimal("51.95"), "FINECT", "EUR");
        when(finectPriceClient.fetchLatestQuote(instrument)).thenReturn(Optional.of(expected));

        Optional<MarketPriceClient.MarketQuote> quote = marketPriceClient.fetchLatestQuote(instrument);

        assertThat(quote).contains(expected);
        verify(finectPriceClient).fetchLatestQuote(instrument);
    }

    @Test
    void fetchLatestQuote_fallsBackWhenFinectReturnsNoQuote() {
        FinectPriceClient finectPriceClient = mock(FinectPriceClient.class);
        MarketPriceClient marketPriceClient = new MarketPriceClient(finectPriceClient);
        InvestmentInstrument instrument = InvestmentInstrument.builder()
                .id(7L)
                .code("LU0114721177")
                .finectUrl("https://www.finect.com/fondos-inversion/LU0114721177-example")
                .build();
        when(finectPriceClient.fetchLatestQuote(instrument)).thenReturn(Optional.empty());

        Optional<MarketPriceClient.MarketQuote> quote = marketPriceClient.fetchLatestQuote(instrument);

        assertThat(quote).isEmpty();
        verify(finectPriceClient).fetchLatestQuote(instrument);
    }

    @Test
    void parseQuote_readsFinectNavAndConvertsSpanishDecimal() {
        FinectPriceClient client = new FinectPriceClient();
        InvestmentInstrument instrument = InvestmentInstrument.builder().currency("EUR").build();
        String html = "<div><span>51,95</span><span>€</span></div>"
                + "<span>Fecha de <!-- -->valor liquidativo:<!-- -->24/09/2026</span>";

        MarketPriceClient.MarketQuote quote = client.parseQuote(html, instrument).orElseThrow();

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("51.95"));
        assertThat(quote.currency()).isEqualTo("EUR");
        assertThat(quote.source()).isEqualTo("FINECT");
    }

    @Test
    void parseQuote_readsCurrentNavFromFinectDisplayedPriceMarkup() {
        FinectPriceClient client = new FinectPriceClient();
        InvestmentInstrument instrument = InvestmentInstrument.builder().currency("EUR").build();
        String html = "<div class=\"partials__StyledPercentLabel-sc-1bigw9a-1 hSJMZM\">"
                + "<span class=\"Label-sc-1esfagn-0 kDrrNc\"><span>8,74</span><span>€</span></span>"
                + "</div><span class=\"Subtitle__SubtitleISIN-sc-1i0tj6c-2\">Datos a "
                + "<time dateTime=\"2026-10-16T00:00:00.000Z\">16/10/2026</time></span>";

        MarketPriceClient.MarketQuote quote = client.parseQuote(html, instrument).orElseThrow();

        assertThat(quote.price()).isEqualByComparingTo(new BigDecimal("8.74"));
        assertThat(quote.source()).isEqualTo("FINECT");
    }
}