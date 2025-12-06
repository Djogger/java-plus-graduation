package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.event.EventFullDto;
import ru.practicum.service.event.EventService;

import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
public class InternalEventController {
    private final EventService eventService;

    @GetMapping("/{id}")
    public Optional<EventFullDto> getEvent(@PathVariable Long id) {
        return eventService.getEvent(id);
    }

    @PatchMapping("/{eventId}/confirmed-requests")
    public void updateConfirmedRequests(
            @PathVariable Long eventId,
            @RequestParam Long confirmedRequests
    ) {
        eventService.updateConfirmedRequests(eventId, confirmedRequests);
    }
}
