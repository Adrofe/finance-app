package es.triana.company.wealth.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import es.triana.company.wealth.model.api.WealthSnapshotCreateRequestDTO;
import es.triana.company.wealth.model.api.WealthSnapshotDTO;
import es.triana.company.wealth.model.api.WealthSnapshotItemInputDTO;
import es.triana.company.wealth.model.db.WealthSnapshot;
import es.triana.company.wealth.model.db.WealthSnapshotItem;
import es.triana.company.wealth.repository.WealthSnapshotRepository;
import es.triana.company.wealth.service.aggregator.WealthAggregator;
import es.triana.company.wealth.service.factory.WealthSnapshotItemFactory;
import es.triana.company.wealth.service.mapper.WealthSnapshotMapper;
import es.triana.company.wealth.service.normalizer.WealthCurrencyNormalizer;
import es.triana.company.wealth.service.validator.WealthValidator;

@Service
public class WealthService {

    private final WealthSnapshotRepository wealthSnapshotRepository;
    private final WealthSnapshotMapper wealthSnapshotMapper;
    private final WealthValidator wealthValidator;
    private final WealthCurrencyNormalizer currencyNormalizer;
    private final WealthSnapshotItemFactory itemFactory;
    private final WealthAggregator wealthAggregator;

    public WealthService(WealthSnapshotRepository wealthSnapshotRepository, WealthSnapshotMapper wealthSnapshotMapper, WealthValidator wealthValidator, WealthCurrencyNormalizer currencyNormalizer, WealthSnapshotItemFactory itemFactory, WealthAggregator wealthAggregator) {
        this.wealthSnapshotRepository = wealthSnapshotRepository;
        this.wealthSnapshotMapper = wealthSnapshotMapper;
        this.wealthValidator = wealthValidator;
        this.currencyNormalizer = currencyNormalizer;
        this.itemFactory = itemFactory;
        this.wealthAggregator = wealthAggregator;
    }

    @Transactional
    public WealthSnapshotDTO upsertSnapshot(Long tenantId, WealthSnapshotCreateRequestDTO request) {
        wealthValidator.validateTenantId(tenantId);
        wealthValidator.validateSnapshotRequest(request);

        LocalDate snapshotDate = request.getSnapshotDate() != null ? request.getSnapshotDate() : LocalDate.now();
        String currency = currencyNormalizer.normalize(request.getCurrency());
        LocalDateTime now = LocalDateTime.now();

        WealthSnapshot snapshot = findOrCreateSnapshot(tenantId, snapshotDate, now);
        snapshot.setSnapshotAt(now);
        snapshot.setUpdatedAt(now);
        snapshot.setCurrency(currency);
        snapshot.setNotes(request.getNotes());
        snapshot.getItems().clear();

        List<WealthSnapshotItem> items = buildItems(request.getItems(), snapshot, currency, now);
        snapshot.setItems(items);
        
        wealthAggregator.aggregateAndSetTotals(snapshot, items);

        WealthSnapshot saved = wealthSnapshotRepository.save(snapshot);
        return wealthSnapshotMapper.toDto(saved, true);
    }

    private WealthSnapshot findOrCreateSnapshot(Long tenantId, LocalDate snapshotDate, LocalDateTime now) {
        return wealthSnapshotRepository
                .findWithItemsByTenantIdAndSnapshotDate(tenantId, snapshotDate)
                .orElseGet(() -> WealthSnapshot.builder()
                        .tenantId(tenantId)
                        .snapshotDate(snapshotDate)
                        .createdAt(now)
                        .build());
    }

    private List<WealthSnapshotItem> buildItems(List<WealthSnapshotItemInputDTO> inputs,
                                                WealthSnapshot snapshot,
                                                String defaultCurrency,
                                                LocalDateTime now) {
        List<WealthSnapshotItem> items = new ArrayList<>();
        
        for (WealthSnapshotItemInputDTO input : inputs) {
            WealthSnapshotItem item = itemFactory.createFromInput(input, snapshot, defaultCurrency, now);
            items.add(item);
        }
        
        return items;
    }

    @Transactional(readOnly = true)
    public List<WealthSnapshotDTO> getSnapshots(Long tenantId, LocalDate from, LocalDate to, boolean includeItems) {
        wealthValidator.validateTenantId(tenantId);

        LocalDate start = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate end = to != null ? to : LocalDate.now();

        List<WealthSnapshot> snapshots = includeItems
                ? wealthSnapshotRepository.findWithItemsByTenantIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(tenantId, start, end)
                : wealthSnapshotRepository.findByTenantIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(tenantId, start, end);

        return snapshots.stream().map(s -> wealthSnapshotMapper.toDto(s, includeItems)).toList();
    }

    @Transactional
    public void deleteSnapshot(Long tenantId, Long id) {
        wealthValidator.validateTenantId(tenantId);
        WealthSnapshot snapshot = wealthSnapshotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found"));
        if (!snapshot.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        wealthSnapshotRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public WealthSnapshotDTO getLatest(Long tenantId, boolean includeItems) {
        wealthValidator.validateTenantId(tenantId);

        WealthSnapshot snapshot = (includeItems
                ? wealthSnapshotRepository.findWithItemsTopByTenantIdOrderBySnapshotDateDescSnapshotAtDesc(tenantId)
                : wealthSnapshotRepository.findTopByTenantIdOrderBySnapshotDateDescSnapshotAtDesc(tenantId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No wealth snapshot found for tenant"));

        return wealthSnapshotMapper.toDto(snapshot, includeItems);
    }
}
