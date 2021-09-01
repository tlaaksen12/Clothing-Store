# hotelone

# 옷주문 서비스


# Table of contents

- [옷 주문](#---)
  - [서비스 시나리오](#시나리오)
  - [분석/설계](#분석-설계)
  - [구현:](#구현)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
    - [API Gateway](#API-게이트웨이-gateway)
    - [CQRS / Meterialized View](#마이페이지)
    - [Saga Pattern / 보상 트랜잭션](#SAGA-CQRS-동작-결과)
  - [운영](#운영)
    - [CI/CD 설정](#cicd-설정)
    - [Self Healing](#Self-Healing)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-배포)
    - [ConfigMap / Secret](#Configmap)

## 서비스 시나리오

옷 주문 시스템에서 요구하는 기능/비기능 요구사항은 다음과 같습니다. 사용자가 옷 주문과 함께 결제를 진행하고 난 후 상점에서 옷을 배송하는 프로세스입니다. 이 과정에 대해서 고객은 진행 상황을 MyPage를 통해 확인할 수 있습니다.

#### 기능적 요구사항

1. 고객이 원하는 옷을 선택 하여 주문한다.
1. 고객이 결제 한다.
1. 옷이 주문되면 판매자는 주문현황을 확인하고 배송을 한다.
1. 고객이 주문 신청을 취소할 수 있다.
1. 주문이 취소 되면 배송이 취소 된다.
1. 고객이 주문 상황을 조회 한다.
1. 고객이 주문을 취소를 하면 주문현황는 삭제 상태로 업데이트 된다.

#### 비기능적 요구사항

1. 트랜잭션
   1. 결제가 되지 않은 예약건은 아예 주문 신청이 되지 않아야 한다. Sync 호출
1. 장애격리
   1. 배송 기능이 수행 되지 않더라도 예약은 365일 24시간 받을 수 있어야 한다. Async (event-driven), Eventual Consistency
   1. 결제 시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도 한다. Circuit breaker, fallback
1. 성능
   1. 고객이 주문 상태를 마이페이지에서 확인할 수 있어야 한다. CQRS
   


# 분석 설계

## Event Storming

### MSAEz 로 모델링한 이벤트스토밍 결과:


![image](https://user-images.githubusercontent.com/87048693/131333817-c46670e3-fd19-4539-92d9-3a6b00d2a7ee.png)


1. order의 주문, ship의 배송, payment의 결제, customer의 mypage 등은 그와 연결된 command 와 event 들에 의하여 트랜잭션이 유지되어야 하는 단위로 그들 끼리 묶어줌(바운디드 컨텍스트)
1. 도메인 서열 분리 
   - Core Domain:  order, ship
   - Supporting Domain: customer
   - General Domain : payment


### 기능 요구사항을 커버하는지 검증
1. 고객이 원하는 옷을 선택 하여 주문한다.(OK)
1. 고객이 결제 한다.(OK)
1. 옷이 주문되면 판매자는 주문현황을 확인하고 배송을 한다.(OK)
1. 고객이 주문 신청을 취소할 수 있다.(OK)
1. 주문이 취소 되면 배송이 취소 된다.(OK)
1. 고객이 주문 상황을 조회 한다.(OK)
1. 고객이 주문을 취소를 하면 주문현황는 삭제 상태로 업데이트 된다.(OK)

### 비기능 요구사항을 커버하는지 검증
1. 트랜잭션 
   - 결제가 되지 않은 주문건은 아예 배달이 되지 않아야 한다. Sync 호출 (OK)
   - 주문 완료 시 결제 처리에 대해서는 Request-Response 방식 처리
1. 장애격리
   - 배송관리 기능이 수행 되지 않더라도 주문은 365일 24시간 받을 수 있어야 한다.(OK)
   - Eventual Consistency 방식으로 트랜잭션 처리함. (PUB/Sub)


## 헥사고날 아키텍처 다이어그램 도출

![image](https://user-images.githubusercontent.com/87048693/131659211-a67965be-43bf-42f4-893d-ad8464f45bcf.png)

    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
    - 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐



# 구현
분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 8084 이다)

```
cd /home/project/team/hotelone/order
mvn spring-boot:run

cd /home/project/team/hotelone/reservation
mvn spring-boot:run 

cd /home/project/team/hotelone/payment
mvn spring-boot:run  

cd /home/project/team/hotelone/customer
mvn spring-boot:run 
```

## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 paymentHistory 마이크로 서비스). 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하려고 노력했다.

```
package hotelone;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="PaymentHistory_table")
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private Long cardNo;
    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public Long getCardNo() {
        return cardNo;
    }

    public void setCardNo(Long cardNo) {
        this.cardNo = cardNo;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }




}
```

- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package hotelone;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface PaymentHistoryRepository extends PagingAndSortingRepository<PaymentHistory, Long>{
	PaymentHistory findByOrderId(Long orderId);
}
```

- 적용 후 REST API 의 테스트
```
# order 서비스의 주문처리
http localhost:8081/orders name=Lee roomType=suite

# reservation 서비스의 예약처리
http localhost:8082/reservations orderId=1 status="confirmed"

```
![image](https://user-images.githubusercontent.com/87048623/129999165-c8f5fb73-59d7-4898-a2f5-cb47fbe2fbeb.png)
![image](https://user-images.githubusercontent.com/87048623/129999197-63d85159-5847-48af-a02c-9aac3b9e2864.png)


## CQRS

- 고객의 예약정보를 한 눈에 볼 수 있게 mypage를 구현 한다.

```
# 주문 상태 확인
http localhost:8084/mypages/1
```
![image](https://user-images.githubusercontent.com/87048623/129999240-6dafc1c9-0bca-4a43-8cb3-a98071a319fb.png)


## 폴리글랏 퍼시스턴스

폴리그랏 퍼시스턴스 요건을 만족하기 위해 customer 서비스의 DB를 기존 h2를 hsqldb로 변경

![image](https://user-images.githubusercontent.com/87048623/129825717-ba8ae72a-5fab-4f48-a55d-6b8c71e1b939.png)


```
<!--		<dependency>-->
<!--			<groupId>com.h2database</groupId>-->
<!--			<artifactId>h2</artifactId>-->
<!--			<scope>runtime</scope>-->
<!--		</dependency>-->

		<dependency>
			<groupId>org.hsqldb</groupId>
			<artifactId>hsqldb</artifactId>
			<scope>runtime</scope>
		</dependency>



# 변경/재기동 후 주문 처리
http localhost:8081/orders name=Park roomType=twin

# 저장이 잘 되었는지 조회
http localhost:8084/mypages/1

```

![image](https://user-images.githubusercontent.com/87048623/129999342-39d60491-b7e9-4a8c-a281-4c335deb3e4d.png)
![image](https://user-images.githubusercontent.com/87048623/129999387-8f1ab984-21a1-4d0c-af5c-dd77c9f88f3c.png)



## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문(order)->결제(payment) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
```
# (order) PaymentHistoryService.java

package hotelone.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="payment", url="${api.payment.url}")
public interface PaymentHistoryService {

    @RequestMapping(method= RequestMethod.POST, path="/paymentHistories")
    public void pay(@RequestBody PaymentHistory paymentHistory);

}
```

- 주문을 받은 직후(@PostPersist) 결제를 요청하도록 처리
```
# Order.java (Entity)
  
    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        //Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

//        hotelone.external.PaymentHistory paymentHistory = new hotelone.external.PaymentHistory();
        PaymentHistory payment = new PaymentHistory();
        System.out.println("this.id() : " + this.id);
        payment.setOrderId(this.id);
        payment.setStatus("Reservation OK");
        // mappings goes here
        OrderApplication.applicationContext.getBean(hotelone.external.PaymentHistoryService.class)
            .pay(payment);

```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:
```
# 결제 (payment) 서비스를 잠시 내려놓음 (ctrl+c)

#주문처리 #Fail
http localhost:8081/orders name=kim roomType=double   

```
![image](https://user-images.githubusercontent.com/87048623/129999620-a66e42bc-0dd6-412c-903e-7175fd590d1d.png)

```
#결제서비스 재기동
cd /home/project/team/hotelone/payment
mvn spring-boot:run

#주문처리 #Success
http localhost:8081/orders name=kim roomType=double  
```
![image](https://user-images.githubusercontent.com/87048623/129999773-53cdeb37-afd3-4e05-bd7a-9aea95d1be21.png)


## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 호텔 시스템으로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 호텔 시스템의 처리를 위하여 결제주문이 블로킹 되지 않도록 처리한다.
 
- 이를 위하여 결제이력에 기록을 남긴 후에 곧바로 결제승인이 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```
#PaymentHistory.java

package hotelone;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="PaymentHistory_table")
public class PaymentHistory {

...

    @PostPersist
    public void onPostPersist(){
        PaymentApproved paymentApproved = new PaymentApproved();
        paymentApproved.setStatus("Pay Approved!!");
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();
    }

```

- reservation 서비스에서는 결제승인 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다.

```
# PolicyHandler.java

package hotelone;

...

@Service
public class PolicyHandler{

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_(@Payload PaymentApproved paymentApproved){


        if(paymentApproved.isMe()){
            System.out.println("##### listener  : " + paymentApproved.toJson());	  
            Reservation reservation = new Reservation();
            reservation.setStatus("Reservation Complete");
            reservation.setOrderId(paymentApproved.getOrderId());
            reservationRepository.save(reservation);
            
        }
    }
    
}
```

reservation 시스템은 order/payment와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 호텔 시스템이 유지보수로 인해 잠시 내려간 상태라도 예약 주문을 받는데 문제가 없다.

```
# 예약 서비스 (reservation) 를 잠시 내려놓음 (ctrl+c)

# 주문 처리
http localhost:8081/orders name=Yoo roomType=standard   #Success
```
![image](https://user-images.githubusercontent.com/87048623/129999950-ca9b0a5c-1b87-4eff-af3c-8b7fc4651ad0.png)

```
# 예약상태 확인
http localhost:8084/mypages/4  # 예약상태 안바뀜 확인     
```
![image](https://user-images.githubusercontent.com/87048623/130000011-0ea13f64-81c3-4ccc-bc53-94f97fb8d9ab.png)

```
# reservation 서비스 기동
cd /home/project/team/hotelone/reservation
mvn spring-boot:run 

# 예약상태 확인
http localhost:8084/mypages/4   # 예약상태가 "Reservation Complete"로 확인
```
![image](https://user-images.githubusercontent.com/87048623/130000098-fb46ed65-62d2-4ae5-93d5-1b92c72c0b3a.png)


## API 게이트웨이(gateway)

API gateway 를 통해 MSA 진입점을 통일 시킨다.

```
# gateway 기동(8080 포트)
cd gateway
mvn spring-boot:run

# API gateway를 통한 예약 주문
http localhost:8080/orders name=jason roomType=suite
```
![image](https://user-images.githubusercontent.com/87048623/130000185-6ecac4c7-1f74-4c2e-8c61-3e1f152f42ac.png)

```
application.yml

server:
  port: 8080

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
        - id: reservation
          uri: http://localhost:8082
          predicates:
            - Path=/reservations/**,/cancellations/** 
        - id: payment
          uri: http://localhost:8083
          predicates:
            - Path=/paymentHistories/** 
        - id: customer
          uri: http://localhost:8084
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
        - id: reservation
          uri: http://reservation:8080
          predicates:
            - Path=/reservations/**,/cancellations/** 
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/paymentHistories/** 
        - id: customer
          uri: http://customer:8080
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
            
logging:
  level:
    root: debug

server:
  port: 8080

```


# 운영

## CI/CD 설정
