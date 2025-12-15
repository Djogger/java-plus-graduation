package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.proto.InteractionsCountRequestProto;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.ewm.stats.proto.SimilarEventsRequestProto;
import ru.practicum.ewm.stats.proto.UserPredictionsRequestProto;
import ru.practicum.mapper.RecommendedEventProtoMapper;
import ru.practicum.model.EventSimilarity;
import ru.practicum.model.UserAction;
import ru.practicum.repository.EventSimilarityRepository;
import ru.practicum.repository.UserActionRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final UserActionRepository userActionRepository;
    private final EventSimilarityRepository eventSimilarityRepository;

    public Stream<RecommendedEventProto> getRecommendationsForUser(UserPredictionsRequestProto request) {
        long userId = request.getUserId();

        List<Long> userEventIds = userActionRepository.findByUserId(userId, PageRequest.of(
                0,
                request.getMaxResult(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        ));

        if (userEventIds.isEmpty()) {
            return Stream.empty();
        }

        PageRequest pageRequest = PageRequest.of(
                0, request.getMaxResult(),
                Sort.by(Sort.Direction.DESC, "score")
        );

        List<EventSimilarity> similarities = eventSimilarityRepository.findNewSimilar(userEventIds, pageRequest);
        Map<Long, Double> predictedScores = predictScores(similarities, userEventIds);

        return RecommendedEventProtoMapper.mapToProto(predictedScores);
    }

    public Stream<RecommendedEventProto> getSimilarEvents(SimilarEventsRequestProto request) {
        long eventId = request.getEventId();

        List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);
        Set<Long> userEventIds = userActionRepository.findByUserIdExcludeEventId(request.getUserId(), eventId);

        similarities = similarities.stream()
                .filter(s -> !(userEventIds.contains(s.getEventA()) || userEventIds.contains(s.getEventB())))
                .collect(Collectors.toList());

        similarities.sort(Comparator.comparingDouble(EventSimilarity::getScore).reversed());

        int n = Math.min(request.getMaxResult(), similarities.size());
        similarities = similarities.subList(0, n);

        return RecommendedEventProtoMapper.mapToProto(similarities, eventId);
    }

    public Stream<RecommendedEventProto> getInteractionsCount(InteractionsCountRequestProto request) {
        Map<Long, Double> sumOfWeightsByEvent = new HashMap<>();
        List<UserAction> userActions = userActionRepository.findByEventIdIn(new HashSet<>(request.getEventIdList()));

        for (UserAction userAction : userActions) {
            long eventId = userAction.getEventId();
            double sum = sumOfWeightsByEvent.getOrDefault(eventId, 0.0);
            double weight = userAction.getWeight();
            sum += weight;
            sumOfWeightsByEvent.put(eventId, sum);
        }

        return RecommendedEventProtoMapper.mapToProto(sumOfWeightsByEvent);
    }

    private Map<Long, Double> predictScores(List<EventSimilarity> similarities, List<Long> userEvents) {
        Map<Long, Double> scoreByEvent = new HashMap<>();

        for (EventSimilarity s : similarities) {
            long candidate = userEvents.contains(s.getEventA()) ? s.getEventB() : s.getEventA();

            PageRequest pageRequest = PageRequest.of(
                    0, 5, Sort.by(Sort.Direction.DESC, "score")
            );

            List<EventSimilarity> neighbours = eventSimilarityRepository.findNeighbours(candidate, pageRequest);

            List<Long> neighboursIds = neighbours.stream()
                    .map(n -> (n.getEventA() == candidate)
                            ? n.getEventB()
                            : n.getEventA()).toList();

            List<UserAction> userActions = userActionRepository.findByEventIdIn(new HashSet<>(neighboursIds));

            double predictedScore = calculatePredictedScore(neighbours, userActions, candidate);

            scoreByEvent.put(candidate, predictedScore);
        }

        return scoreByEvent;
    }

    private double calculatePredictedScore(List<EventSimilarity> neighbours, List<UserAction> userActions, long candidateId) {
        Map<Long, Double> weightByEvent = userActions.stream()
                .collect(Collectors.toMap(UserAction::getEventId, UserAction::getWeight));

        double sumOfWeightedScore = 0.0;
        double sumOfSimilarity = 0.0;

        for (EventSimilarity neighbour : neighbours) {
            long neighbourId = (neighbour.getEventA() == candidateId)
                    ? neighbour.getEventB()
                    : neighbour.getEventA();

            double weight = weightByEvent.getOrDefault(neighbourId, 0.0);
            double similarity = neighbour.getScore();
            double weightedScore = weight * similarity;

            sumOfWeightedScore += weightedScore;
            sumOfSimilarity += similarity;
        }

        return sumOfSimilarity == 0 ? 0.0 : sumOfWeightedScore / sumOfSimilarity;
    }

}
