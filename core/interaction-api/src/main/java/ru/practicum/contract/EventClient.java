package ru.practicum.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.dto.event.EventFullDto;

import java.util.Optional;

@FeignClient(name = "event-service", path = "/api/v1/events")
public interface EventClient {
    @GetMapping("/{id}")
    Optional<EventFullDto> getEvent(@PathVariable Long id);

    @PatchMapping("/{eventId}/confirmed-requests")
    void updateConfirmedRequestsForEvent(@PathVariable Long eventId, @RequestParam Long confirmedRequests);
}
