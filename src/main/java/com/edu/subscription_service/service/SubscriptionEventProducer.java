package com.edu.subscription_service.service;

import com.edu.subscription_service.event.SubscriptionEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionEventProducer.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "push-notification-topic";

    public void sendEvent(SubscriptionEvent event) {
        kafkaTemplate.send(TOPIC, event);
        logger.info("Sent event to Kafka topic {}: {}", TOPIC, event);
    }
}
