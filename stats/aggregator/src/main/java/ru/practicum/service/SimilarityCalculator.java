package ru.practicum.service;

import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.ActionTypeAvro;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.mapper.EventSimilarityAvroMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimilarityCalculator {
    private final static double VIEW_WEIGHT = 0.4;
    private final static double REGISTER_WEIGHT = 0.8;
    private final static double LIKE_WEIGHT = 1.0;

    private static final Map<ActionTypeAvro, Double> WEIGHTS = Map.of(
            ActionTypeAvro.VIEW, VIEW_WEIGHT,
            ActionTypeAvro.REGISTER, REGISTER_WEIGHT,
            ActionTypeAvro.LIKE, LIKE_WEIGHT
    );

    private final Map<Long, Map<Long, Double>> weightMatrix = new ConcurrentHashMap<>();

    private final Map<Long, Double> weightSumByEvent = new ConcurrentHashMap<>();

    private final Map<Long, Map<Long, Double>> minWeightsSums = new ConcurrentHashMap<>();

    private final Map<Long, Set<Long>> eventsByUser = new ConcurrentHashMap<>();

    private final Map<Long, Object> eventLocks = new ConcurrentHashMap<>();

    private Object lock(long eventId) {
        return eventLocks.computeIfAbsent(eventId, k -> new Object());
    }

    public List<EventSimilarityAvro> calculateSimilarity(UserActionAvro userActionAvro) {
        long eventId = userActionAvro.getEventId();
        long userId = userActionAvro.getUserId();
        double newWeight = WEIGHTS.get(userActionAvro.getActionType());

        synchronized (lock(eventId)) {
            Map<Long, Double> userWeights = weightMatrix.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>());

            double oldWeight = userWeights.getOrDefault(userId, 0.0);

            if (newWeight <= oldWeight) return Collections.emptyList();

            userWeights.put(userId, newWeight);

            double totalWeight = weightSumByEvent.getOrDefault(eventId, 0.0);
            totalWeight = totalWeight - oldWeight + newWeight;
            weightSumByEvent.put(eventId, totalWeight);

            Set<Long> userEvents = eventsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());

            List<EventSimilarityAvro> results = new ArrayList<>();

            for (long otherEventId : userEvents) {
                if (otherEventId == eventId) continue;

                Object lock1 = lock(Math.min(eventId, otherEventId));
                Object lock2 = lock(Math.max(eventId, otherEventId));

                synchronized (lock1) {
                    synchronized (lock2) {
                        double similarity = recalcForUserPair(eventId, otherEventId, userId, oldWeight, newWeight);
                        results.add(
                                EventSimilarityAvroMapper.mapToEventSimilarityAvro(eventId, otherEventId, similarity)
                        );
                    }
                }
            }

            userEvents.add(eventId);

            return results;
        }
    }

    private double recalcForUserPair(long eventA, long eventB, long userId, double oldWeightA, double newWeightA) {
        Map<Long, Double> userWeightsB = weightMatrix.getOrDefault(eventB, Map.of());

        double weightB = userWeightsB.getOrDefault(userId, 0.0);

        if (weightB == 0.0) {
            return getMinSum(eventA, eventB) /
                    (Math.sqrt(weightSumByEvent.getOrDefault(eventA, 0.0)) *
                            Math.sqrt(weightSumByEvent.getOrDefault(eventB, 0.0)));
        }

        double oldContribution = Math.min(oldWeightA, weightB);
        double newContribution = Math.min(newWeightA, weightB);
        double diff = newContribution - oldContribution;

        double updatedSum = getMinSum(eventA, eventB) + diff;
        putMinSum(eventA, eventB, updatedSum);

        double totalA = weightSumByEvent.getOrDefault(eventA, 0.0);
        double totalB = weightSumByEvent.getOrDefault(eventB, 0.0);

        if (totalA == 0 || totalB == 0) return 0.0;

        return updatedSum / (Math.sqrt(totalA) * Math.sqrt(totalB));
    }

    private void putMinSum(long eventA, long eventB, double sum) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        minWeightsSums.computeIfAbsent(first, x -> new ConcurrentHashMap<>()).put(second, sum);
    }

    private double getMinSum(long eventA, long eventB) {
        long first = Math.min(eventA, eventB);
        long second = Math.max(eventA, eventB);
        return minWeightsSums.getOrDefault(first, Map.of()).getOrDefault(second, 0.0);
    }

}
