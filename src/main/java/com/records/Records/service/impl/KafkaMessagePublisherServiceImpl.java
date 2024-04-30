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
import java.util.concurrent.ExecutionException;

@Service
public class KafkaMessagePublisherServiceImpl implements KafkaMessagePublisherService {

    @Autowired
    private KafkaTemplate<String,Object> template;

    private final Logger logger = LoggerFactory.getLogger(KafkaMessagePublisherServiceImpl.class);

    @Override
    public void sendMessageToTopic(KafkaUserData user, String topic){
            CompletableFuture<SendResult<String, Object>> result = template.send(topic, user);
            result.whenComplete((finalResult, ex)->{
                        if(ex == null)
                            logger.info("Sent message=[%s] with offset=[%d], partition=[%d] and key=[%s]".formatted(user.toString(), finalResult.getRecordMetadata().offset(), finalResult.getRecordMetadata().partition(), finalResult.getProducerRecord().key()));
                        else
                            logger.error("Unable to send message", ex.getMessage());
                    });
    }

    @Override
    public void sendMessageToTopic(KafkaUserData message, String key, String topic) {
        CompletableFuture<SendResult<String, Object>> result = template.send(topic, key, message);
        result.whenComplete((finalResult, ex)->{
            if(ex == null)
                logger.info("Sent message=[%s] with offset=[%d], partition=[%d] and key=[%s]".formatted(message.toString(), finalResult.getRecordMetadata().offset(), finalResult.getRecordMetadata().partition(), finalResult.getProducerRecord().key()));
            else
                logger.error("Unable to send message", ex.getMessage());
        });
    }
}
