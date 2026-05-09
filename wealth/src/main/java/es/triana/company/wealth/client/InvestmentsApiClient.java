package es.triana.company.wealth.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import es.triana.company.wealth.client.dto.ApiListResponse;
import es.triana.company.wealth.client.dto.InvestmentPositionDTO;

@Component
public class InvestmentsApiClient {

    private static final Logger log = LoggerFactory.getLogger(InvestmentsApiClient.class);

    private final RestClient restClient;

    public InvestmentsApiClient(@Value("${clients.investments.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public List<InvestmentPositionDTO> getInvestments(String bearerToken) {
        log.debug("Fetching investments from investments service");
        ApiListResponse<InvestmentPositionDTO> response = restClient.get()
                .uri("/v1/api/investments")
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Investments API error: {} {}", res.getStatusCode(), req.getURI());
                    throw new RuntimeException("Investments API returned error: " + res.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }
}
