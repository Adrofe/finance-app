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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String SOURCE_SCRAPER = "SCRAPER";

    private static final Pattern YAHOO_PRICE_PATTERN =
        Pattern.compile("\\\"regularMarketPrice\\\":\\{\\\"raw\\\":([0-9]+(?:\\\\.[0-9]+)?)");
    private static final Pattern YAHOO_CURRENCY_PATTERN =
        Pattern.compile("\\\"currency\\\":\\\"([A-Z]{3})\\\"");
    private static final Pattern TWELVEDATA_PRICE_CURRENCY_PATTERN =
        Pattern.compile(
            "stats-symbol-price[^>]*>\\s*<span>\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)\\s*</span>\\s*<span[^>]*stats-symbol-price__currency[^>]*>\\s*([A-Za-z]{3})\\s*</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TWELVEDATA_PRICE_ONLY_PATTERN =
        Pattern.compile(
            "stats-symbol-price[^>]*>\\s*<span>\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)\\s*</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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

    @Value("${investments.prices.scraper.enabled:false}")
    private boolean scraperEnabled;

    @Value("${investments.prices.scraper.yahoo-url:https://finance.yahoo.com/quote/}")
    private String yahooQuoteBaseUrl;

    @Value("${investments.prices.scraper.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String scraperUserAgent;

    public MarketPriceClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<MarketQuote> fetchLatestQuote(InvestmentInstrument instrument) {
        Optional<MarketQuote> quote = fetchFromTwelveDataSingle(instrument);
        if (quote.isPresent()) {
            return quote;
        }
        return fetchFromScraper(instrument);
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

        if (scraperEnabled) {
            for (InvestmentInstrument instrument : valid) {
                if (!result.containsKey(instrument.getId())) {
                    fetchFromScraper(instrument).ifPresent(quote -> result.put(instrument.getId(), quote));
                }
            }
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

    private Optional<MarketQuote> fetchFromScraper(InvestmentInstrument instrument) {
        if (!scraperEnabled) {
            return Optional.empty();
        }

        if (instrument.getScraperUrl() != null && !instrument.getScraperUrl().isBlank()) {
            for (String candidate : getScraperCandidates(instrument)) {
                Optional<MarketQuote> customQuote = fetchFromCustomUrl(instrument.getScraperUrl(), candidate, instrument);
                if (customQuote.isPresent()) {
                    return customQuote;
                }
            }
        }

        for (String candidate : getScraperCandidates(instrument)) {
            Optional<MarketQuote> quote = fetchFromYahooQuotePage(candidate, instrument);
            if (quote.isPresent()) {
                return quote;
            }
        }
        return Optional.empty();
    }

    private List<String> getScraperCandidates(InvestmentInstrument instrument) {
        List<String> candidates = new ArrayList<>();
        if (instrument.getCode() != null && !instrument.getCode().isBlank()) {
            candidates.add(instrument.getCode().trim());
        }
        if (instrument.getSymbol() != null && !instrument.getSymbol().isBlank()) {
            String symbol = instrument.getSymbol().trim();
            if (!candidates.contains(symbol)) {
                candidates.add(symbol);
            }
        }
        return candidates;
    }

    private Optional<MarketQuote> fetchFromYahooQuotePage(String symbol, InvestmentInstrument instrument) {
        String url = buildYahooQuoteUrl(symbol);

        try {
            String html = doGetText(url, scraperUserAgent);
            return parseScrapedQuote(html, symbol, instrument, "Yahoo");
        } catch (Exception ex) {
            LOG.debug("Scraper fetch failed for symbol={} instrumentId={}: {}", symbol, instrument.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<MarketQuote> fetchFromCustomUrl(String url, String symbol, InvestmentInstrument instrument) {
        try {
            String resolvedUrl = url
                    .replace("{symbol}", instrument.getSymbol() == null ? "" : instrument.getSymbol().trim())
                    .replace("{code}", instrument.getCode() == null ? "" : instrument.getCode().trim())
                    .replace("{candidate}", symbol);
            String html = doGetText(resolvedUrl, scraperUserAgent);
            return parseScrapedQuote(html, symbol, instrument, "custom");
        } catch (Exception ex) {
            LOG.debug("Custom scraper fetch failed for symbol={} instrumentId={} url={}: {}",
                    symbol,
                    instrument.getId(),
                    url,
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<MarketQuote> parseScrapedQuote(String html, String symbol, InvestmentInstrument instrument, String sourceHint) {
        try {
            Matcher yahooPriceMatcher = YAHOO_PRICE_PATTERN.matcher(html);
            if (yahooPriceMatcher.find()) {
                BigDecimal price = parsePrice(yahooPriceMatcher.group(1));
                Matcher currencyMatcher = YAHOO_CURRENCY_PATTERN.matcher(html);
                String currency = currencyMatcher.find() ? currencyMatcher.group(1) : instrument.getCurrency();

                LOG.info("Scraper price fetched for instrumentId={} symbol={} price={} sourceHint={}",
                        instrument.getId(),
                        symbol,
                        price,
                        sourceHint);
                return Optional.of(new MarketQuote(price, SOURCE_SCRAPER, currency));
            }

            Matcher twelveDataMatcher = TWELVEDATA_PRICE_CURRENCY_PATTERN.matcher(html);
            if (twelveDataMatcher.find()) {
                BigDecimal price = parsePrice(twelveDataMatcher.group(1));
                String currency = twelveDataMatcher.group(2).toUpperCase();

                LOG.info("Scraper price fetched for instrumentId={} symbol={} price={} sourceHint={} provider=twelvedata-page",
                        instrument.getId(),
                        symbol,
                        price,
                        sourceHint);
                return Optional.of(new MarketQuote(price, SOURCE_SCRAPER, currency));
            }

            Matcher twelveDataPriceOnlyMatcher = TWELVEDATA_PRICE_ONLY_PATTERN.matcher(html);
            if (twelveDataPriceOnlyMatcher.find()) {
                BigDecimal price = parsePrice(twelveDataPriceOnlyMatcher.group(1));
                String currency = instrument.getCurrency();

                LOG.info("Scraper price fetched for instrumentId={} symbol={} price={} sourceHint={} provider=twelvedata-page-price-only",
                        instrument.getId(),
                        symbol,
                        price,
                        sourceHint);
                return Optional.of(new MarketQuote(price, SOURCE_SCRAPER, currency));
            }

            LOG.debug("Scraper: no known price pattern found for symbol={} instrumentId={} source={}",
                    symbol,
                    instrument.getId(),
                    sourceHint);
            return Optional.empty();
        } catch (Exception ex) {
            LOG.debug("Scraper parse failed for symbol={} instrumentId={} source={}: {}",
                    symbol,
                    instrument.getId(),
                    sourceHint,
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private BigDecimal parsePrice(String rawPrice) {
        return new BigDecimal(rawPrice.replace(",", ""));
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

    private String buildYahooQuoteUrl(String symbol) {
        String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        String base = yahooQuoteBaseUrl.endsWith("/") ? yahooQuoteBaseUrl : yahooQuoteBaseUrl + "/";
        return base + encoded + "?p=" + encoded;
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

    private String doGetText(String url, String userAgent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Scraper provider returned HTTP " + response.statusCode());
        }

        return response.body();
    }

    public record MarketQuote(BigDecimal price, String source, String currency) {
    }
}
