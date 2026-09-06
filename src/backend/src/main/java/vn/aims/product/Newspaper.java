package vn.aims.product;

import jakarta.persistence.*;

@Entity
@Table(name = "newspapers")
public class Newspaper {
    @Id @Column(name = "product_id") Integer productID;
    @MapsId @OneToOne @JoinColumn(name = "product_id") Media media;
    @Column(name = "editor_in_chief", nullable = false, length = 255) String editorInChief;
    @Column(name = "issue_number", length = 50) String issueNumber;
    @Column(length = 50) String frequency;
    @Column(length = 50) String issn;
    @Column(columnDefinition = "text") String sections;
    protected Newspaper() { }
}
