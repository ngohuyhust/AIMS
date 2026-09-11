package vn.aims.product.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "media")
public class Media {
    @Id @Column(name = "product_id") Integer productID;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") Product product;
    @Column(length = 255) String publisher;
    @Column(name = "release_date") LocalDate releaseDate;
    @Column(length = 50) String language;
    @Column(length = 100) String genre;
    protected Media() { }
    public Integer getProductID() { return productID; }
    public String getPublisher() { return publisher; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public String getLanguage() { return language; }
    public String getGenre() { return genre; }
}
