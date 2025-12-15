package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.practicum.config.KafkaClient;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregatorProcessor {
    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();

    private final KafkaClient kafkaClient;
    private final SimilarityCalculator calculator;

    public void start() {
        Consumer<Long, SpecificRecordBase> consumer = kafkaClient.getConsumer();
        Producer<Long, SpecificRecordBase> producer = kafkaClient.getProducer();

        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));

        try {
            consumer.subscribe(List.of(kafkaClient.getTopicsProperties().getStatsUserActionV1()));

            while (true) {
                ConsumerRecords<Long, SpecificRecordBase> records = consumer.poll(kafkaClient.getPollTimeout());

                if (!records.isEmpty()) {
                    AtomicInteger index = new AtomicInteger(0);
                    records.forEach(record -> {
                        handleRecord(record, producer);
                        manageOffsets(record, index.getAndIncrement(), consumer);
                    });

                    consumer.commitAsync();
                }
            }
        } catch (WakeupException ignored) {
        } catch (Exception e) {
            log.error("Ошибка обработки событий от сенсоров:", e);
        } finally {
            try {
                producer.flush();
                consumer.commitSync(currentOffsets);
            } finally {
                consumer.close();
                producer.close();
            }
        }
    }

    private void manageOffsets(ConsumerRecord<Long, SpecificRecordBase> record, int count, Consumer<Long, SpecificRecordBase> consumer) {
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

    private void handleRecord(
            ConsumerRecord<Long, SpecificRecordBase> record,
            Producer<Long, SpecificRecordBase> producer
    ) {
        if (record.value() instanceof UserActionAvro action) {
            List<EventSimilarityAvro> similarities = calculator.calculateSimilarity(action);

            if (similarities.isEmpty()) return;

            for (EventSimilarityAvro similarity : similarities) {
                long timestamp = similarity.getTimestamp().toEpochMilli();

                ProducerRecord<Long, SpecificRecordBase> similarityRecord = new ProducerRecord<>(
                        kafkaClient.getTopicsProperties().getStatsEventsSimilarityV1(),
                        null,
                        timestamp,
                        similarity.getEventA(),
                        similarity
                );

                producer.send(similarityRecord, (metadata, ex) -> {
                    if (Objects.nonNull(ex)) {
                        log.error(
                                "Ошибка отправки события A: {}, события B: {}: {}",
                                similarity.getEventA(),
                                similarity.getEventB(),
                                ex.getMessage(),
                                ex
                        );
                    } else {
                        log.info(
                                "События A: {} и B: {} отправлены на партицию: {} - офсета: {}",
                                similarity.getEventA(),
                                similarity.getEventB(),
                                metadata.partition(),
                                metadata.offset()
                        );
                    }
                });
            }
        } else {
            log.warn("Внимание: {}", record.value());
        }
    }
}
