package himarket;

import himarket.config.kafka.KafkaProcessor;

import java.util.NoSuchElementException;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload final String eventString) {

    }

    @Autowired
    OrderRepository orderRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverShipped_(@Payload Shipped shipped) {

        if (shipped.isMe()) {
            java.util.Optional<Order> orderOptioonal = orderRepository.findById(shipped.getOrderId());
            Order order = orderOptioonal.get();
            order.setStatus(shipped.getStatus());

            orderRepository.save(order);

            // Order mybeOrder = orderRepository.findById(shipped.getOrderId())
            // .orElseThrow(java.util.NoSuchElementException::new);

            // mybeOrder.setStatus(shipped.getStatus());
            // orderRepository.save(mybeOrder);

            //System.out.println("##### listener  : " + shipped.toJson());
        }
    }

}
