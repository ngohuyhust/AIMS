package vn.aims.product;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {
    @Id @Column(name = "product_id") Integer productID;
    @MapsId @OneToOne @JoinColumn(name = "product_id") Media media;
    @Column(nullable = false, columnDefinition = "text") String authors;
    @Column(name = "cover_type", nullable = false, length = 50) String coverType;
    @Column(name = "num_pages") Integer numPages;
    protected Book() { }
}
