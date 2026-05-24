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
import java.util.ArrayList;
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

    /** Max symbols per batch request — TwelveData free tier allows 8 credits/min. */
    @Value("${investments.prices.providers.twelvedata.max-batch-size:8}")
    private int maxBatchSize;

    /** Milliseconds to wait between consecutive batch requests to stay within the rate limit. */
    @Value("${investments.prices.providers.twelvedata.batch-delay-ms:62000}")
    private long batchDelayMs;

    public MarketPriceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Optional<MarketQuote> fetchLatestQuote(InvestmentInstrument instrument) {
        return fetchFromTwelveDataSingle(instrument);
    }

    public Map<Long, MarketQuote> fetchLatestQuotes(List<InvestmentInstrument> instruments) {
        Map<Long, MarketQuote> result = new HashMap<>();
        if (instruments == null || instruments.isEmpty()) {
            return result;
        }

        List<InvestmentInstrument> valid = instruments.stream()
                .filter(i -> i.getCode() != null && !i.getCode().isBlank())
                .toList();

        if (valid.isEmpty()) {
            return result;
        }

        List<List<InvestmentInstrument>> chunks = partition(valid, maxBatchSize);
        LOG.info("Fetching quotes for {} instruments in {} batch(es) of up to {} (delay between batches: {}ms)",
                valid.size(), chunks.size(), maxBatchSize, batchDelayMs);

        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                LOG.info("Rate-limit pause: waiting {}ms before batch {}/{}", batchDelayMs, i + 1, chunks.size());
                try {
                    Thread.sleep(batchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Price fetch interrupted during rate-limit pause");
                    break;
                }
            }
            fetchBatch(chunks.get(i), result);
        }

        return result;
    }

    private void fetchBatch(List<InvestmentInstrument> chunk, Map<Long, MarketQuote> resultMap) {
        String symbolsCsv = chunk.stream()
                .map(i -> i.getCode().trim())
                .collect(Collectors.joining(","));

        try {
            JsonNode root = doGetJson(buildTimeSeriesUrl(symbolsCsv));
            JsonNode statusNode = root.get("status");

            if (statusNode != null && "error".equalsIgnoreCase(statusNode.asText())) {
                LOG.warn("TwelveData batch API error for symbols={}: {}", symbolsCsv,
                        root.path("message").asText("(no message)"));
                return;
            }

            if (chunk.size() == 1) {
                parseTimeSeriesNode(root, chunk.get(0))
                        .ifPresent(q -> resultMap.put(chunk.get(0).getId(), q));
                return;
            }

            for (InvestmentInstrument instrument : chunk) {
                JsonNode node = root.path(instrument.getCode().trim());
                if (!node.isMissingNode() && !node.isNull()) {
                    parseTimeSeriesNode(node, instrument)
                            .ifPresent(q -> resultMap.put(instrument.getId(), q));
                }
            }
        } catch (Exception ex) {
            LOG.warn("TwelveData batch fetch failed for symbols={}: {}", symbolsCsv, ex.getMessage());
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
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
