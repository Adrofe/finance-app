package es.triana.company.wealth.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import es.triana.company.wealth.client.BankingApiClient;
import es.triana.company.wealth.client.InvestmentsApiClient;
import es.triana.company.wealth.client.dto.BankingAccountDTO;
import es.triana.company.wealth.client.dto.InvestmentPositionDTO;
import es.triana.company.wealth.model.api.WealthSnapshotCreateRequestDTO;
import es.triana.company.wealth.model.api.WealthSnapshotDTO;
import es.triana.company.wealth.model.api.WealthSnapshotItemInputDTO;

@Service
public class WealthIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WealthIngestionService.class);

    private final BankingApiClient bankingApiClient;
    private final InvestmentsApiClient investmentsApiClient;
    private final WealthService wealthService;

    public WealthIngestionService(BankingApiClient bankingApiClient,
                                  InvestmentsApiClient investmentsApiClient,
                                  WealthService wealthService) {
        this.bankingApiClient = bankingApiClient;
        this.investmentsApiClient = investmentsApiClient;
        this.wealthService = wealthService;
    }

    /**
     * Force refresh of today's snapshot using the current request's JWT token.
     * The upsert guarantees only one snapshot exists per tenant per day.
     */
    public WealthSnapshotDTO refreshToday(Long tenantId, String bearerToken) {
        log.info("Starting wealth snapshot refresh for tenant {} on {}", tenantId, LocalDate.now());

        List<WealthSnapshotItemInputDTO> items = new ArrayList<>();
        items.addAll(fetchBankingItems(bearerToken));
        items.addAll(fetchInvestmentItems(bearerToken));

        if (items.isEmpty()) {
            throw new RuntimeException("No data retrieved from Banking or Investments services. Snapshot not generated.");
        }

        WealthSnapshotCreateRequestDTO request = WealthSnapshotCreateRequestDTO.builder()
                .snapshotDate(LocalDate.now())
                .currency("EUR")
                .notes("Auto-generated from Banking + Investments ingestion")
                .items(items)
                .build();

        WealthSnapshotDTO result = wealthService.upsertSnapshot(tenantId, request);
        log.info("Snapshot refreshed for tenant {}: totalValue={}", tenantId, result.getTotalValue());
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<WealthSnapshotItemInputDTO> fetchBankingItems(String bearerToken) {
        try {
            List<BankingAccountDTO> accounts = bankingApiClient.getAccounts(bearerToken);
            List<WealthSnapshotItemInputDTO> items = new ArrayList<>();
            for (BankingAccountDTO account : accounts) {
                if (Boolean.FALSE.equals(account.getIsActive())) {
                    continue;
                }
                BigDecimal value = account.getBalance() != null
                        ? BigDecimal.valueOf(account.getBalance())
                        : BigDecimal.ZERO;
                String label = account.getName() != null ? account.getName() : "Account " + account.getId();
                if (account.getInstitutionName() != null) {
                    label = label + " - " + account.getInstitutionName();
                }
                items.add(WealthSnapshotItemInputDTO.builder()
                        .type("CASH")
                        .subtype(account.getAccountTypeName())
                        .source("banking")
                        .sourceRef(account.getId() != null ? account.getId().toString() : null)
                        .label(label)
                        .quantity(BigDecimal.ONE)
                        .unitPrice(value)
                        .value(value)
                        .currency(account.getCurrency() != null ? account.getCurrency() : "EUR")
                        .build());
            }
            log.debug("Fetched {} banking accounts as CASH items", items.size());
            return items;
        } catch (Exception e) {
            log.error("Failed to fetch banking accounts: {}", e.getMessage());
            return List.of();
        }
    }

    private List<WealthSnapshotItemInputDTO> fetchInvestmentItems(String bearerToken) {
        try {
            List<InvestmentPositionDTO> positions = investmentsApiClient.getInvestments(bearerToken);
            List<WealthSnapshotItemInputDTO> items = new ArrayList<>();
            for (InvestmentPositionDTO pos : positions) {
                BigDecimal value = resolveCurrentValue(pos);
                if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
                    value = BigDecimal.ZERO;
                }
                String label = pos.getName() != null ? pos.getName() : "Investment " + pos.getId();
                if (pos.getInstrumentSymbol() != null) {
                    label = label + " (" + pos.getInstrumentSymbol() + ")";
                }
                String assetType = pos.getTypeCode() != null ? pos.getTypeCode().toUpperCase() : "OTHER";
                items.add(WealthSnapshotItemInputDTO.builder()
                        .type(assetType)
                        .subtype(pos.getTypeName())
                        .source("investments")
                        .sourceRef(pos.getId() != null ? pos.getId().toString() : null)
                        .label(label)
                        .quantity(pos.getQuantity())
                        .value(value)
                        .currency(pos.getCurrency() != null ? pos.getCurrency() : "EUR")
                        .build());
            }
            log.debug("Fetched {} investment positions", items.size());
            return items;
        } catch (Exception e) {
            log.error("Failed to fetch investments: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Picks the best available current value for an investment:
     * calculated price > manual valuation > invested amount (cost basis).
     */
    private BigDecimal resolveCurrentValue(InvestmentPositionDTO pos) {
        if (pos.getCurrentValueCalculated() != null && pos.getCurrentValueCalculated().compareTo(BigDecimal.ZERO) > 0) {
            return pos.getCurrentValueCalculated();
        }
        if (pos.getCurrentValueManual() != null && pos.getCurrentValueManual().compareTo(BigDecimal.ZERO) > 0) {
            return pos.getCurrentValueManual();
        }
        return pos.getInvestedAmount();
    }

}
