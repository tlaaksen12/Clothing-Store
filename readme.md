# clothingstore

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
C:\dev\vs\clothing-store\payment
mvn spring-boot:run

C:\dev\vs\clothing-store\order
mvn spring-boot:run 

C:\dev\vs\clothing-store\shipping
mvn spring-boot:run  

C:\dev\vs\clothing-store\mypage
mvn spring-boot:run 
```

## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 shipping 마이크로 서비스). 이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하려고 노력했다.

```
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

```

- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package clothingstore;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="shippings", path="shippings")
public interface ShippingRepository extends PagingAndSortingRepository<Shipping, Long>{


}

```

- 적용 후 REST API 의 테스트
```
# order 서비스의 주문처리
http http://localhost:8088/orders clothingid='CA' price=1000 address='myhouse' cnt=1 cardno='1234'

# shipping 서비스의 배송처리
http http://localhost:8088/shippings orderId=1 status='shipped'


```
![image](https://user-images.githubusercontent.com/87048693/131695160-c2df4870-9fc9-436b-8d78-ddb0460d174f.png)
![image](https://user-images.githubusercontent.com/87048693/131695174-bc53182b-ec3e-4556-8b1a-fd7edf5e7f4c.png)



## CQRS

- 고객의 예약정보를 한 눈에 볼 수 있게 mypage를 구현 한다.

```
# 주문 상태 확인
http http://localhost:8088/myPages/3
```
![image](https://user-images.githubusercontent.com/87048693/131695591-12ff4e17-65d0-41e3-b7c0-2acae6db4f56.png)


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
http http://localhost:8088/orders clothingid='HO' price=1200 address='BKhouse' cnt=1 cardno='5524'

# 저장이 잘 되었는지 조회
http http://localhost:8088/myPages/1

```

![image](https://user-images.githubusercontent.com/87048693/131696970-ffcc3873-1f54-4945-b6ac-75967628c0f2.png)
![image](https://user-images.githubusercontent.com/87048693/131696991-a09c9719-1972-43fe-8e2b-52eae3ecd212.png)



## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문(order)->결제(payment) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 
```
# (order) PaymentService.java

package clothingstore.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="payment", url="http://localhost:8088")
public interface PaymentService {
    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void payment(@RequestBody Payment payment);

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

        Payment payment = new Payment();
        //payment.setId(this.id);
        //payment.setCardno(this.cardno);
        BeanUtils.copyProperties(ordered, payment);
        payment.setOrderId(this.id.toString());
        payment.setStatus("Ordered OK");
        OrderApplication.applicationContext.getBean(clothingstore.external.PaymentService.class).payment(payment);        

    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:
```
# 결제 (payment) 서비스를 잠시 내려놓음 (ctrl+c)

#주문처리 #Fail
 http http://localhost:8088/orders clothingid='HO' price=1200 address='BKhouse' cnt=1 cardno='5524'

```
![image](https://user-images.githubusercontent.com/87048693/131699734-83a828d9-4b89-4bbb-9e77-194d2f4d2154.png)

```
#결제서비스 재기동
C:\dev\vs\clothing-store\payment
mvn spring-boot:run

#주문처리 #Success
 http http://localhost:8088/orders clothingid='HO' price=1200 address='BKhouse' cnt=1 cardno='5524'
```
![image](https://user-images.githubusercontent.com/87048693/131699771-52ef9e0b-3197-4802-b3ce-58017f839ed7.png)



## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 호텔 시스템으로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 호텔 시스템의 처리를 위하여 결제주문이 블로킹 되지 않도록 처리한다.
 
- 이를 위하여 결제이력에 기록을 남긴 후에 곧바로 결제승인이 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```
#Payment.java

package clothingstore;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;

@Entity
@Table(name="Payment_table")
public class Payment {

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

package clothingstore;

...

@Service
public class PolicyHandler{
    @Autowired ShippingRepository shippingRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_Ship(@Payload PaymentApproved paymentApproved){

        if(!paymentApproved.validate()) return;

        System.out.println("\n\n##### listener Ship : " + paymentApproved.toJson() + "\n\n");


        Shipping shipping = new Shipping();
        shipping.setAddress(paymentApproved.getAddress());
        shipping.setClothingid(paymentApproved.getClothingid());
        shipping.setCnt(paymentApproved.getCnt().toString());
        shipping.setOrderId(paymentApproved.getId().toString());
        shippingRepository.save(shipping);

    }
}
```

shipping 시스템은 order/payment와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 호텔 시스템이 유지보수로 인해 잠시 내려간 상태라도 예약 주문을 받는데 문제가 없다.

```
# 예약 서비스 (shipping) 를 잠시 내려놓음 (ctrl+c)

# 주문 처리
http http://localhost:8088/orders clothingid='HO' price=1200 address='BKhouse' cnt=1 cardno='5524'   #Success
```
![image](https://user-images.githubusercontent.com/87048693/131704227-f81e1314-527f-45c1-86ec-ccb2ca355c79.png)

```
# 예약상태 확인
http http://localhost:8088/myPages/1 # 배달으로 안바뀜 확인     
```
![image](https://user-images.githubusercontent.com/87048693/131704294-723eb003-9a5c-4795-9167-8543d4d90d98.png)

```
# shipping 서비스 기동
C:\dev\vs\clothing-store\shipping
mvn spring-boot:run 

# 예약상태 확인
http http://localhost:8088/myPages/1   # 상태가 "Shipped"로 확인
```
![image](https://user-images.githubusercontent.com/87048693/131704535-3b51b1f2-d403-47ec-a942-7d94c9dc0fed.png)



## API 게이트웨이(gateway)

API gateway 를 통해 MSA 진입점을 통일 시킨다.

```
# gateway 기동(8088 포트)
C:\dev\vs\clothing-store\gateway
mvn spring-boot:run 

# API gateway를 통한 주문
http http://localhost:8088/orders clothingid='HO' price=1200 address='BKhouse' cnt=1 cardno='5524'
```
![image](https://user-images.githubusercontent.com/87048693/131705041-e7ec2a7a-3b4e-4e5b-b2c5-22bbed6039cd.png)


```
application.yml

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
        - id: shipping
          uri: http://localhost:8082
          predicates:
            - Path=/shippings/** 
        - id: payment
          uri: http://localhost:8083
          predicates:
            - Path=/payments/** 
        - id: mypage
          uri: http://localhost:8084
          predicates:
            - Path= /myPages/**
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
        - id: shipping
          uri: http://shipping:8080
          predicates:
            - Path=/shippings/** 
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/payments/** 
        - id: mypage
          uri: http://mypage:8080
          predicates:
            - Path= /myPages/**
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


# 운영

## CI/CD 설정
