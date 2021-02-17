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
