package es.triana.company.investments.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import es.triana.company.investments.model.db.InvestmentInstrument;

@Component
public class FinectPriceClient {

    private static final Logger LOG = LoggerFactory.getLogger(FinectPriceClient.class);
    private static final String SOURCE_FINECT = "FINECT";
    private static final Pattern NAV_PATTERN = Pattern.compile(
            "(?i)([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]+|[0-9]+(?:\\.[0-9]+)?)\\s*(?:€|EUR)\\s*Fecha\\s+(?:de\\s+)?(?:actualizaci[oó]n\\s+)?valor\\s+liquidativo");

    private final HttpClient httpClient;

    @Value("${investments.prices.providers.finect.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    public FinectPriceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<MarketPriceClient.MarketQuote> fetchLatestQuote(InvestmentInstrument instrument) {
        if (instrument == null || instrument.getFinectUrl() == null || instrument.getFinectUrl().isBlank()) {
            return Optional.empty();
        }

        String url = instrument.getFinectUrl().trim();
        try {
            Optional<MarketPriceClient.MarketQuote> quote = parseQuote(doGetText(url), instrument);
            if (quote.isEmpty()) {
                LOG.warn("Finect price was not found for instrumentId={} url={}", instrument.getId(), url);
            }
            return quote;
        } catch (Exception ex) {
            LOG.warn("Finect price scraper failed for instrumentId={} url={}: {}", instrument.getId(), url, ex.getMessage());
            return Optional.empty();
        }
    }

    Optional<MarketPriceClient.MarketQuote> parseQuote(String html, InvestmentInstrument instrument) {
        String text = html.replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ");
        Matcher matcher = NAV_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        BigDecimal price = parseSpanishPrice(matcher.group(1));
        return Optional.of(new MarketPriceClient.MarketQuote(price, SOURCE_FINECT, instrument.getCurrency()));
    }

    private BigDecimal parseSpanishPrice(String rawPrice) {
        String normalized = rawPrice.replace(" ", "");
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(',', '.');
        }
        return new BigDecimal(normalized);
    }

    private String doGetText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Finect returned HTTP " + response.statusCode());
        }
        return response.body();
    }
}