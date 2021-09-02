package clothingstore;

import clothingstore.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class MyPageViewHandler {


    @Autowired
    private MyPageRepository myPageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrdered_then_CREATE_1 (@Payload Ordered ordered) {
        try {

            if (!ordered.validate()) return;

            // view 객체 생성
            MyPage myPage = new MyPage();
            // view 객체에 이벤트의 Value 를 set 함
            myPage.setId(ordered.getId());
            myPage.setClothingid(ordered.getClothingid());
            myPage.setAddress(ordered.getAddress());
            myPage.setCnt(ordered.getCnt());
            myPage.setPrice(ordered.getPrice());
            myPage.setStatus("Ordered");
            // view 레파지 토리에 save
            myPageRepository.save(myPage);

        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenPaymentApproved_then_UPDATE_1(@Payload PaymentApproved paymentApproved) {
        try {
            System.out.println("111111111111111111111111111111111111");
            System.out.println("111111111111111111111111111111111111");
            System.out.println("111111111111111111111111111111111111");
            System.out.println("111111111111111111111111111111111111");
            System.out.println("111111111111111111111111111111111111");
            System.out.println("111111111111111111111111111111111111");
            System.out.println(paymentApproved.validate());
            System.out.println(paymentApproved.getClass().getSimpleName());
            System.out.println(paymentApproved.getEventType());
            System.out.println("111111111111111111111111111111111111");
            System.out.println("111111111111111111111111111111111111");

            if (!paymentApproved.validate()) return;
                // view 객체 조회
                Optional<MyPage> myPageOptional = myPageRepository.findById(Long.valueOf(paymentApproved.getOrderId()).longValue());
                System.out.println("---------------------paymentApproved.getOrderId()"+paymentApproved.getOrderId()+"--------------------");
                System.out.println("---------------------paymentApproved.getOrderId()"+paymentApproved.getOrderId()+"--------------------");
                System.out.println("---------------------paymentApproved.getOrderId()"+paymentApproved.getOrderId()+"--------------------");
                System.out.println("---------------------paymentApproved.getOrderId()"+paymentApproved.getOrderId()+"--------------------");
                System.out.println("---------------------paymentApproved.getOrderId()"+paymentApproved.getOrderId()+"--------------------");
                System.out.println("---------------------paymentApproved.getOrderId()"+paymentApproved.getOrderId()+"--------------------");
                System.out.println("myPageOptional.isPresent()" + myPageOptional.isPresent());
                Optional<MyPage> myPageOptional2 = myPageRepository.findById((long)1);
                String strr = "1";
                Optional<MyPage> myPageOptional3 = myPageRepository.findById(Long.parseLong(strr));
                System.out.println("222"+myPageOptional2.isPresent());
                System.out.println("333"+myPageOptional3.isPresent());
            if( myPageOptional.isPresent()) {
                 MyPage myPage = myPageOptional.get();
            // view 객체에 이벤트의 eventDirectValue 를 set 함
                 myPage.setStatus("Paymented");
                // view 레파지 토리에 save
                 myPageRepository.save(myPage);
                }


        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenShipped_then_UPDATE_2(@Payload Shipped shipped) {
        try {
            if (!shipped.validate()) return;
                // view 객체 조회
            Optional<MyPage> myPageOptional = myPageRepository.findById(Long.valueOf(shipped.getOrderId()).longValue());

            if( myPageOptional.isPresent()) {
                 MyPage myPage = myPageOptional.get();
            // view 객체에 이벤트의 eventDirectValue 를 set 함
                 myPage.setStatus("Shipped");
                // view 레파지 토리에 save
                 myPageRepository.save(myPage);
                }


        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenShippingCanceled_then_DELETE_1(@Payload ShippingCanceled shippingCanceled) {
        try {
            if (!shippingCanceled.validate()) return;
            // view 레파지 토리에 삭제 쿼리
            myPageRepository.deleteById(Long.valueOf(shippingCanceled.getOrderId()).longValue());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}

