package clothingstore;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

@Entity
@Table(name="Shipping_table")
public class Shipping {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String clothingid;
    private String status;
    private String address;
    private String cnt;
    private String orderId;

    @PostPersist
    public void onPostPersist(){
        Shipped shipped = new Shipped();
        BeanUtils.copyProperties(this, shipped);
        shipped.publishAfterCommit();

    }
    @PostRemove
    public void onPostRemove(){
        ShippingCanceled shippingCanceled = new ShippingCanceled();
        BeanUtils.copyProperties(this, shippingCanceled);
        shippingCanceled.publishAfterCommit();

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
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
    public String getCnt() {
        return cnt;
    }

    public void setCnt(String cnt) {
        this.cnt = cnt;
    }
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }




}