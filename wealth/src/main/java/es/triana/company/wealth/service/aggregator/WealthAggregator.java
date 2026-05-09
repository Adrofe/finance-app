package es.triana.company.wealth.service.aggregator;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import es.triana.company.wealth.model.db.WealthSnapshot;
import es.triana.company.wealth.model.db.WealthSnapshotItem;

/**
 * Single Responsibility: Calculate wealth aggregates by asset type.
 */
@Component
public class WealthAggregator {

    public void aggregateAndSetTotals(WealthSnapshot snapshot, List<WealthSnapshotItem> items) {
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal funds = BigDecimal.ZERO;
        BigDecimal etfs = BigDecimal.ZERO;
        BigDecimal crypto = BigDecimal.ZERO;
        BigDecimal stocks = BigDecimal.ZERO;
        BigDecimal bonds = BigDecimal.ZERO;
        BigDecimal realEstate = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;

        for (WealthSnapshotItem item : items) {
            BigDecimal value = nvl(item.getValueAmount());
            
            switch (item.getAssetType()) {
                case CASH -> cash = cash.add(value);
                case FUND -> funds = funds.add(value);
                case ETF -> etfs = etfs.add(value);
                case CRYPTO -> crypto = crypto.add(value);
                case STOCK -> stocks = stocks.add(value);
                case BOND -> bonds = bonds.add(value);
                case REAL_ESTATE -> realEstate = realEstate.add(value);
                case OTHER -> other = other.add(value);
            }
        }

        snapshot.setCashValue(cash);
        snapshot.setFundsValue(funds);
        snapshot.setEtfsValue(etfs);
        snapshot.setCryptoValue(crypto);
        snapshot.setStocksValue(stocks);
        snapshot.setBondsValue(bonds);
        snapshot.setRealEstateValue(realEstate);
        snapshot.setOtherValue(other);
        snapshot.setTotalValue(cash.add(funds).add(etfs).add(crypto).add(stocks).add(bonds).add(realEstate).add(other));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
