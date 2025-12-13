package ru.practicum.mapper;

import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.model.EventSimilarity;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class RecommendedEventProtoMapper {
    private RecommendedEventProtoMapper() {
    }

    public static Stream<RecommendedEventProto> mapToProto(List<EventSimilarity> eventSimilarities, long currentEventId) {
        return eventSimilarities.stream()
                .map(similarity -> {
                    long recommended = (similarity.getEventA() == currentEventId)
                            ? similarity.getEventB()
                            : similarity.getEventA();
                    return RecommendedEventProto.newBuilder()
                            .setEventId(recommended)
                            .setScore(similarity.getScore())
                            .build();
                });
    }

    public static Stream<RecommendedEventProto> mapToProto(Map<Long, Double> scoreByEvent) {
        return scoreByEvent.entrySet().stream()
                .map(p -> RecommendedEventProto.newBuilder()
                        .setEventId(p.getKey())
                        .setScore(p.getValue())
                        .build());
    }

}
