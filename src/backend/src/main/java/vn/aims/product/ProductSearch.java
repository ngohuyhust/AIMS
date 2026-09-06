package vn.aims.product;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ProductSearch(String keyword, String category, List<String> mediaTypes,
                            BigDecimal minPrice, BigDecimal maxPrice, String status) {
    private static final Set<String> TYPES = Set.of("BOOK", "CD", "DVD", "NEWSPAPER");

    public static ProductSearch parse(String keyword, String category, String mediaTypes,
                                      String minPrice, String maxPrice, String status) {
        List<String> types = mediaTypes == null ? List.of() : Arrays.stream(mediaTypes.split(","))
                .map(ProductSearch::trim).map(s -> s.toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).distinct().toList();
        types.stream().filter(t -> !TYPES.contains(t)).findFirst().ifPresent(t -> {
            throw new CatalogException(400, "Invalid media type: " + t);
        });
        return new ProductSearch(keyword, category, types, number(minPrice, "minPrice"),
                number(maxPrice, "maxPrice"), status);
    }

    static String trim(String value) {
        return value.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "");
    }

    // Nest uses Number(string), not parseFloat: whitespace=0 and radix literals are accepted.
    private static BigDecimal number(String value, String field) {
        if (value == null || value.isEmpty()) return null;
        String text = trim(value);
        try {
            double parsed;
            if (text.isEmpty()) parsed = 0;
            else if (text.matches("0[xX][0-9a-fA-F]+")) parsed = new BigInteger(text.substring(2),16).doubleValue();
            else if (text.matches("0[bB][01]+")) parsed = new BigInteger(text.substring(2),2).doubleValue();
            else if (text.matches("0[oO][0-7]+")) parsed = new BigInteger(text.substring(2),8).doubleValue();
            else if (text.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) parsed = Double.parseDouble(text);
            else throw new NumberFormatException();
            if (!Double.isFinite(parsed)) throw new NumberFormatException();
            return BigDecimal.valueOf(parsed);
        } catch (NumberFormatException ex) {
            throw new CatalogException(400, field + " must be a number");
        }
    }
}
