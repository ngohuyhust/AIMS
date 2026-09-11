package vn.aims.product.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cd_tracks")
public class CdTrack {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "track_id") Integer id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id") Cd cd;
    @Column(nullable = false, length = 255) String title;
    @Column(name = "length_seconds", nullable = false) int lengthSeconds;
    protected CdTrack() { }
    public Integer getId() { return id; }
    public String getTitle() { return title; }
    public int getLengthSeconds() { return lengthSeconds; }
}
