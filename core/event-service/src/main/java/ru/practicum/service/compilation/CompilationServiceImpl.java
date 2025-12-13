package ru.practicum.service.compilation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.contract.ParticipationRequestClient;
import ru.practicum.dto.compilation.CompilationDto;
import ru.practicum.dto.compilation.NewCompilationDto;
import ru.practicum.dto.compilation.UpdateCompilationRequest;
import ru.practicum.exception.NotFoundException;
import ru.practicum.mapper.CompilationMapper;
import ru.practicum.model.Compilation;
import ru.practicum.model.Event;
import ru.practicum.repository.CompilationRepository;
import ru.practicum.repository.EventRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {
    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final ParticipationRequestClient participationRequestClient;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newDto) {
        Set<Long> eventIds = newDto.getEvents();
        Set<Event> events = new HashSet<>();

        if (eventIds != null && !eventIds.isEmpty()) {
            events.addAll(eventRepository.findAllById(eventIds));

            if (events.size() != eventIds.size()) {
                throw new NotFoundException("Одно или несколько событий из списка не найдены.");
            }
        }

        Compilation compilation = new Compilation(null, events, newDto.getPinned(), newDto.getTitle());
        Compilation saved = compilationRepository.save(compilation);

        Set<Long> savedEventIds = saved.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedRequestsCounts = getConfirmedRequestsCounts(savedEventIds);

        return CompilationMapper.toCompilationDto(saved, confirmedRequestsCounts);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Подборка с ID=" + compId + " не найдена.");
        }

        compilationRepository.deleteById(compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        Compilation compilation = findCompilationById(compId);

        if (updateRequest.getEvents() != null) {
            Set<Event> events = eventRepository.findAllByIdIn(updateRequest.getEvents());
            compilation.setEvents(events);
        }

        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }

        if (updateRequest.getTitle() != null && !updateRequest.getTitle().isBlank()) {
            compilation.setTitle(updateRequest.getTitle());
        }

        Compilation updatedCompilation = compilationRepository.save(compilation);

        Set<Long> updatedEventIds = updatedCompilation.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedRequestsCounts = getConfirmedRequestsCounts(updatedEventIds);

        return CompilationMapper.toCompilationDto(updatedCompilation, confirmedRequestsCounts);
    }

    @Override
    public List<CompilationDto> getAllCompilations(Boolean pinned, int from, int size) {
        PageRequest page = PageRequest.of(from / size, size);
        List<Compilation> compilations;

        if (pinned != null) {
            compilations = compilationRepository.findAllByPinned(pinned, page).getContent();
        } else {
            compilations = compilationRepository.findAll(page).getContent();
        }

        Set<Long> allEventIds = compilations.stream()
                .flatMap(compilation -> compilation.getEvents().stream())
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> allConfirmedRequestsCounts = getConfirmedRequestsCounts(allEventIds);

        return compilations.stream()
                .map(compilation -> CompilationMapper.toCompilationDto(compilation, allConfirmedRequestsCounts))
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        Compilation compilation = findCompilationById(compId);

        Set<Long> eventIds = compilation.getEvents().stream()
                .map(Event::getId)
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedRequestsCounts = getConfirmedRequestsCounts(eventIds);

        return CompilationMapper.toCompilationDto(compilation, confirmedRequestsCounts);
    }

    private Map<Long, Long> getConfirmedRequestsCounts(Set<Long> eventIds) {
        if (Objects.isNull(eventIds) || eventIds.isEmpty()) return Collections.emptyMap();
        return participationRequestClient.countConfirmedRequestsForEvents(eventIds);
    }

    private Compilation findCompilationById(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с ID=" + compId + " не найдена."));
    }

}
