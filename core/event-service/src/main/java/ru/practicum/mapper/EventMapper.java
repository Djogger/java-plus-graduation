package ru.practicum.mapper;

import ru.practicum.dto.event.EventFullDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.dto.event.NewEventDto;
import ru.practicum.dto.user.UserDto;
import ru.practicum.enums.EventState;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.Location;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class EventMapper {
    private EventMapper() {

    }

    public static Event toEvent(NewEventDto dto, Category category, UserDto user, Location location) {
        if (Objects.isNull(dto)) return null;

        return Event.builder()
                .annotation(dto.getAnnotation())
                .category(category)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .eventDate(dto.getEventDate())
                .location(location)
                .paid(dto.getPaid())
                .participantLimit(dto.getParticipantLimit() != null ? dto.getParticipantLimit() : 0)
                .requestModeration(dto.getRequestModeration() != null ? dto.getRequestModeration() : true)
                .initiator(user.getId())
                .state(EventState.PENDING)
                .createdOn(LocalDateTime.now())
                .publishedOn(null)
                .views(0L)
                .confirmedRequests(0L)
                .build();
    }

    public static EventShortDto toEventShortDto(Event event) {
        if (Objects.isNull(event)) return null;

        return new EventShortDto(
                event.getId(),
                event.getAnnotation(),
                CategoryMapper.toCategoryDto(event.getCategory()),
                event.getEventDate(),
                event.getInitiator(),
                event.getPaid(),
                event.getTitle(),
                event.getViews(),
                event.getConfirmedRequests(),
                event.getParticipantLimit()
        );
    }

    public static Set<EventShortDto> toEventShortDtoSet(Set<Event> events) {
        if (Objects.isNull(events) || events.isEmpty()) return Collections.emptySet();

        return events.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toSet());
    }

    public static List<EventShortDto> toEventShortDtoList(List<Event> events) {
        if (Objects.isNull(events) || events.isEmpty()) return Collections.emptyList();

        return events.stream()
                .map(EventMapper::toEventShortDto)
                .collect(Collectors.toList());
    }

    public static EventFullDto toFullEventDto(Event event, long confirmedRequestsCount) {
        if (Objects.isNull(event)) return null;

        return new EventFullDto(
                event.getId(),
                event.getAnnotation(),
                CategoryMapper.toCategoryDto(event.getCategory()),
                confirmedRequestsCount,
                event.getCreatedOn(),
                event.getDescription(),
                event.getEventDate(),
                event.getInitiator(),
                LocationMapper.toLocationDto(event.getLocation()),
                event.getPaid(),
                event.getParticipantLimit(),
                event.getPublishedOn(),
                event.getRequestModeration(),
                event.getState(),
                event.getTitle(),
                event.getViews()
        );
    }

    public static EventFullDto toFullEventDto(Event event) {
        if (Objects.isNull(event)) return null;
        return toFullEventDto(event, Objects.nonNull(event.getConfirmedRequests())
                ? event.getConfirmedRequests()
                : 0L
        );
    }

    public static List<EventFullDto> toEventFullDtoList(List<Event> events) {
        if (Objects.isNull(events) || events.isEmpty()) return Collections.emptyList();

        return events.stream()
                .map(EventMapper::toFullEventDto)
                .collect(Collectors.toList());
    }
}
