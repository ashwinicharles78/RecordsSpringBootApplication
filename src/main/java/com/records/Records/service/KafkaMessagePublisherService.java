package com.records.Records.service;

import com.records.Records.model.UserModel;

public interface KafkaMessagePublisherService {
    void sendMessageToTopic(UserModel message, String topic);
}
