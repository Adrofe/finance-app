package es.triana.company.investments.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.triana.company.investments.model.api.InvestmentInstrumentExposureDTO;
import es.triana.company.investments.model.db.InvestmentInstrument;
import es.triana.company.investments.model.db.InvestmentInstrumentExposure.Dimension;

@Component
public class FinectExposureClient {

    private static final Logger LOG = LoggerFactory.getLogger(FinectExposureClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern INITIAL_STATE_PATTERN = Pattern.compile(
            "window\\.INITIAL_STATE=\\\"([^\\\"]+)\\\";");
    private static final Pattern EXPOSURE_BLOCK_TITLE_PATTERN = Pattern.compile(
            "(?is)<p[^>]*>\\s*(Exposici[oó]n por sectores|Exposici[oó]n regional|Exposici[oó]n por pa[ií]ses|Exposici[oó]n por industrias?)\\s*</p>");
    private static final Pattern BLOCK_ROW_VALUE_PATTERN = Pattern.compile(
            "(?is)<span>\\s*(?:<div[^>]*>.*?</div>\\s*)*([^<]+?)\\s*</span>.*?<span>\\s*([0-9]{1,3}(?:[.,][0-9]+)?)%\\s*</span>");
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?is)<table[^>]*>(.*?)</table>");
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t[hd][^>]*>(.*?)</t[hd]>");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]+)?)\s*%");

    private final HttpClient httpClient;

    @Value("${investments.exposures.finect.base-url:https://www.finect.com/etfs/}")
    private String finectBaseUrl;

    @Value("${investments.exposures.finect.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36}")
    private String userAgent;

    public FinectExposureClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<InvestmentInstrumentExposureDTO> fetchExposures(InvestmentInstrument instrument) {
        if (instrument == null || instrument.getCode() == null || instrument.getCode().isBlank()) {
            return List.of();
        }

        String url = buildFinectUrl(instrument);
        try {
            String html = doGetText(url);
            List<InvestmentInstrumentExposureDTO> exposures = parseExposures(html);
            if (exposures.isEmpty()) {
                LOG.info("Finect scraper returned no compound exposures for instrumentId={} symbol={} url={}",
                        instrument.getId(), instrument.getSymbol(), url);
            } else {
                LOG.info("Finect scraper parsed {} compound exposures for instrumentId={} symbol={} url={}",
                        exposures.size(), instrument.getId(), instrument.getSymbol(), url);
            }
            return exposures;
        } catch (Exception ex) {
            LOG.warn("Finect scraper failed for instrumentId={} symbol={} url={}: {}",
                    instrument.getId(), instrument.getSymbol(), url, ex.getMessage());
            return List.of();
        }
    }

    private String buildFinectUrl(InvestmentInstrument instrument) {
        if (instrument.getFinectUrl() != null && !instrument.getFinectUrl().isBlank()) {
            return instrument.getFinectUrl().trim();
        }
        String code = instrument.getCode().trim();
        String slug = slugify(instrument.getName());
        String base = finectBaseUrl.endsWith("/") ? finectBaseUrl : finectBaseUrl + "/";
        return base + URLEncoder.encode(code + "-" + slug, StandardCharsets.UTF_8);
    }

    private List<InvestmentInstrumentExposureDTO> parseExposures(String html) {
        List<InvestmentInstrumentExposureDTO> exposures = parseInitialStateBreakdown(html);
        if (!exposures.isEmpty()) {
            return exposures;
        }

        exposures = parseExposureBlocks(html);
        if (!exposures.isEmpty()) {
            return exposures;
        }

        exposures = new ArrayList<>();
        Matcher tableMatcher = TABLE_PATTERN.matcher(html);
        while (tableMatcher.find()) {
            String tableHtml = tableMatcher.group(1);
            Dimension dimension = inferDimension(tableHtml);
            if (dimension == null) {
                continue;
            }

            exposures.addAll(parseTableRows(tableHtml, dimension));
        }

        if (exposures.isEmpty()) {
            exposures.addAll(parseLooseText(html));
        }

        return exposures;
    }

    private List<InvestmentInstrumentExposureDTO> parseInitialStateBreakdown(String html) {
        Matcher matcher = INITIAL_STATE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return List.of();
        }

        try {
            String encodedState = matcher.group(1);
            String decodedState = java.net.URLDecoder.decode(encodedState, StandardCharsets.UTF_8);
            JsonNode breakdown = OBJECT_MAPPER.readTree(decodedState)
                    .path("fund")
                    .path("fund")
                    .path("model")
                    .path("breakdown");

            if (!breakdown.isArray()) {
                return List.of();
            }

            List<InvestmentInstrumentExposureDTO> exposures = new ArrayList<>();
            for (JsonNode block : breakdown) {
                Dimension dimension = inferDimensionFromBreakdownBlock(block);
                if (dimension == null) {
                    continue;
                }

                JsonNode items = extractItemsArray(block);
                if (!items.isArray()) {
                    continue;
                }

                for (JsonNode item : items) {
                    String bucket = extractBucketName(item);
                    BigDecimal weight = extractWeightValue(item);
                    if (bucket.isBlank() || weight == null) {
                        continue;
                    }

                    exposures.add(InvestmentInstrumentExposureDTO.builder()
                            .dimension(dimension)
                            .bucketName(bucket)
                            .weightPct(weight)
                            .build());
                }
            }

            return exposures;
        } catch (Exception ex) {
            LOG.debug("Failed to parse Finect INITIAL_STATE breakdown: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<InvestmentInstrumentExposureDTO> parseExposureBlocks(String html) {
        List<InvestmentInstrumentExposureDTO> exposures = new ArrayList<>();
        List<ExposureBlockMatch> blocks = new ArrayList<>();
        Matcher matcher = EXPOSURE_BLOCK_TITLE_PATTERN.matcher(html);
        while (matcher.find()) {
            blocks.add(new ExposureBlockMatch(matcher.start(), matcher.end(), matcher.group(1)));
        }

        for (int index = 0; index < blocks.size(); index++) {
            ExposureBlockMatch block = blocks.get(index);
            Dimension dimension = inferDimension(block.title());
            if (dimension == null) {
                continue;
            }

            int nextBoundary = index + 1 < blocks.size() ? blocks.get(index + 1).start() : html.length();
            String snippet = html.substring(block.end(), nextBoundary);
            Matcher rowMatcher = BLOCK_ROW_VALUE_PATTERN.matcher(snippet);
            while (rowMatcher.find()) {
                String bucket = stripTags(rowMatcher.group(1)).trim();
                BigDecimal weight = parsePercent(rowMatcher.group(2));
                if (bucket.isBlank() || weight == null) {
                    continue;
                }

                exposures.add(InvestmentInstrumentExposureDTO.builder()
                        .dimension(dimension)
                        .bucketName(bucket)
                        .weightPct(weight)
                        .build());
            }
        }

        return exposures;
    }

    private Dimension inferDimensionFromBreakdownType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.contains("industry")) {
            return Dimension.INDUSTRY;
        }
        if (normalized.contains("sector")) {
            return Dimension.SECTOR;
        }
        if (normalized.contains("country") || normalized.contains("geograph")) {
            return Dimension.COUNTRY;
        }
        if (normalized.contains("region")) {
            return Dimension.REGION;
        }
        if (normalized.contains("develop") || normalized.contains("emerg") || normalized.contains("regime")) {
            return Dimension.MARKET_REGIME;
        }
        return null;
    }

    private Dimension inferDimensionFromBreakdownBlock(JsonNode block) {
        List<String> hints = Arrays.asList(
                block.path("type").asText(""),
                block.path("title").asText(""),
                block.path("name").asText(""),
                block.path("label").asText(""),
                block.path("id").asText(""));

        for (String hint : hints) {
            Dimension dimension = inferDimensionFromBreakdownType(hint);
            if (dimension != null) {
                return dimension;
            }
        }
        return null;
    }

    private JsonNode extractItemsArray(JsonNode block) {
        JsonNode items = block.path("items");
        if (items.isArray()) {
            return items;
        }
        items = block.path("rows");
        if (items.isArray()) {
            return items;
        }
        items = block.path("data");
        if (items.isArray()) {
            return items;
        }
        items = block.path("breakdown");
        if (items.isArray()) {
            return items;
        }
        return items;
    }

    private String extractBucketName(JsonNode item) {
        String bucket = item.path("drawer").asText("").trim();
        if (!bucket.isBlank()) {
            return bucket;
        }
        bucket = item.path("label").asText("").trim();
        if (!bucket.isBlank()) {
            return bucket;
        }
        bucket = item.path("name").asText("").trim();
        if (!bucket.isBlank()) {
            return bucket;
        }
        return item.path("title").asText("").trim();
    }

    private BigDecimal extractWeightValue(JsonNode item) {
        JsonNode values = item.path("values");
        BigDecimal value = readDecimal(values.path("long"));
        if (value != null) {
            return value;
        }
        value = readDecimal(values.path("net"));
        if (value != null) {
            return value;
        }
        value = readDecimal(values.path("value"));
        if (value != null) {
            return value;
        }
        value = readDecimal(item.path("value"));
        if (value != null) {
            return value;
        }
        value = readDecimal(item.path("long"));
        if (value != null) {
            return value;
        }
        value = readDecimal(item.path("weight"));
        if (value != null) {
            return value;
        }
        value = readDecimal(item.path("percentage"));
        if (value != null) {
            return value;
        }
        return readDecimal(item.path("pct"));
    }

    private BigDecimal readDecimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return parsePercent(node.asText());
        }
        return null;
    }

    private List<InvestmentInstrumentExposureDTO> parseTableRows(String tableHtml, Dimension dimension) {
        List<InvestmentInstrumentExposureDTO> exposures = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(tableHtml);
        boolean headerSeen = false;

        while (rowMatcher.find()) {
            String rowHtml = rowMatcher.group(1);
            List<String> cells = extractCells(rowHtml);
            if (cells.isEmpty()) {
                continue;
            }

            if (!headerSeen && rowHtml.toLowerCase(Locale.ROOT).contains("<th")) {
                headerSeen = true;
                continue;
            }

            String bucket = null;
            BigDecimal weight = null;
            for (String cell : cells) {
                Optional<BigDecimal> maybeWeight = extractPercent(cell);
                if (maybeWeight.isPresent()) {
                    weight = maybeWeight.get();
                    continue;
                }
                String text = stripTags(cell).trim();
                if (!text.isBlank() && bucket == null) {
                    bucket = text;
                }
            }

            if (bucket == null || weight == null) {
                continue;
            }

            exposures.add(InvestmentInstrumentExposureDTO.builder()
                    .dimension(dimension)
                    .bucketName(bucket)
                    .weightPct(weight)
                    .build());
        }

        return exposures;
    }

    private List<InvestmentInstrumentExposureDTO> parseLooseText(String html) {
        List<InvestmentInstrumentExposureDTO> exposures = new ArrayList<>();
        String text = stripTags(html).replace('\u00A0', ' ');
        String normalized = text.replaceAll("\\s+", " ").trim();
        Matcher matcher = Pattern.compile("(?i)([A-Za-zÀ-ÿ0-9 &.'()/\\-]{2,80}?)\\s+([0-9]{1,3}(?:[.,][0-9]+)?)%").matcher(normalized);
        while (matcher.find()) {
            String bucket = matcher.group(1).trim();
            BigDecimal weight = parsePercent(matcher.group(2));
            Dimension dimension = guessDimensionFromContext(normalized, matcher.start());
            if (dimension == null || bucket.isBlank() || weight == null) {
                continue;
            }
            exposures.add(InvestmentInstrumentExposureDTO.builder()
                    .dimension(dimension)
                    .bucketName(bucket)
                    .weightPct(weight)
                    .build());
        }
        return exposures;
    }

    private Dimension inferDimension(String snippet) {
        String normalized = stripTags(snippet).toLowerCase(Locale.ROOT);
        if (normalized.contains("country") || normalized.contains("país") || normalized.contains("pais")) {
            return Dimension.COUNTRY;
        }
        if (normalized.contains("region") || normalized.contains("región") || normalized.contains("region")) {
            return Dimension.REGION;
        }
        if (normalized.contains("desarroll") || normalized.contains("emerg") || normalized.contains("regime")) {
            return Dimension.MARKET_REGIME;
        }
        if (normalized.contains("sector")) {
            return Dimension.SECTOR;
        }
        if (normalized.contains("industry") || normalized.contains("industria")) {
            return Dimension.INDUSTRY;
        }
        return null;
    }

    private Dimension guessDimensionFromContext(String text, int position) {
        int start = Math.max(0, position - 400);
        int end = Math.min(text.length(), position + 200);
        String context = text.substring(start, end).toLowerCase(Locale.ROOT);
        if (context.contains("country") || context.contains("país") || context.contains("pais")) {
            return Dimension.COUNTRY;
        }
        if (context.contains("region") || context.contains("región")) {
            return Dimension.REGION;
        }
        if (context.contains("desarroll") || context.contains("emerg") || context.contains("regime")) {
            return Dimension.MARKET_REGIME;
        }
        if (context.contains("sector")) {
            return Dimension.SECTOR;
        }
        if (context.contains("industry") || context.contains("industria")) {
            return Dimension.INDUSTRY;
        }
        return null;
    }

    private List<String> extractCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
        while (cellMatcher.find()) {
            cells.add(cellMatcher.group(1));
        }
        return cells;
    }

    private Optional<BigDecimal> extractPercent(String text) {
        Matcher matcher = PERCENT_PATTERN.matcher(stripTags(text));
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.ofNullable(parsePercent(matcher.group(1)));
    }

    private BigDecimal parsePercent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.contains(",")) {
            normalized = normalized.replace(".", "").replace(',', '.');
        } else {
            normalized = normalized.replace(",", "");
        }
        return new BigDecimal(normalized);
    }

    private String stripTags(String html) {
        return html == null ? "" : html.replaceAll("(?is)<[^>]+>", " ").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&");
    }

    private String slugify(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String doGetText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Finect provider returned HTTP " + response.statusCode());
        }

        return response.body();
    }

    private record ExposureBlockMatch(int start, int end, String title) {
    }
}