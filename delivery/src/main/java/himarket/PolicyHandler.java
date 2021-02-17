package himarket;

import himarket.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload final String eventString){

    }
    @Autowired
    DeliveryRepository deliveryRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrdered_(@Payload final Ordered ordered){

        if(ordered.isMe()){
            // customer에게 sns 발송
            // 대한통운 전문교환
            // 배송 장부에 추가 
            final Delivery delivery = new Delivery();
            delivery.setOrderId(ordered.getId());
            delivery.setStatus("deliveryStarted");

            deliveryRepository.save(delivery);

            //System.out.println("##### listener  : " + ordered.toJson());
        }
    }

}
