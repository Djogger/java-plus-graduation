package ru.practicum.mapper;

import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.model.ParticipationRequest;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ParticipationRequestMapper {
    private ParticipationRequestMapper() {

    }

    public static ParticipationRequestDto toParticipationRequestDto(ParticipationRequest request) {
        if (Objects.isNull(request)) return null;

        return new ParticipationRequestDto(
                request.getId(),
                request.getEvent(),
                request.getRequester(),
                request.getStatus(),
                request.getCreated()
        );
    }

    public static List<ParticipationRequestDto> toParticipationRequestDtoList(List<ParticipationRequest> requests) {
        return requests.stream()
                .map(ParticipationRequestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }
}
