package clothingstore;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

import clothingstore.external.Payment;
import clothingstore.external.PaymentService;

import java.util.List;
import java.util.Date;

@Entity
@Table(name="Order_table")
public class Order {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String clothingid;
    private String address;
    private String cardno;
    private String status;
    private Integer cnt;
    private Integer price;

    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        Payment payment = new Payment();
        //payment.setId(this.id);
        //payment.setCardno(this.cardno);
        BeanUtils.copyProperties(ordered, payment);
        payment.setOrderId(this.id.toString());
        payment.setStatus("Ordered OK");
        OrderApplication.applicationContext.getBean(clothingstore.external.PaymentService.class).payment(payment);        

    }
    @PostRemove
    public void onPostRemove(){
        OrederCanceled orederCanceled = new OrederCanceled();
        BeanUtils.copyProperties(this, orederCanceled);
        orederCanceled.publishAfterCommit();
        
    }
    @PrePersist
    public void onPrePersist(){
    }
    @PreRemove
    public void onPreRemove(){
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getClothingid() {
        return clothingid;
    }

    public void setClothingid(String clothingid) {
        this.clothingid = clothingid;
    }
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
    public String getCardno() {
        return cardno;
    }

    public void setCardno(String cardno) {
        this.cardno = cardno;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public Integer getCnt() {
        return cnt;
    }

    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }
    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }




}