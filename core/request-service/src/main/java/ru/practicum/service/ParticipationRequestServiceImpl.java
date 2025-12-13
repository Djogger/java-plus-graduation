package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.contract.EventClient;
import ru.practicum.contract.UserClient;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.dto.user.UserDto;
import ru.practicum.enums.EventState;
import ru.practicum.enums.RequestStatus;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.ParticipationRequestMapper;
import ru.practicum.model.ParticipationRequest;
import ru.practicum.repository.ParticipationRequestRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {
    private final ParticipationRequestRepository requestRepository;
    private final UserClient userClient;
    private final EventClient eventClient;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Пользователь id={} создает запрос на участие в событии id={}", userId, eventId);

        EventFullDto eventFullDto = getEvent(eventId);

        if (requestRepository.existsByEventAndRequester(eventId, userId)) {
            throw new ConflictException(String.format(
                    "Запрос от пользователя %s на событие %d уже существует", userId, eventId
            ));
        }

        if (eventFullDto.getInitiator().equals(userId)) {
            throw new ConflictException("Инициатор не может подавать заявку на собственное событие");
        }

        if (eventFullDto.getState() != EventState.PUBLISHED) {
            EventState status = eventFullDto.getState();
            throw new ConflictException("Нельзя подать заявку на неопубликованное событие. Текущий статус: " + status);
        }

        if (eventFullDto.getParticipantLimit() > 0) {
            long confirmedCount = requestRepository.countByEventAndStatus(eventId, RequestStatus.CONFIRMED);
            if (confirmedCount >= eventFullDto.getParticipantLimit()) {
                throw new ConflictException("Лимит участников для события " + eventId + " был достигнут.");
            }
        }

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(userId)
                .event(eventFullDto.getId())
                .created(LocalDateTime.now())
                .build();

        boolean needsModeration = eventFullDto.getRequestModeration() && eventFullDto.getParticipantLimit() != 0;
        request.setStatus(needsModeration ? RequestStatus.PENDING : RequestStatus.CONFIRMED);

        ParticipationRequest savedRequest = requestRepository.save(request);
        log.info("Успешно создан запрос id={} со статусом {}", savedRequest.getId(), savedRequest.getStatus());

        return ParticipationRequestMapper.toParticipationRequestDto(savedRequest);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequests(
            Long userId, Long eventId, EventRequestStatusUpdateRequest statusUpdateRequest
    ) {
        log.info(
                "Пользователь id={} обновляет статусы заявок {} для события id={}",
                userId, statusUpdateRequest.getRequestIds(), eventId
        );

        EventFullDto eventFullDto = getEvent(eventId);

        if (!eventFullDto.getInitiator().equals(userId)) {
            throw new ConflictException("Только инициатор события может обновлять статусы заявок.");
        }

        if (!eventFullDto.getRequestModeration() || eventFullDto.getParticipantLimit() == 0) {
            log.warn("Событие id={} не требует модерации заявок или не имеет лимита.", eventId);
            return new EventRequestStatusUpdateResult(List.of(), List.of());
        }

        List<ParticipationRequest> requestsToUpdate =
                requestRepository.findAllByIdIn(statusUpdateRequest.getRequestIds());

        if (requestsToUpdate.stream().anyMatch(req -> req.getStatus() != RequestStatus.PENDING)) {
            throw new ConflictException("Можно изменять только заявки в статусе PENDING.");
        }

        List<ParticipationRequest> confirmedRequests = new ArrayList<>();
        List<ParticipationRequest> rejectedRequests = new ArrayList<>();
        RequestStatus newStatus = statusUpdateRequest.getStatus();

        long currentConfirmedCountInEventColumn = eventFullDto.getConfirmedRequests() != null
                ? eventFullDto.getConfirmedRequests()
                : 0L;

        long limit = eventFullDto.getParticipantLimit();

        if (newStatus == RequestStatus.REJECTED) {
            requestsToUpdate.forEach(request -> request.setStatus(RequestStatus.REJECTED));
            rejectedRequests.addAll(requestsToUpdate);
        } else if (newStatus == RequestStatus.CONFIRMED) {
            if (currentConfirmedCountInEventColumn >= limit) {
                requestsToUpdate.forEach(request -> request.setStatus(RequestStatus.REJECTED));
                rejectedRequests.addAll(requestsToUpdate);
                throw new ConflictException("Лимит участников уже достигнут. Невозможно подтвердить новые заявки.");
            }

            for (ParticipationRequest request : requestsToUpdate) {
                if (currentConfirmedCountInEventColumn < limit) {
                    request.setStatus(RequestStatus.CONFIRMED);
                    confirmedRequests.add(request);
                    currentConfirmedCountInEventColumn++;
                } else {
                    request.setStatus(RequestStatus.REJECTED);
                    rejectedRequests.add(request);
                }
            }

            if (currentConfirmedCountInEventColumn >= limit) {
                List<ParticipationRequest> otherPendingRequests =
                        requestRepository.findAllByEventAndStatus(eventId, RequestStatus.PENDING);
                otherPendingRequests.forEach(req -> req.setStatus(RequestStatus.REJECTED));
                rejectedRequests.addAll(otherPendingRequests);
                log.info(
                        "Достигнут лимит участников для события {}. Автоматически отклонено {} других заявок.",
                        eventId, otherPendingRequests.size()
                );
            }

            eventFullDto.setConfirmedRequests(currentConfirmedCountInEventColumn);
        }

        requestRepository.saveAll(confirmedRequests);
        requestRepository.saveAll(rejectedRequests);
        requestRepository.flush();

        eventClient.updateConfirmedRequestsForEvent(eventFullDto.getId(), eventFullDto.getConfirmedRequests());

        return new EventRequestStatusUpdateResult(
                toDtoList(confirmedRequests),
                toDtoList(rejectedRequests)
        );
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Получение всех заявок на участие для пользователя id={}", userId);
        getUserDto(userId);
        return toDtoList(requestRepository.findAllByRequester(userId));
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByOwner(Long userId, Long eventId) {
        log.info("Владелец id={} получает заявки для своего события id={}", userId, eventId);
        EventFullDto eventFullDto = getEvent(eventId);

        if (!eventFullDto.getInitiator().equals(userId)) {
            throw new ConflictException("Пользователь " + userId + " не является инициатором события " + eventId);
        }

        return toDtoList(requestRepository.findAllByEvent(eventId));
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Пользователь id={} отменяет свой запрос id={}", userId, requestId);
        ParticipationRequest request = requestRepository.findByIdAndRequester(requestId, userId)
                .orElseThrow(() -> new NotFoundException(String.format(
                        "Запрос с id=%d от пользователя c id=%d - не найден", requestId, userId
                )));

        if (request.getStatus() == RequestStatus.CONFIRMED) {
            throw new ConflictException("Невозможно отменить уже подтвержденную заявку.");
        }

        request.setStatus(RequestStatus.CANCELED);
        return ParticipationRequestMapper.toParticipationRequestDto(requestRepository.save(request));
    }

    private EventFullDto getEvent(Long eventId) {
        return eventClient.getEvent(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено."));
    }

    private UserDto getUserDto(Long userId) {
        return findUserById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + userId + " не найден."));
    }

    private List<ParticipationRequestDto> toDtoList(List<ParticipationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        return requests.stream()
                .map(ParticipationRequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    public long countEventsInStatus(Long eventId, RequestStatus status) {
        return requestRepository.countByEventAndStatus(eventId, status);
    }

    @Override
    public Map<Long, Long> countConfirmedRequestsForEvents(Set<Long> eventIds) {
        return requestRepository.countConfirmedRequestsForEvents(eventIds);
    }

    private Optional<UserDto> findUserById(Long userId) {
        List<UserDto> userDtos = userClient.getUsers(List.of(userId));
        return userDtos.isEmpty() ? Optional.empty() : Optional.of(userDtos.getFirst());
    }
}
