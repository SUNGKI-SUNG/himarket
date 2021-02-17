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

# 헥사고날 아키텍처 다이어그램 도출
![헥사고날](https://user-images.githubusercontent.com/77369319/108262114-d57abd00-71a7-11eb-8fec-467b0c8d1821.png)

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

<주문(order)>

![order](https://user-images.githubusercontent.com/77369319/108148522-4ff9fd00-7114-11eb-9f94-3c726de9532d.png)

<주문 후 delivery 전송 결과>

![statusupdate](https://user-images.githubusercontent.com/77369319/108150019-4aea7d00-7117-11eb-9374-416ac6b794ba.png)

<Kafka Event 발생>

![kafka](https://user-images.githubusercontent.com/77369319/108151148-65255a80-7119-11eb-8381-576b50b6a45d.png)

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
<GateWay를 통한 주문>

![gatewayorder](https://user-images.githubusercontent.com/77369319/108150643-58543700-7118-11eb-806d-754def74ab95.png)

# CQRS
Materialized View 를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이) 도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다.
- 주문 실행후 customercenter mypages를 통한 주문내역 확인
```
root@labs-119641427:/home/project# http http://localhost:8083/mypages
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Wed, 17 Feb 2021 03:24:55 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "mypages": [
            {
                "_links": {
                    "mypage": {
                        "href": "http://localhost:8083/mypages/1"
                    }, 
                    "self": {
                        "href": "http://localhost:8083/mypages/1"
                    }
                }, 
                "deliveryId": 1, 
                "orderId": 1, 
                "productId": "10", 
                "qty": 1, 
                "status": "deliveryStarted"
            }, 
            {
                "_links": {
                    "mypage": {
                        "href": "http://localhost:8083/mypages/2"
                    }, 
                    "self": {
                        "href": "http://localhost:8083/mypages/2"
                    }
                }, 
                "deliveryId": 2, 
                "orderId": 2, 
                "productId": "20", 
                "qty": 2, 
                "status": "deliveryStarted"
            }
        ]
    }, 
    "_links": {
        "profile": {
            "href": "http://localhost:8083/profile/mypages"
        }, 
        "search": {
            "href": "http://localhost:8083/mypages/search"
        }, 
        "self": {
            "href": "http://localhost:8083/mypages"
        }
    }
}
```

# 폴리그랏
- delivery 서비스의 DB와 Order 서비스의 DB를 다른 DB를 사용하여 폴리글랏을 만족시키고 있다.
```
-delivery pom.xml DB 설정코드
<dependency>
<groupId>org.hsqldb</groupId>
			<artifactId>hsqldb</artifactId>
			<scope>runtime</scope>
</dependency>   

-order pom.xml DB 설정코드 
<dependency>
	<groupId>com.h2database</groupId>
	<artifactId>h2</artifactId>
	<scope>runtime</scope>
</dependency>
```

# 동기식 호출과 Fallback 처리
분석단계에서의 조건 중 하나로 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 
호출 프로토콜은 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다.
```
package external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="delivery", url="${api.url.Payment}")
public interface CancellationService{

    @RequestMapping(method= RequestMethod.POST, path="/cancellations")
    public void cancel(@RequestBody Cancellation cancellation);

}
```

# 운영

# Deploy / Pipeline

- git에서 소스 가져오기
```
git clone https://github.com/SUNGKI-SUNG/himarket.git
```
- Build 하기
```
cd /himarket
cd gateway
mvn compile
mvn package

cd ..
cd order
mvn compile
mvn package

cd ..
cd delivery
mvn compile
mvn package

cd ..
cd customercenter
mvn compile
mvn package
```

- Docker Image Push/deploy/서비스생성
```
cd gateway
az acr build --registry skuser04 --image skuser04.azurecr.io/gateway:v1 .
kubectl create ns tutorial

kubectl create deploy gateway --image=skuser04.azurecr.io/gateway:v1 -n tutorial
kubectl expose deploy gateway --type=ClusterIP --port=8080 -n tutorial

cd ..
cd delivery
az acr build --registry skuser04 --image skuser04.azurecr.io/delivery:v1 .

kubectl create deploy delivery --image=skuser04.azurecr.io/delivery:v1 -n tutorial
kubectl expose deploy delivery --type=ClusterIP --port=8080 -n tutorial

cd ..
cd customercenter
az acr build --registry skuser04 --image skuser04.azurecr.io/customercenter:v1 .

kubectl create deploy customercenter --image=skuser04.azurecr.io/customercenter:v1 -n tutorial
kubectl expose deploy customercenter --type=ClusterIP --port=8080 -n tutorial
```

- yml파일 이용한 deploy
```
cd ..
cd order
az acr build --registry skuser04 --image skuser04.azurecr.io/order:v1 .
```
![증빙7](https://user-images.githubusercontent.com/77368578/107920373-35a70e80-6fb0-11eb-8024-a6fc42fea93f.png)

```
kubectl expose deploy order --type=ClusterIP --port=8080 -n tutorial
```

- himarket/order/kubernetes/deployment.yml 파일 
```yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order
  labels:
    app: order
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order
  template:
    metadata:
      labels:
        app: order
    spec:
      containers:
        - name: order
          image: skuser04.azurecr.io/order:v1
          ports:
            - containerPort: 8080
          env:
            - name: configurl
              valueFrom:
                confiMapKeyRef:
                  name: apiurl
                  key: url
```	  
- deploy 완료

![전체 MSA](https://user-images.githubusercontent.com/77368578/108006011-992b4d80-703d-11eb-8df9-a2cea19aa693.png)
