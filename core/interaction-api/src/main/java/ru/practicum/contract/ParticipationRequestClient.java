package ru.practicum.contract;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.request.EventRequestStatusUpdateRequest;
import ru.practicum.dto.request.EventRequestStatusUpdateResult;
import ru.practicum.dto.request.ParticipationRequestDto;
import ru.practicum.enums.RequestStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;

@FeignClient(name = "request-service", path = "/api/v1/requests")
public interface ParticipationRequestClient {
    @GetMapping("/{eventId}/count")
    long countEventsInStatus(@PathVariable Long eventId, @RequestParam RequestStatus status);

    @GetMapping("/confirmed/count")
    Map<Long, Long> countConfirmedRequestsForEvents(@RequestParam Set<Long> eventIds);

    @GetMapping("/owner/{ownerId}/event/{eventId}")
    List<ParticipationRequestDto> getRequestsForEventByOwner(@PathVariable Long ownerId, @PathVariable Long eventId);

    @PatchMapping("/user/{userId}/event/{eventId}/status")
    EventRequestStatusUpdateResult updateRequestStatus(
            @PathVariable Long userId,
            @PathVariable Long eventId,
            @RequestBody EventRequestStatusUpdateRequest requestStatusUpdateDto
    );

    @PostMapping("{eventId}/participant/{userId}")
    boolean isUserParticipant(@PathVariable Long userId, @PathVariable Long eventId);

}
