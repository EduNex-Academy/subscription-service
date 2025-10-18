package com.edu.subscription_service.service;

import com.edu.subscription_service.event.SubscriptionPushEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushNotificationProducer {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationProducer.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "subscription-events-topic";

    public void sendPush(SubscriptionPushEvent event) {
        kafkaTemplate.send(TOPIC, event);
        logger.info("Sent push event to Kafka topic {}: {}", TOPIC, event);
    }
}

