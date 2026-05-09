package es.triana.company.wealth.service.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import es.triana.company.wealth.model.api.WealthSnapshotDTO;
import es.triana.company.wealth.model.api.WealthSnapshotItemDTO;
import es.triana.company.wealth.model.db.WealthAssetType;
import es.triana.company.wealth.model.db.WealthSnapshot;
import es.triana.company.wealth.model.db.WealthSnapshotItem;

@Component
public class WealthSnapshotMapper {

    public WealthSnapshotDTO toDto(WealthSnapshot snapshot, boolean includeItems) {
        return WealthSnapshotDTO.builder()
                .id(snapshot.getId())
                .snapshotDate(snapshot.getSnapshotDate())
                .snapshotAt(snapshot.getSnapshotAt())
                .currency(snapshot.getCurrency())
                .totalValue(nvl(snapshot.getTotalValue()))
                .cashValue(nvl(snapshot.getCashValue()))
                .fundsValue(nvl(snapshot.getFundsValue()))
                .etfsValue(nvl(snapshot.getEtfsValue()))
                .cryptoValue(nvl(snapshot.getCryptoValue()))
                .stocksValue(nvl(snapshot.getStocksValue()))
                .bondsValue(nvl(snapshot.getBondsValue()))
                .realEstateValue(nvl(snapshot.getRealEstateValue()))
                .otherValue(nvl(snapshot.getOtherValue()))
                .notes(snapshot.getNotes())
                .items(includeItems ? toItemDtos(snapshot.getItems()) : null)
                .build();
    }

    public List<WealthSnapshotItemDTO> toItemDtos(List<WealthSnapshotItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .map(this::toItemDto)
                .toList();
    }

    private WealthSnapshotItemDTO toItemDto(WealthSnapshotItem item) {
        return WealthSnapshotItemDTO.builder()
                .id(item.getId())
                .type(item.getAssetType() != null ? item.getAssetType().name() : WealthAssetType.OTHER.name())
                .subtype(item.getAssetSubtype())
                .source(item.getSourceSystem())
                .sourceRef(item.getSourceRef())
                .label(item.getLabel())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .value(nvl(item.getValueAmount()))
                .currency(item.getCurrency())
                .build();
    }

    public BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}