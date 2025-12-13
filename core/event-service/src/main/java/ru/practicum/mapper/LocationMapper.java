package ru.practicum.mapper;

import ru.practicum.dto.location.LocationDto;
import ru.practicum.model.Location;

public class LocationMapper {
    private LocationMapper() {

    }

    public static Location toLocation(LocationDto locationDto) {
        return Location.builder()
                .id(locationDto.getId())
                .lat(locationDto.getLat())
                .lon(locationDto.getLon())
                .build();
    }

    public static LocationDto toLocationDto(Location location) {
        return LocationDto.builder()
                .lat(location.getLat())
                .lon(location.getLon())
                .build();
    }
}
