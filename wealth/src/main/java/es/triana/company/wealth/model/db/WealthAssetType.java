package es.triana.company.wealth.model.db;

import java.util.Locale;

public enum WealthAssetType {
    CASH,
    FUND,
    ETF,
    CRYPTO,
    STOCK,
    BOND,
    REAL_ESTATE,
    OTHER;

    public static WealthAssetType fromString(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "FUNDS" -> FUND;
            case "ETFS" -> ETF;
            case "CRYPTOS" -> CRYPTO;
            case "STOCKS", "SHARE", "SHARES" -> STOCK;
            case "BONDS" -> BOND;
            case "REALESTATE", "REALSTATE", "PROPERTY", "PROPERTIES" -> REAL_ESTATE;
            default -> {
                try {
                    yield WealthAssetType.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    yield OTHER;
                }
            }
        };
    }
}
