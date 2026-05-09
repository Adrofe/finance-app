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
import es.triana.company.wealth.client.dto.BankingAccountDTO;

@Component
public class BankingApiClient {

    private static final Logger log = LoggerFactory.getLogger(BankingApiClient.class);

    private final RestClient restClient;

    public BankingApiClient(@Value("${clients.banking.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public List<BankingAccountDTO> getAccounts(String bearerToken) {
        log.debug("Fetching accounts from banking service");
        ApiListResponse<BankingAccountDTO> response = restClient.get()
                .uri("/v1/api/accounts")
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    log.error("Banking API error: {} {}", res.getStatusCode(), req.getURI());
                    throw new RuntimeException("Banking API returned error: " + res.getStatusCode());
                })
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.getData() == null) {
            return List.of();
        }
        return response.getData();
    }
}
