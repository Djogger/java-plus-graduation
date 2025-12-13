package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.model.EventSimilarity;
import ru.practicum.repository.EventSimilarityRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventSimilarityService {
    private final EventSimilarityRepository eventSimilarityRepository;

    @Transactional
    public void save(EventSimilarity eventSimilarity) {
        Optional<EventSimilarity> similarity =
                eventSimilarityRepository.findByEventAAndEventB(
                        eventSimilarity.getEventA(),
                        eventSimilarity.getEventB()
                );

        if (similarity.isEmpty()) {
            eventSimilarityRepository.save(eventSimilarity);
            return;
        }

        EventSimilarity oldSimilarity = similarity.get();

        if (oldSimilarity.getScore() != eventSimilarity.getScore()) {
            oldSimilarity.setScore(eventSimilarity.getScore());
        }

        eventSimilarityRepository.save(oldSimilarity);
    }

}
