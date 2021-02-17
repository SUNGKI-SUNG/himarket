# himarket(전자제품 온라인 마켓)

# 서비스 시나리오
### 기능적 요구사항
1. 고객이 전자제품을 주문하면 주문정보를 바탕으로 배송이 시작된다.
2. 고객이 주문 취소를 하게 되면 주문 정보는 삭제되나 배송팀에서는 취소 장부를 별도 관리한다.
3. 주문과 배송 서비스는 게이트웨이를 통해 통신한다.

### 비기능 요구사항
1. 주문팀의 주문 취소는 반드시 배송팀의 배송취소가 전제되어야 한다.

### 추가 기능 요구사항
1. 고객은 주문서비스에서 배송 상태를 열람 할 수 있어야 한다.
2. 고객센터는 MyPage를 통해 모든 진행내역의 모니터링을 제공해야 한다.

# Event Storming 결과
![EventStorming](https://user-images.githubusercontent.com/77369319/108143073-597e6780-710a-11eb-9cae-6f2c170848ab.png)


# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬(msaez.io 개인랩)에서 실행하는 방법은 아래와 같다 
(각자의 포트넘버는 8081[order], 8082[delivery], 8083[customercenter], 8088[gateway] 이다)
```
cd Order
mvn spring-boot:run  

cd dilivery
mvn spring-boot:run

cd customercenter
mvn spring-boot:run 

cd gateway
mvn spring-boot:run  
```
![portcheck](https://user-images.githubusercontent.com/77369319/108149626-9ea89680-7116-11eb-9c04-2c42949be3d9.png)

## DDD 의 적용
msaez.io 를 통해 구현한 Aggregate 단위로 Entity 를 선언 후, 구현을 진행하였다.
Entity Pattern 과 Repository Pattern 을 적용하기 위해 Spring Data REST 의 RestRepository 를 적용하였다.

**himarket 서비스의 order.java**
```
package himarket;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import external.Cancellation;
import java.util.List;

@Entity
@Table(name="Order_table")
public class Order {
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String productId;
    private Integer qty;
    private String status;

    @PostPersist
    public void onPostPersist(){
        final Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();
    }

    @PreRemove
    public void onPreRemove(){
        final OrderCanceled orderCanceled = new OrderCanceled();
        BeanUtils.copyProperties(this, orderCanceled);
        orderCanceled.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        himarket.external.Cancellation cancellation = new himarket.external.Cancellation();
        // mappings goes here
        cancellation.setOrderId(this.getId());
        cancellation.setStatus("DeliveryCancelled");

        OrderApplication.applicationContext.getBean(himarket.external.CancellationService.class)
            .cancel(cancellation);
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(final String productId) {
        this.productId = productId;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(final Integer qty) {
        this.qty = qty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }
}
```

**himarket 서비스의 PolicyHandler.java**
```
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
```

- DDD 적용 후 REST API의 테스트 통하여 정상적으로 동작하는 것을 확인할 수 있었다. 

주문(order)

![order](https://user-images.githubusercontent.com/77369319/108148522-4ff9fd00-7114-11eb-9f94-3c726de9532d.png)

주문 후 delivery 전송 결과

![statusupdate](https://user-images.githubusercontent.com/77369319/108150019-4aea7d00-7117-11eb-9374-416ac6b794ba.png)

# GateWay 적용
API GateWay를 통하여 마이크로 서비스들의 진입점을 통일할 수 있다. 다음과 같이 GateWay를 적용하였다.

**himarket 서비스의 Gateway - Application.yml**
```
server:
  port: 8088

---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://localhost:8081
          predicates:
            - Path=/orders/** 
        - id: delivery
          uri: http://localhost:8082
          predicates:
            - Path=/deliveries/**,/cancellations/** 
        - id: customercenter
          uri: http://localhost:8083
          predicates:
            - Path= /mypages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/** 
        - id: delivery
          uri: http://delivery:8080
          predicates:
            - Path=/deliveries/**,/cancellations/** 
        - id: customercenter
          uri: http://customercenter:8080
          predicates:
            - Path= /mypages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
```

# CQRS
Materialized View 를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이) 도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다.
