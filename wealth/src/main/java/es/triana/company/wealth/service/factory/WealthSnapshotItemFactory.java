package es.triana.company.wealth.service.factory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import es.triana.company.wealth.model.api.WealthSnapshotItemInputDTO;
import es.triana.company.wealth.model.db.WealthAssetType;
import es.triana.company.wealth.model.db.WealthSnapshot;
import es.triana.company.wealth.model.db.WealthSnapshotItem;
import es.triana.company.wealth.service.normalizer.WealthCurrencyNormalizer;

/**
 * Single Responsibility: Create WealthSnapshotItem entities from input DTOs.
 */
@Component
public class WealthSnapshotItemFactory {

    private final WealthCurrencyNormalizer currencyNormalizer;

    public WealthSnapshotItemFactory(WealthCurrencyNormalizer currencyNormalizer) {
        this.currencyNormalizer = currencyNormalizer;
    }

    public WealthSnapshotItem createFromInput(WealthSnapshotItemInputDTO input,  WealthSnapshot snapshot,  String defaultCurrency, LocalDateTime now) {
        WealthAssetType type = WealthAssetType.fromString(input.getType());
        BigDecimal value = nvl(input.getValue());
        String itemCurrency = currencyNormalizer.normalize(input.getCurrency() != null ? input.getCurrency() : defaultCurrency );

        return WealthSnapshotItem.builder()
                .snapshot(snapshot)
                .assetType(type)
                .assetSubtype(input.getSubtype())
                .sourceSystem(input.getSource())
                .sourceRef(input.getSourceRef())
                .label(input.getLabel())
                .quantity(input.getQuantity())
                .unitPrice(input.getUnitPrice())
                .valueAmount(value)
                .currency(itemCurrency)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
