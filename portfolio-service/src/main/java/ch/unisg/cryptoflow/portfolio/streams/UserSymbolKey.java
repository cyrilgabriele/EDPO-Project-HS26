package ch.unisg.cryptoflow.portfolio.streams;

import java.util.Locale;

/**
 * Composite key for the holdings and position-value KTables in the scope-04
 * valuation topology. Encoded as {@code "userId|SYMBOL"} so a plain String
 * Serde is enough (no custom Avro/JSON serde to maintain).
 */
public record UserSymbolKey(String userId, String symbol) {

    private static final String DELIMITER = "|";

    public static String encode(String userId, String symbol) {
        if (userId == null || symbol == null) {
            throw new IllegalArgumentException("userId and symbol must be non-null");
        }
        if (userId.contains(DELIMITER)) {
            throw new IllegalArgumentException("userId must not contain '" + DELIMITER + "'");
        }
        return userId + DELIMITER + symbol.toUpperCase(Locale.ROOT);
    }

    public static UserSymbolKey decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded key must be non-null");
        }
        int idx = encoded.indexOf(DELIMITER);
        if (idx < 0) {
            throw new IllegalArgumentException("missing '" + DELIMITER + "' in key: " + encoded);
        }
        return new UserSymbolKey(encoded.substring(0, idx), encoded.substring(idx + 1));
    }
}
