package clothingstore;

import clothingstore.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired PaymentRepository paymentRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverShippingCanceled_Cancelpayment(@Payload ShippingCanceled shippingCanceled){

        if(!shippingCanceled.validate()) return;

        System.out.println("\n\n##### listener Cancelpayment : " + shippingCanceled.toJson() + "\n\n");

        paymentRepository.findById(shippingCanceled.getId()).ifPresent(payment -> {
            paymentRepository.delete(payment);
        });;

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
