package vn.aims.product;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** PostgreSQL-specific search; all user values are bound parameters. */
@Repository
public class ProductRepository {
    private final EntityManager entities;
    public ProductRepository(EntityManager entities) { this.entities = entities; }

    public Optional<Product> findVisible(int id) {
        return entities.createQuery("select p from Product p where p.productID=:id and p.status<>'DELETED'", Product.class)
                .setParameter("id", id).getResultStream().findFirst();
    }

    public <T> T detail(Class<T> type, int id) { return entities.find(type, id); }

    @SuppressWarnings("unchecked")
    public List<Product> random() {
        return entities.createNativeQuery("SELECT * FROM products WHERE status='ACTIVE' ORDER BY RANDOM() LIMIT 20", Product.class)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Product> search(ProductSearch filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT p.* FROM products p
                LEFT JOIN books b ON b.product_id=p.product_id
                LEFT JOIN cds c ON c.product_id=p.product_id
                LEFT JOIN cd_tracks t ON t.product_id=p.product_id
                LEFT JOIN dvds d ON d.product_id=p.product_id
                LEFT JOIN newspapers n ON n.product_id=p.product_id
                LEFT JOIN media m ON m.product_id=p.product_id
                WHERE
                """);
        Map<String,Object> params = new LinkedHashMap<>();
        String status = filter.status() == null || filter.status().isEmpty() ? "ACTIVE" : filter.status().toUpperCase(Locale.ROOT);
        sql.append("ALL".equals(status) ? " p.status <> :status" : " p.status = :status");
        params.put("status", "ALL".equals(status) ? "DELETED" : status);
        if (filter.keyword() != null && !ProductSearch.trim(filter.keyword()).isEmpty()) {
            sql.append("""
                     AND (p.title ILIKE :keyword OR p.category ILIKE :keyword OR p.description ILIKE :keyword
                     OR p.barcode ILIKE :keyword OR b.authors ILIKE :keyword OR c.artists ILIKE :keyword
                     OR t.title ILIKE :keyword OR d.director ILIKE :keyword OR n.editor_in_chief ILIKE :keyword
                     OR n.sections ILIKE :keyword OR m.publisher ILIKE :keyword OR m.genre ILIKE :keyword
                     OR m.language ILIKE :keyword)
                    """);
            params.put("keyword", "%" + ProductSearch.trim(filter.keyword()) + "%");
        }
        if (filter.category() != null && !filter.category().isEmpty()) {
            String category = ProductSearch.trim(filter.category());
            String upper = category.toUpperCase(Locale.ROOT);
            String type = switch (upper) {
                case "SÁCH", "SACH", "BOOK" -> "BOOK";
                case "BÁO", "BÁO CHÍ", "BAO", "BAO CHI", "NEWSPAPER" -> "NEWSPAPER";
                case "CD", "DVD" -> upper;
                default -> null;
            };
            List<String> aliases = new ArrayList<>(List.of(category, upper));
            if (type != null) aliases.addAll(switch (type) {
                case "BOOK" -> List.of("SÁCH", "SACH", "BOOK", "Book", "book");
                case "NEWSPAPER" -> List.of("BÁO", "BÁO CHÍ", "BAO", "BAO CHI", "NEWSPAPER", "Newspaper", "newspaper");
                case "CD" -> List.of("CD", "cd");
                default -> List.of("DVD", "dvd");
            });
            sql.append(" AND (p.category IN (:categories)");
            params.put("categories", aliases);
            if (type != null) { sql.append(" OR p.product_type=:categoryType"); params.put("categoryType", type); }
            sql.append(")");
        }
        if (!filter.mediaTypes().isEmpty()) {
            sql.append(" AND p.product_type IN (:types)"); params.put("types", filter.mediaTypes());
        }
        if (filter.minPrice() != null) { sql.append(" AND p.current_price>=:minPrice"); params.put("minPrice", filter.minPrice()); }
        if (filter.maxPrice() != null) { sql.append(" AND p.current_price<=:maxPrice"); params.put("maxPrice", filter.maxPrice()); }
        sql.append(" ORDER BY p.title ASC");
        var query = entities.createNativeQuery(sql.toString(), Product.class);
        params.forEach(query::setParameter);
        return query.getResultList();
    }
}
