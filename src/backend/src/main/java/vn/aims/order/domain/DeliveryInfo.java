package vn.aims.order.domain;

import jakarta.persistence.*;

@Entity @Table(name="delivery_info")
public class DeliveryInfo {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="delivery_id") private Integer deliveryID;
    @Column(name="receiver_name",nullable=false,length=255) private String receiverName;
    @Column(nullable=false,length=255) private String email;
    @Column(name="phone_number",nullable=false,length=20) private String phoneNumber;
    @Column(nullable=false,length=255) private String address;
    @Column(nullable=false,length=100) private String province;
    @Column(name="delivery_notes",columnDefinition="text") private String deliveryNotes;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",unique=true) private Order order;
    public DeliveryInfo() {}
    public Integer getDeliveryID() {return deliveryID;}
    public String getReceiverName() {return receiverName;}
    public void setReceiverName(String receiverName) {this.receiverName=receiverName;}
    public String getEmail() {return email;}
    public void setEmail(String email) {this.email=email;}
    public String getPhoneNumber() {return phoneNumber;}
    public void setPhoneNumber(String phoneNumber) {this.phoneNumber=phoneNumber;}
    public String getAddress() {return address;}
    public void setAddress(String address) {this.address=address;}
    public String getProvince() {return province;}
    public void setProvince(String province) {this.province=province;}
    public String getDeliveryNotes() {return deliveryNotes;}
    public void setDeliveryNotes(String deliveryNotes) {this.deliveryNotes=deliveryNotes;}
    public void setOrder(Order order) {this.order=order;}
}
