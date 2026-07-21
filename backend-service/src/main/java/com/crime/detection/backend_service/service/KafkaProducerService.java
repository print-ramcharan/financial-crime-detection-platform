package com.crime.detection.backend_service.service;

import com.crime.detection.backend_service.model.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class KafkaProducerService {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    public void sendTransaction(Transaction tx) {
        Map<String, Object> event = new HashMap<>();
        event.put("id", tx.getId());
        event.put("sender_account", tx.getSenderAccount());
        event.put("receiver_account", tx.getReceiverAccount());
        event.put("amount", tx.getAmount().doubleValue());
        event.put("currency", tx.getCurrency());
        event.put("country", tx.getCountry());
        event.put("timestamp", tx.getTimestamp().toString());

        kafkaTemplate.send("transactions", tx.getId(), event);
        System.out.println("Published transaction to Kafka: " + tx.getId());
    }
}
