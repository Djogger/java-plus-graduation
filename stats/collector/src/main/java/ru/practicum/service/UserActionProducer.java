package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import ru.practicum.config.KafkaTopicsProperties;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Component
@RequiredArgsConstructor
public class UserActionProducer {
    private final Producer<Long, SpecificRecordBase> producer;
    private final KafkaTopicsProperties kafkaTopics;

    private void send(String topic, SpecificRecordBase event, long timestamp, Long key) {
        producer.send(new ProducerRecord<>(topic, null, timestamp, key, event));
    }

    public void sendUserAction(SpecificRecordBase userAction) {
        UserActionAvro avroAction = (UserActionAvro) userAction;
        Long userId = avroAction.getUserId();
        long timestamp = avroAction.getTimestamp().toEpochMilli();
        send(kafkaTopics.getStatsUserActionV1(), avroAction, timestamp, userId);
    }

}
