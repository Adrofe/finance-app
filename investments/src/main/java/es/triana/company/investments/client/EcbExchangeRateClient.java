package es.triana.company.investments.client;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import es.triana.company.investments.model.db.ExchangeRate;

/**
 * Fetches EUR foreign exchange reference rates from the European Central Bank (ECB).
 * The ECB publishes daily XML feeds; all rates are relative to EUR (base currency).
 *
 * Daily feed   : eurofxref-daily.xml   (today only)
 * Last 90 days : eurofxref-hist-90d.xml
 */
@Component
public class EcbExchangeRateClient {

    private static final Logger LOG = LoggerFactory.getLogger(EcbExchangeRateClient.class);
    private static final String SOURCE = "ECB";
    private static final String BASE_CURRENCY = "EUR";

    private final HttpClient httpClient;

    @Value("${investments.exchange-rates.providers.ecb.daily-url}")
    private String dailyUrl;

    @Value("${investments.exchange-rates.providers.ecb.hist90d-url}")
    private String hist90dUrl;

    public EcbExchangeRateClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Fetches only today's rates. */
    public List<ExchangeRate> fetchDailyRates() {
        return fetchAndParse(dailyUrl);
    }

    /** Fetches the last 90 days of rates (useful for initial DB population). */
    public List<ExchangeRate> fetchLast90DaysRates() {
        return fetchAndParse(hist90dUrl);
    }

    /**
     * Fetches rates for a specific date from the 90-day historical feed.
     * Returns an empty list when the date is not present in the feed.
     */
    public List<ExchangeRate> fetchRatesForDate(LocalDate date) {
        return fetchLast90DaysRates().stream()
                .filter(rate -> date.equals(rate.getAsOf()))
                .toList();
    }

    private List<ExchangeRate> fetchAndParse(String url) {
        List<ExchangeRate> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warn("ECB API returned status {} for URL {}", response.statusCode(), url);
                return result;
            }

            result = parseXml(response.body());
            LOG.debug("ECB: parsed {} exchange rate entries from {}", result.size(), url);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.error("Error fetching ECB exchange rates from {}: {}", url, e.getMessage());
        }
        return result;
    }

    /**
     * Parses the ECB gesmes:Envelope XML. Structure:
     * <pre>
     *   &lt;gesmes:Envelope&gt;
     *     &lt;Cube&gt;
     *       &lt;Cube time="2026-05-01"&gt;
     *         &lt;Cube currency="USD" rate="1.0850"/&gt;
     *         ...
     *       &lt;/Cube&gt;
     *     &lt;/Cube&gt;
     *   &lt;/gesmes:Envelope&gt;
     * </pre>
     * Base currency is always EUR.
     */
    private List<ExchangeRate> parseXml(String xml) {
        List<ExchangeRate> rates = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity processing to prevent XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // Find all <Cube time="..."> elements (day-level nodes)
            NodeList dayNodes = doc.getElementsByTagName("Cube");
            for (int i = 0; i < dayNodes.getLength(); i++) {
                Element node = (Element) dayNodes.item(i);
                if (!node.hasAttribute("time")) {
                    continue;
                }
                LocalDate asOf = LocalDate.parse(node.getAttribute("time"));

                // Child <Cube currency="..." rate="..."> elements
                NodeList rateNodes = node.getChildNodes();
                for (int j = 0; j < rateNodes.getLength(); j++) {
                    if (!(rateNodes.item(j) instanceof Element rateNode)) {
                        continue;
                    }
                    String currency = rateNode.getAttribute("currency");
                    String rateStr = rateNode.getAttribute("rate");
                    if (currency.isBlank() || rateStr.isBlank()) {
                        continue;
                    }
                    rates.add(ExchangeRate.builder()
                            .fromCurrency(BASE_CURRENCY)
                            .toCurrency(currency)
                            .rate(new BigDecimal(rateStr))
                            .source(SOURCE)
                            .asOf(asOf)
                            .build());
                }
            }
        } catch (Exception e) {
            LOG.error("Error parsing ECB XML: {}", e.getMessage());
        }
        return rates;
    }
}
