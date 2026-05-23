package es.triana.company.investments.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.triana.company.investments.model.db.InvestmentInstrument;

@Component
public class MarketPriceClient {

    private static final Logger LOG = LoggerFactory.getLogger(MarketPriceClient.class);
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
        return fetchFromTwelveDataSingle(instrument);
    }

    public Map<Long, MarketQuote> fetchLatestQuotes(List<InvestmentInstrument> instruments) {
        Map<Long, MarketQuote> quotesByInstrumentId = new HashMap<>();
        if (instruments == null || instruments.isEmpty()) {
            return quotesByInstrumentId;
        }

        List<InvestmentInstrument> validInstruments = instruments.stream()
                .filter(instrument -> instrument.getCode() != null && !instrument.getCode().isBlank())
                .toList();

        if (validInstruments.isEmpty()) {
            return quotesByInstrumentId;
        }

        String symbolsCsv = validInstruments.stream()
                .map(instrument -> instrument.getCode().trim())
                .collect(Collectors.joining(","));

        try {
            JsonNode root = doGetJson(buildTimeSeriesUrl(symbolsCsv));
            JsonNode statusNode = root.get("status");

            if (statusNode != null && "error".equalsIgnoreCase(statusNode.asText())) {
                String message = root.path("message").asText("(no message)");
                LOG.warn("TwelveData batch API error for symbols={}: {}", symbolsCsv, message);
                return fallbackSingleFetch(validInstruments, quotesByInstrumentId);
            }

            if (validInstruments.size() == 1) {
                InvestmentInstrument single = validInstruments.get(0);
                parseTimeSeriesNode(root, single).ifPresent(quote -> quotesByInstrumentId.put(single.getId(), quote));
                return quotesByInstrumentId;
            }

            for (InvestmentInstrument instrument : validInstruments) {
                JsonNode node = root.path(instrument.getCode().trim());
                if (node.isMissingNode() || node.isNull()) {
                    continue;
                }

                parseTimeSeriesNode(node, instrument).ifPresent(quote -> quotesByInstrumentId.put(instrument.getId(), quote));
            }

            if (quotesByInstrumentId.size() != validInstruments.size()) {
                List<InvestmentInstrument> unresolved = validInstruments.stream()
                        .filter(instrument -> !quotesByInstrumentId.containsKey(instrument.getId()))
                        .toList();
                fallbackSingleFetch(unresolved, quotesByInstrumentId);
            }

            return quotesByInstrumentId;
        } catch (Exception ex) {
            LOG.warn("TwelveData batch fetch failed for symbols={}: {}", symbolsCsv, ex.getMessage());
            return fallbackSingleFetch(validInstruments, quotesByInstrumentId);
        }
    }

    private Map<Long, MarketQuote> fallbackSingleFetch(List<InvestmentInstrument> instruments, Map<Long, MarketQuote> quotesByInstrumentId) {
        for (InvestmentInstrument instrument : instruments) {
            fetchFromTwelveDataSingle(instrument).ifPresent(quote -> quotesByInstrumentId.put(instrument.getId(), quote));
        }
        return quotesByInstrumentId;
    }

    private Optional<MarketQuote> fetchFromTwelveDataSingle(InvestmentInstrument instrument) {
        String symbol = instrument.getCode();
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = doGetJson(buildTimeSeriesUrl(symbol.trim()));
            return parseTimeSeriesNode(root, instrument);
        } catch (Exception ex) {
            LOG.warn("TwelveData fetch failed for symbol={} code={}: {}", instrument.getSymbol(), symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<MarketQuote> parseTimeSeriesNode(JsonNode root, InvestmentInstrument instrument) {
        JsonNode statusNode = root.get("status");
        if (statusNode != null && "error".equalsIgnoreCase(statusNode.asText())) {
            String message = root.path("message").asText("(no message)");
            LOG.warn("TwelveData API error for symbol={} code={}: {}", instrument.getSymbol(), instrument.getCode(), message);
            return Optional.empty();
        }

        JsonNode values = root.path("values");
        if (!values.isArray() || values.isEmpty()) {
            LOG.debug("TwelveData: no values array for symbol={} code={}", instrument.getSymbol(), instrument.getCode());
            return Optional.empty();
        }

        JsonNode latest = values.get(0);
        JsonNode priceNode = latest.get("close");
        if (priceNode == null || priceNode.isNull()) {
            LOG.debug("TwelveData: no 'close' price for symbol={} code={}", instrument.getSymbol(), instrument.getCode());
            return Optional.empty();
        }

        BigDecimal price = new BigDecimal(priceNode.asText());
        String currency = root.path("meta").path("currency").asText(instrument.getCurrency());
        return Optional.of(new MarketQuote(price, SOURCE_TWELVEDATA, currency));
    }

    private String buildTimeSeriesUrl(String symbolParam) {
        return new StringBuilder(twelveDataTimeSeriesUrl)
                .append("?symbol=")
                .append(URLEncoder.encode(symbolParam, StandardCharsets.UTF_8))
                .append("&interval=")
                .append(URLEncoder.encode(twelveDataInterval, StandardCharsets.UTF_8))
                .append("&outputsize=")
                .append(twelveDataOutputSize)
                .append("&apikey=")
                .append(URLEncoder.encode(twelveDataApiKey, StandardCharsets.UTF_8))
                .toString();
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
