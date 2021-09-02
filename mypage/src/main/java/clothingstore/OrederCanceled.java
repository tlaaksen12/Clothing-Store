package clothingstore;

public class OrederCanceled extends AbstractEvent {

    private Long id;
    private String status;
    private String clothingid;
    private Integer cnt;
    private Integer price;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public String getClothing() {
        return clothingid;
    }

    public void setClothing(String clothingid) {
        this.clothingid = clothingid;
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