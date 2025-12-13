package ru.practicum.service.handler.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.model.EventSimilarity;
import ru.practicum.service.EventSimilarityService;
import ru.practicum.service.handler.SimilarityHandler;

@Component
@RequiredArgsConstructor
public class SimilarityHandlerImpl implements SimilarityHandler {
    private final EventSimilarityService eventSimilarityService;

    @Override
    public void handle(EventSimilarityAvro eventSimilarityAvro) {
        EventSimilarity eventSimilarity = map(eventSimilarityAvro);
        eventSimilarityService.save(eventSimilarity);
    }

    EventSimilarity map(EventSimilarityAvro eventSimilarityAvro) {
        return EventSimilarity.builder()
                .eventA(eventSimilarityAvro.getEventA())
                .eventB(eventSimilarityAvro.getEventB())
                .score(eventSimilarityAvro.getScore())
                .build();
    }

}
