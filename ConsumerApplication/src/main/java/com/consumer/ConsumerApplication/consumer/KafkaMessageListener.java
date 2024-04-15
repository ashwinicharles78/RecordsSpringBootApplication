package com.consumer.ConsumerApplication.consumer;

import com.records.Records.model.KafkaUserData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaMessageListener {
    Logger log = LoggerFactory.getLogger(KafkaMessageListener.class);

    @KafkaListener(topics = "test",groupId = "consumer-group")
    public void consumeEvents(KafkaUserData userData) {
        log.info("consumer consume the events {} ", userData.getEmail());
    }
}
