package es.triana.company.investments.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.triana.company.investments.model.db.InvestmentInstrument;

@Component
public class MarketPriceClient {

    private static final String SOURCE_TWELVEDATA = "TWELVEDATA";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    @Value("${investments.prices.providers.twelvedata.time-series-url}")
    private String twelveDataTimeSeriesUrl;

    @Value("${investments.prices.providers.twelvedata.api-key}")
    private String twelveDataApiKey;

    @Value("${investments.prices.providers.twelvedata.interval:1day}")
    private String twelveDataInterval;

    @Value("${investments.prices.providers.twelvedata.outputsize:1}")
    private int twelveDataOutputSize;

    public MarketPriceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Optional<MarketQuote> fetchLatestQuote(InvestmentInstrument instrument) {
        return fetchFromTwelveData(instrument);
    }

    private Optional<MarketQuote> fetchFromTwelveData(InvestmentInstrument instrument) {
        String symbol = instrument.getCode();
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        StringBuilder url = new StringBuilder(twelveDataTimeSeriesUrl)
                .append("?symbol=")
                .append(URLEncoder.encode(symbol.trim(), StandardCharsets.UTF_8))
                .append("&interval=")
                .append(URLEncoder.encode(twelveDataInterval, StandardCharsets.UTF_8))
                .append("&outputsize=")
                .append(twelveDataOutputSize)
                .append("&apikey=")
                .append(URLEncoder.encode(twelveDataApiKey, StandardCharsets.UTF_8));

        try {
            JsonNode root = doGetJson(url.toString());
            JsonNode statusNode = root.get("status");
            if (statusNode != null && "error".equalsIgnoreCase(statusNode.asText())) {
                return Optional.empty();
            }

            JsonNode values = root.path("values");
            if (!values.isArray() || values.isEmpty()) {
                return Optional.empty();
            }

            JsonNode latest = values.get(0);
            JsonNode priceNode = latest.get("close");
            if (priceNode == null || priceNode.isNull()) {
                return Optional.empty();
            }

            BigDecimal price = new BigDecimal(priceNode.asText());
            String currency = root.path("meta").path("currency").asText(instrument.getCurrency());
            return Optional.of(new MarketQuote(price, SOURCE_TWELVEDATA, currency));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private JsonNode doGetJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Quote provider returned HTTP " + response.statusCode());
        }

        return objectMapper.readTree(response.body());
    }

    public record MarketQuote(BigDecimal price, String source, String currency) {
    }
}
