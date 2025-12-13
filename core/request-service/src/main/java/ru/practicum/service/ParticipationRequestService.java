package ru.practicum.service;

import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.enums.RequestStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ParticipationRequestService {

    @Transactional
    ParticipationRequestDto createRequest(Long userId, Long eventId);

    @Transactional
    EventRequestStatusUpdateResult updateRequests(
            Long userId, Long eventId, EventRequestStatusUpdateRequest statusUpdateRequest
    );

    List<ParticipationRequestDto> getUserRequests(Long userId);

    long countEventsInStatus(Long eventId, RequestStatus status);

    Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds);

    List<ParticipationRequestDto> getRequestsByOwner(Long userId, Long eventId);

    @Transactional
    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    boolean isUserParticipant(Long userId, Long eventId);

}
