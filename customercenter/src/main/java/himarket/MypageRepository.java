package himarket;


import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.messaging.handler.annotation.Payload;

import himarket.config.kafka.KafkaProcessor;

import java.util.List;

public interface MypageRepository extends CrudRepository<Mypage, Long> {

    List<Mypage> findByOrderId(Long orderId);

	@StreamListener(KafkaProcessor.INPUT)
	default void whenOrdered_then_CREATE_1 (final Ordered ordered) {
	    try {
	        if (ordered.isMe()) {
	            // view 객체 생성
	            final Mypage mypage = new Mypage();
	            // view 객체에 이벤트의 Value 를 set 함
	            mypage.setOrderId(ordered.getId());
	            mypage.setProductId(ordered.getProductId());
	            mypage.setQty(ordered.getQty());
	            mypage.setStatus(ordered.getStatus());
	            // view 레파지 토리에 save
	            save(mypage);
	        }
	    } catch (final Exception e) {
	        e.printStackTrace();
	    }
	}

	@StreamListener(KafkaProcessor.INPUT)
	default void whenDeliveryCanceled_then_UPDATE_2(final DeliveryCanceled deliveryCanceled) {
	    try {
	        if (deliveryCanceled.isMe()) {
	            // view 객체 조회
	            final List<Mypage> mypageList = findByOrderId(deliveryCanceled.getOrderId());
	            for (final Mypage mypage : mypageList) {
	                // view 객체에 이벤트의 eventDirectValue 를 set 함
	                mypage.setStatus(deliveryCanceled.getStatus());
	                // view 레파지 토리에 save
	                save(mypage);
	            }
	        }
	    } catch (final Exception e) {
	        e.printStackTrace();
	    }
	}



}