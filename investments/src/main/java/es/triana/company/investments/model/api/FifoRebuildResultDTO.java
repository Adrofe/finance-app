package es.triana.company.investments.model.api;

public record FifoRebuildResultDTO(
        Long instrumentId,
        Long tenantId,
        int operationsProcessed,
        int sellsRebuilt,
        int lotsCreated) {
}
