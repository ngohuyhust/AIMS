package vn.aims.order;

import jakarta.persistence.*;

@Entity @Table(name="delivery_info")
public class DeliveryInfo {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="delivery_id") Integer deliveryID;
    @Column(name="receiver_name",nullable=false,length=255) String receiverName;
    @Column(nullable=false,length=255) String email;
    @Column(name="phone_number",nullable=false,length=20) String phoneNumber;
    @Column(nullable=false,length=255) String address;
    @Column(nullable=false,length=100) String province;
    @Column(name="delivery_notes",columnDefinition="text") String deliveryNotes;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",unique=true) Order order;
    protected DeliveryInfo() {}
}
