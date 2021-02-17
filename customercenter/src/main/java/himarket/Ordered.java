package himarket;

import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;

import himarket.config.kafka.KafkaProcessor;

public class Ordered extends AbstractEvent {

    private Long id;
    private String productId;
    private Integer qty;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }
    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

	@StreamListener(KafkaProcessor.INPUT)
	public void whenOrdered_then_CREATE_1 (MypageViewHandler mypageViewHandler) {
	    try {
	        if (isMe()) {
	            // view 객체 생성
	            final Mypage mypage = new Mypage();
	            // view 객체에 이벤트의 Value 를 set 함
	            mypage.setOrderId(getId());
	            mypage.setProductId(getProductId());
	            mypage.setQty(getQty());
	            mypage.setStatus(getStatus());
	            // view 레파지 토리에 save
	            mypageViewHandler.mypageRepository.save(mypage);
	        }
	    } catch (final Exception e) {
	        e.printStackTrace();
	    }
	}
}