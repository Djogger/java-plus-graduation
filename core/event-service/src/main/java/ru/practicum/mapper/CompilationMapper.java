package ru.practicum.mapper;

import ru.practicum.dto.compilation.CompilationDto;
import ru.practicum.dto.event.EventShortDto;
import ru.practicum.model.Compilation;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CompilationMapper {
    private CompilationMapper() {

    }

    public static CompilationDto toCompilationDto(Compilation compilation, Map<Long, Long> confirmedRequestsCounts) {
        if (Objects.isNull(compilation)) return null;

        Set<EventShortDto> eventShortDtos = Collections.emptySet();

        if (Objects.nonNull(compilation.getEvents()) && !compilation.getEvents().isEmpty()) {
            eventShortDtos = compilation.getEvents().stream()
                    .map(EventMapper::toEventShortDto)
                    .collect(Collectors.toSet());
        }

        return new CompilationDto(
                compilation.getId(),
                compilation.getPinned(),
                compilation.getTitle(),
                eventShortDtos
        );
    }
}
