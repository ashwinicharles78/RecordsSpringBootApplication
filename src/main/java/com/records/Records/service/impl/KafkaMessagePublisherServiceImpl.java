package com.records.Records.service.impl;

import com.records.Records.model.KafkaUserData;
import com.records.Records.service.KafkaMessagePublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaMessagePublisherServiceImpl implements KafkaMessagePublisherService {

    @Autowired
    private KafkaTemplate<String,Object> template;

    private final Logger logger = LoggerFactory.getLogger(KafkaMessagePublisherServiceImpl.class);

    @Override
    public void sendMessageToTopic(KafkaUserData user, String topic) {
        CompletableFuture<SendResult<String, Object>> future = template.send(topic, user);
        future.whenComplete((result,ex)->{
            if (ex == null) {
                logger.info("Sent message=[" + user.toString() +
                        "] with offset=[" + result.getRecordMetadata().offset() + "]");
            } else {
                logger.info("Unable to send message=[" +
                        user.toString() + "] due to : " + ex.getMessage());
            }
        });

    }
}
