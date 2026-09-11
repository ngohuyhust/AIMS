package vn.aims.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import vn.aims.product.entity.Product;

public record ProductResponse(@JsonProperty("productID") int productID, String productType,
        String title, String category, String description, String barcode, Double length, Double width,
        Double height, double weight, String originalPrice, String currentPrice, int quantityInStock,
        String status, String imageUrl, String createdAt, String updatedAt) {
    private static final DateTimeFormatter JS_DATE = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getProductID(),p.getProductType(),p.getTitle(),p.getCategory(),p.getDescription(),p.getBarcode(),
                p.getLength(),p.getWidth(),p.getHeight(),p.getWeight(),p.getOriginalPrice().toPlainString(),p.getCurrentPrice().toPlainString(),
                p.getQuantityInStock(),p.getStatus(),p.getImageUrl(),JS_DATE.format(p.getCreatedAt()),JS_DATE.format(p.getUpdatedAt()));
    }
}
