package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.practicum.config.KafkaClient;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.service.handler.UserActionHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProcessor implements Runnable {
    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    private final KafkaClient kafkaClient;
    private final UserActionHandler userActionHandler;

    @Override
    public void run() {
        Consumer<Long, SpecificRecordBase> consumer = kafkaClient.getConsumerAction();

        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));

        try {
            consumer.subscribe(List.of(kafkaClient.getTopicsProperties().getStatsUserActionV1()));

            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(kafkaClient.getPollTimeout());

                if (!records.isEmpty()) {
                    AtomicInteger index = new AtomicInteger(0);
                    records.forEach(record -> {
                        handleRecord(record);
                        manageOffsets(record, index.getAndIncrement(), consumer);
                    });

                    consumer.commitAsync();
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка обработки событий от сенсоров", e);
        } finally {
            try {
                consumer.commitSync(currentOffsets);
            } finally {
                consumer.close();
            }
        }
    }

    private void manageOffsets(
            ConsumerRecord<Long, SpecificRecordBase> record,
            int count,
            Consumer<Long, SpecificRecordBase> consumer
    ) {
        currentOffsets.put(
                new TopicPartition(record.topic(), record.partition()),
                new OffsetAndMetadata(record.offset() + 1)
        );

        if (count % 10 == 0) {
            consumer.commitAsync(currentOffsets, (offsets, exception) -> {
                if (Objects.nonNull(exception)) log.warn("Ошибка офсета: {}", offsets, exception);
            });
        }
    }

    private void handleRecord(ConsumerRecord<Long, SpecificRecordBase> record) {
        if (record.value() instanceof UserActionAvro userActionAvro) {
            log.info(
                    "Получено действие пользователя: userId = {}, eventId = {}, type = {}",
                    userActionAvro.getUserId(),
                    userActionAvro.getEventId(),
                    userActionAvro.getActionType()
            );
            userActionHandler.handle(userActionAvro);
        }
    }
}
