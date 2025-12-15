package ru.practicum.service.event;

import feign.FeignException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.contract.ParticipationRequestClient;
import ru.practicum.contract.UserClient;
import ru.practicum.dto.event.*;
import ru.practicum.dto.location.LocationDto;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.dto.user.UserDto;
import ru.practicum.enums.*;
import ru.practicum.ewm.stats.proto.RecommendedEventProto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.ActionType;
import ru.practicum.AnalyzerGrpcClient;
import ru.practicum.CollectorGrpcClient;
import ru.practicum.mapper.EventMapper;
import ru.practicum.mapper.LocationMapper;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.Location;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.LocationRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final UserClient userClient;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final ParticipationRequestClient participationRequestClient;
    private final EntityManager entityManager;
    private final CollectorGrpcClient collectorGrpcClient;
    private final AnalyzerGrpcClient analyzerGrpcClient;

    @Override
    @Transactional
    public EventFullDto createEvent(NewEventDto newEventDto, Long userId) {
        validateEventDate(newEventDto.getEventDate(), 1);

        Optional<UserDto> userDto = findUserById(userId);
        if (userDto.isEmpty()) throw new NotFoundException("Пользователь с ID=" + userId + " не найден.");

        Category category = findCategoryById(newEventDto.getCategory());

        Location location = getLocation(newEventDto.getLocation());
        Event event = EventMapper.toEvent(newEventDto, category, userDto.get(), location);

        return EventMapper.toFullEventDto(eventRepository.save(event));
    }

    @Override
    public Optional<EventFullDto> getEvent(Long eventId) {
        return eventRepository.findById(eventId).map(EventMapper::toFullEventDto);
    }

    @Override
    public EventRequestStatusUpdateResult updateParticipationRequestStatus(
            Long userId, Long eventId, EventRequestStatusUpdateRequest requestStatusUpdateDto
    ) {
        try {
            return participationRequestClient.updateRequestStatus(userId, eventId, requestStatusUpdateDto);
        } catch (FeignException.Conflict e) {
            throw new ConflictException(e.getMessage());
        }
    }

    @Override
    public List<ParticipationRequestDto> getEventParticipationRequests(Long userId, Long eventId) {
        return participationRequestClient.getRequestsForEventByOwner(userId, eventId);
    }

    @Override
    public void updateConfirmedRequests(Long eventId, Long confirmedRequests) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Событие не найдено"));
        event.setConfirmedRequests(confirmedRequests);
        eventRepository.save(event);
    }

    @Override
    public List<EventShortDto> getEvents(Long userId, Integer from, Integer size) {
        Optional<UserDto> userDto = findUserById(userId);
        if (userDto.isEmpty()) throw new NotFoundException("Пользователь с ID=" + userId + " не найден.");

        Pageable page = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiator(userId, page);

        return EventMapper.toEventShortDtoList(events);
    }

    @Override
    public EventFullDto getEventByUser(Long userId, Long eventId) {
        Event event = findEventByIdAndInitiatorId(eventId, userId);
        long confirmedRequests = participationRequestClient.countEventsInStatus(eventId, RequestStatus.CONFIRMED);

        return EventMapper.toFullEventDto(event, confirmedRequests);
    }

    private void updateEventFromAdminRequest(Event event, UpdateEventAdminRequest dto) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());

        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate(), 1);
            event.setEventDate(dto.getEventDate());
        }

        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());

        if (dto.getCategory() != null) {
            Category category = findCategoryById(dto.getCategory());
            event.setCategory(category);
        }

        if (dto.getLocation() != null) event.setLocation(getLocation(dto.getLocation()));
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
    }

    private void updateEventFromUserRequest(Event event, UpdateEventUserRequest dto) {
        if (dto.getAnnotation() != null) event.setAnnotation(dto.getAnnotation());
        if (dto.getDescription() != null) event.setDescription(dto.getDescription());

        if (dto.getEventDate() != null) {
            validateEventDate(dto.getEventDate(), 1);
            event.setEventDate(dto.getEventDate());
        }

        if (dto.getPaid() != null) event.setPaid(dto.getPaid());
        if (dto.getParticipantLimit() != null) event.setParticipantLimit(dto.getParticipantLimit());
        if (dto.getRequestModeration() != null) event.setRequestModeration(dto.getRequestModeration());
        if (dto.getTitle() != null) event.setTitle(dto.getTitle());

        if (dto.getCategory() != null) {
            Category category = findCategoryById(dto.getCategory());
            event.setCategory(category);
        }

        if (dto.getLocation() != null) event.setLocation(getLocation(dto.getLocation()));
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        Event event = findEventByIdAndInitiatorId(eventId, userId);

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя изменить уже опубликованное событие.");
        }

        updateEventFromUserRequest(event, updateRequest);

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == UserEventStateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (updateRequest.getStateAction() == UserEventStateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        Event updatedEvent = eventRepository.save(event);
        return EventMapper.toFullEventDto(updatedEvent);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с ID=" + eventId + " не найдено."));

        if (updateRequest.getStateAction() != null) {
            if (updateRequest.getStateAction() == StateActionAdmin.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Нельзя опубликовать событие, так как оно не в состоянии ожидания. Текущий статус: " + event.getState());
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (updateRequest.getStateAction() == StateActionAdmin.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Нельзя отклонить уже опубликованное событие.");
                }
                event.setState(EventState.CANCELED);
            }
        }

        updateEventFromAdminRequest(event, updateRequest);

        return EventMapper.toFullEventDto(eventRepository.save(event));
    }

    @Override
    public List<EventShortDto> getEventsByUser(String text, List<Long> categories, Boolean paid, LocalDateTime rangeStart, LocalDateTime rangeEnd,
            Boolean onlyAvailable, SortValue sort, Integer from, Integer size, HttpServletRequest request
    ) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата начала не может быть позже даты окончания.");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> eventRoot = query.from(Event.class);

        List<Predicate> predicates = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            predicates.add(cb.or(
                    cb.like(cb.lower(eventRoot.get("annotation")), "%" + text.toLowerCase() + "%"),
                    cb.like(cb.lower(eventRoot.get("description")), "%" + text.toLowerCase() + "%")
            ));
        }

        if (categories != null && !categories.isEmpty()) {
            predicates.add(eventRoot.get("category").get("id").in(categories));
        }

        if (paid != null) {
            predicates.add(cb.equal(eventRoot.get("paid"), paid));
        }

        LocalDateTime startDateTime = (rangeStart != null) ? rangeStart : LocalDateTime.now();
        predicates.add(cb.greaterThan(eventRoot.get("eventDate"), startDateTime));
        if (rangeEnd != null) {
            predicates.add(cb.lessThan(eventRoot.get("eventDate"), rangeEnd));
        }

        predicates.add(cb.equal(eventRoot.get("state"), EventState.PUBLISHED));

        query.where(predicates.toArray(new Predicate[0]));

        if (sort == SortValue.EVENT_DATE) query.orderBy(cb.desc(eventRoot.get("eventDate")));

        List<Event> events = entityManager.createQuery(query)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();

        List<EventShortDto> shortDtos = EventMapper.toEventShortDtoList(events);

        if (onlyAvailable != null && onlyAvailable) {
            return shortDtos.stream()
                    .filter(dto -> dto.getParticipantLimit() == 0 || dto.getConfirmedRequests() < dto.getParticipantLimit())
                    .collect(Collectors.toList());
        }

        return shortDtos;
    }

    @Override
    @Transactional(readOnly = true)
    public EventFullDto getEvent(Long eventId, Long userId, HttpServletRequest request) {
        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Опубликованное событие с ID=" + eventId + " не найдено."));

        collectorGrpcClient.collectUserActions(userId, eventId, ActionType.ACTION_VIEW);

        long confirmedRequests = participationRequestClient.countEventsInStatus(eventId, RequestStatus.CONFIRMED);

        return EventMapper.toFullEventDto(event, confirmedRequests);
    }

    @Override
    public List<EventFullDto> getEventsByAdmin(
            List<Long> users, List<EventState> states, List<Long> categories,
            LocalDateTime rangeStart, LocalDateTime rangeEnd, Integer from, Integer size
    ) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Дата начала не может быть позже даты окончания.");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> eventRoot = query.from(Event.class);

        List<Predicate> predicates = new ArrayList<>();

        if (users != null && !users.isEmpty()) predicates.add(eventRoot.get("initiator").in(users));
        if (states != null && !states.isEmpty()) predicates.add(eventRoot.get("state").in(states));

        if (categories != null && !categories.isEmpty()) {
            predicates.add(eventRoot.get("category").get("id").in(categories));
        }

        if (rangeStart != null) predicates.add(cb.greaterThanOrEqualTo(eventRoot.get("eventDate"), rangeStart));
        if (rangeEnd != null) predicates.add(cb.lessThanOrEqualTo(eventRoot.get("eventDate"), rangeEnd));

        query.where(predicates.toArray(new Predicate[0]));

        List<Event> events = entityManager.createQuery(query)
                .setFirstResult(from)
                .setMaxResults(size)
                .getResultList();

        return events.stream()
                .map(EventMapper::toFullEventDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<EventShortDto> getRecommendations(Long userId) {
        List<RecommendedEventProto> recommendedEvents = analyzerGrpcClient
                .getRecommendationsForUser(userId, 10)
                .toList();

        Map<Long, Double> scoreByEvent = recommendedEvents.stream()
                .collect(Collectors.toMap(RecommendedEventProto::getEventId, RecommendedEventProto::getScore));

        Set<Long> eventIds = recommendedEvents.stream()
                .map(RecommendedEventProto::getEventId)
                .collect(Collectors.toSet());

        Set<Event> events = eventRepository.findAllByIdIn(eventIds);
        Set<EventShortDto> shortEvents = EventMapper.toEventShortDtoSet(events);

        shortEvents.forEach(eventShortDto -> eventShortDto.setRating(scoreByEvent.get(eventShortDto.getId())));

        return shortEvents.stream().toList();
    }

    @Override
    public void like(Long userId, Long eventId) {
        if (!participationRequestClient.isUserParticipant(userId, eventId)) {
            throw new ValidationException(String.format(
                    "Пользователь с ID=%d не является участником события с ID=%d",
                    userId, eventId)
            );
        }
        collectorGrpcClient.collectUserActions(userId, eventId, ActionType.ACTION_LIKE);
    }

    private void validateEventDate(LocalDateTime eventDate, int hours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(hours))) {
            throw new ValidationException("Дата события должна быть как минимум через " + hours + " часа от текущего момента.");
        }
    }

    private Location getLocation(LocationDto locationDto) {
        return locationRepository.findByLatAndLon(locationDto.getLat(), locationDto.getLon())
                .orElseGet(() -> locationRepository.save(LocationMapper.toLocation(locationDto)));
    }

    private Event findEventByIdAndInitiatorId(Long eventId, Long userId) {
        return eventRepository.findByIdAndInitiator(eventId, userId)
                .orElseThrow(() -> new NotFoundException(
                        String.format("Событие с ID=%d и инициатором ID=%d не найдено.", eventId, userId)
                ));
    }

    private Optional<UserDto> findUserById(Long userId) {
        List<UserDto> userDtos = userClient.getUsers(List.of(userId));
        return userDtos.isEmpty() ? Optional.empty() : Optional.of(userDtos.getFirst());
    }

    private Category findCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Категория с ID=" + categoryId + " не найдена."));
    }

}