package es.triana.company.investments.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.triana.company.investments.model.api.InvestmentInstrumentExposureDTO;
import es.triana.company.investments.model.db.InvestmentInstrumentExposure.Dimension;

class FinectExposureClientTest {

    @Test
    void parseExposures_readsBreakdownFromInitialState() throws Exception {
        FinectExposureClient client = new FinectExposureClient();
        String initialStateJson = """
                {
                  "fund": {
                    "fund": {
                      "model": {
                        "breakdown": [
                          {
                            "type": "regional-exposure",
                            "items": [
                              { "drawer": "Asia Emergente", "values": { "long": 94.13436126708984, "short": 0 } },
                              { "drawer": "Estados Unidos", "values": { "long": 1.5659199953079224, "short": 0 } }
                            ]
                          },
                          {
                            "type": "stock-sector",
                            "items": [
                              { "drawer": "Financial Services", "values": { "long": 34.46862030029297, "short": 0 } },
                              { "drawer": "Technology", "values": { "long": 5.392730236053467, "short": 0 } }
                            ]
                          },
                          {
                            "type": "stock-industry",
                            "items": [
                              { "drawer": "Semiconductors", "values": { "long": 8.25, "short": 0 } }
                            ]
                          }
                        ]
                      }
                    }
                  }
                }
                """;
        String html = "<html><body><script>window.INITIAL_STATE=\""
                + URLEncoder.encode(initialStateJson, StandardCharsets.UTF_8)
                + "\";</script></body></html>";

        Method parseExposures = FinectExposureClient.class.getDeclaredMethod("parseExposures", String.class);
        parseExposures.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<InvestmentInstrumentExposureDTO> exposures =
                (List<InvestmentInstrumentExposureDTO>) parseExposures.invoke(client, html);

        assertThat(exposures).hasSize(5);
        assertThat(exposures)
                .anySatisfy(exposure -> {
                    assertThat(exposure.getDimension()).isEqualTo(Dimension.REGION);
                    assertThat(exposure.getBucketName()).isEqualTo("Asia Emergente");
                    assertThat(exposure.getWeightPct()).isEqualByComparingTo(new BigDecimal("94.13436126708984"));
                })
                .anySatisfy(exposure -> {
                    assertThat(exposure.getDimension()).isEqualTo(Dimension.SECTOR);
                    assertThat(exposure.getBucketName()).isEqualTo("Financial Services");
                    assertThat(exposure.getWeightPct()).isEqualByComparingTo(new BigDecimal("34.46862030029297"));
                })
                .anySatisfy(exposure -> {
                    assertThat(exposure.getDimension()).isEqualTo(Dimension.INDUSTRY);
                    assertThat(exposure.getBucketName()).isEqualTo("Semiconductors");
                    assertThat(exposure.getWeightPct()).isEqualByComparingTo(new BigDecimal("8.25"));
                });
    }

    @Test
    void parseExposures_readsSectorFromAlternateBreakdownFields() throws Exception {
        FinectExposureClient client = new FinectExposureClient();
        String initialStateJson = """
                {
                  "fund": {
                    "fund": {
                      "model": {
                        "breakdown": [
                          {
                            "type": "style",
                            "title": "Exposición por sectores",
                            "rows": [
                              { "label": "Technology", "value": "25,50" },
                              { "name": "Financials", "percentage": "14.3" }
                            ]
                          }
                        ]
                      }
                    }
                  }
                }
                """;
        String html = "<html><body><script>window.INITIAL_STATE=\""
                + URLEncoder.encode(initialStateJson, StandardCharsets.UTF_8)
                + "\";</script></body></html>";

        Method parseExposures = FinectExposureClient.class.getDeclaredMethod("parseExposures", String.class);
        parseExposures.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<InvestmentInstrumentExposureDTO> exposures =
                (List<InvestmentInstrumentExposureDTO>) parseExposures.invoke(client, html);

        assertThat(exposures).hasSize(2);
        assertThat(exposures)
                .allSatisfy(exposure -> assertThat(exposure.getDimension()).isEqualTo(Dimension.SECTOR))
                .anySatisfy(exposure -> {
                    assertThat(exposure.getBucketName()).isEqualTo("Technology");
                    assertThat(exposure.getWeightPct()).isEqualByComparingTo(new BigDecimal("25.50"));
                })
                .anySatisfy(exposure -> {
                    assertThat(exposure.getBucketName()).isEqualTo("Financials");
                    assertThat(exposure.getWeightPct()).isEqualByComparingTo(new BigDecimal("14.3"));
                });
    }
}