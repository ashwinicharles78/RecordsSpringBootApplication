package com.records.Records.service;

import com.records.Records.model.KafkaUserData;

import java.util.concurrent.ExecutionException;

public interface KafkaMessagePublisherService {
    void sendMessageToTopic(KafkaUserData message, String topic);
    void sendMessageToTopic(KafkaUserData message, String key, String topic);
}
