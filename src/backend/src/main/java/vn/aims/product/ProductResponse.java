package vn.aims.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public record ProductResponse(@JsonProperty("productID") int productID, String productType,
        String title, String category, String description, String barcode, Double length, Double width,
        Double height, double weight, String originalPrice, String currentPrice, int quantityInStock,
        String status, String imageUrl, String createdAt, String updatedAt) {
    private static final DateTimeFormatter JS_DATE = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.productID,p.productType,p.title,p.category,p.description,p.barcode,
                p.length,p.width,p.height,p.weight,p.originalPrice.toPlainString(),p.currentPrice.toPlainString(),
                p.quantityInStock,p.status,p.imageUrl,JS_DATE.format(p.createdAt),JS_DATE.format(p.updatedAt));
    }
}
