package vn.aims.product;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id") Integer productID;
    @Column(name = "product_type", nullable = false, length = 20) String productType;
    @Column(nullable = false, length = 255) String title;
    @Column(nullable = false, length = 45) String category;
    @Column(columnDefinition = "text") String description;
    @Column(nullable = false, length = 50, unique = true) String barcode;
    Double length;
    Double width;
    Double height;
    @Column(nullable = false) double weight;
    @Column(name = "original_value", nullable = false, precision = 12, scale = 2) BigDecimal originalPrice;
    @Column(name = "current_price", nullable = false, precision = 12, scale = 2) BigDecimal currentPrice;
    @Column(name = "quantity_in_stock", nullable = false) int quantityInStock;
    @Column(nullable = false, length = 20) String status;
    @Column(name = "image_url", length = 255) String imageUrl;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    protected Product() { }
}
