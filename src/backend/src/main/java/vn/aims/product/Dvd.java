package vn.aims.product;

import jakarta.persistence.*;

@Entity
@Table(name = "dvds")
public class Dvd {
    @Id @Column(name = "product_id") Integer productID;
    @MapsId @OneToOne @JoinColumn(name = "product_id") Media media;
    @Column(name = "disc_type", nullable = false, length = 50) String discType;
    @Column(nullable = false, length = 255) String director;
    @Column(name = "runtime_minutes", nullable = false) int runtimeMinutes;
    @Column(nullable = false, columnDefinition = "text") String subtitles;
    protected Dvd() { }
}
