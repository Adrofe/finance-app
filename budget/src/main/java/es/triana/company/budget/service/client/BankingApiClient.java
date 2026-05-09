package es.triana.company.budget.service.client;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import es.triana.company.budget.model.api.BankingApiResponse;
import es.triana.company.budget.model.api.BankingCategoryDTO;
import es.triana.company.budget.model.api.BankingTransactionDTO;

@Component
public class BankingApiClient {

    private final RestClient restClient;

    public BankingApiClient(@Value("${clients.banking.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public List<BankingCategoryDTO> getCategories(String bearerToken) {
        BankingApiResponse<List<BankingCategoryDTO>> response = restClient.get()
                .uri("/v1/api/categories")
                .header(HttpHeaders.AUTHORIZATION, normalizeAuthorizationHeader(bearerToken))
                .retrieve()
                .body(new ParameterizedTypeReference<BankingApiResponse<List<BankingCategoryDTO>>>() {});
        return response != null && response.getData() != null ? response.getData() : List.of();
    }

    public List<BankingTransactionDTO> getTransactions(String bearerToken, LocalDate startDate, LocalDate endDate) {
        BankingApiResponse<List<BankingTransactionDTO>> response = restClient.get()
            .uri(uriBuilder -> buildTransactionsDateRangeUri(uriBuilder, startDate, endDate))
            .header(HttpHeaders.AUTHORIZATION, normalizeAuthorizationHeader(bearerToken))
            .retrieve()
            .body(new ParameterizedTypeReference<BankingApiResponse<List<BankingTransactionDTO>>>() {});
        return response != null && response.getData() != null ? response.getData() : List.of();
    }

    private String normalizeAuthorizationHeader(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return bearerToken;
        }
        return bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken;
    }

        private URI buildTransactionsDateRangeUri(UriBuilder uriBuilder, LocalDate startDate, LocalDate endDate) {
        return uriBuilder
            .path("/v1/api/transactions/date-range")
            .queryParam("startDate", startDate)
            .queryParam("endDate", endDate)
            .build();
        }
}
