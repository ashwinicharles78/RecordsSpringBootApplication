package com.records.Records.service;

import com.records.Records.model.KafkaUserData;

public interface KafkaMessagePublisherService {
    void sendMessageToTopic(KafkaUserData message, String topic);
}
