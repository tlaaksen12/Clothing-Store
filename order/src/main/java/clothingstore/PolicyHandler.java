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
    @Autowired OrderRepository orderRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverShipped_UpdateStatus(@Payload Shipped shipped){

        if(!shipped.validate()) return;

        System.out.println("\n\n##### listener UpdateStatus : " + shipped.toJson() + "\n\n");
        orderRepository.findById(shipped.getId()).ifPresent(order -> {
            order.setStatus("Shipped");
            orderRepository.save(order);    
        });

    }
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverShippingCanceled_UpdateStatus(@Payload ShippingCanceled shippingCanceled){

        if(!shippingCanceled.validate()) return;

        System.out.println("\n\n##### listener UpdateStatus : " + shippingCanceled.toJson() + "\n\n");

        orderRepository.findById(shippingCanceled.getId()).ifPresent(order -> {
            orderRepository.delete(order);    
        });

    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString){}


}
