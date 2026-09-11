package vn.aims.product.entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "cds")
public class Cd {
    @Id @Column(name = "product_id") Integer productID;
    @MapsId @OneToOne @JoinColumn(name = "product_id") Media media;
    @Column(nullable = false, columnDefinition = "text") String artists;
    @OneToMany(mappedBy = "cd") List<CdTrack> tracks;
    protected Cd() { }
    public Media getMedia() { return media; }
    public String getArtists() { return artists; }
    public List<CdTrack> getTracks() { return tracks; }
}
