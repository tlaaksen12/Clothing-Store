package clothingstore;

import clothingstore.config.kafka.KafkaProcessor;

import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

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
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrederCanceled_ShippingCancel(@Payload OrederCanceled orederCanceled){

        if(!orederCanceled.validate()) return;

        System.out.println("\n\n##### listener ShippingCancel : " + orederCanceled.toJson() + "\n\n");

        shippingRepository.findById(orederCanceled.getId()).ifPresent(shipping -> {
            shippingRepository.delete(shipping);
        });;

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
