package ru.practicum.mapper;

import ru.practicum.dto.user.NewUserRequest;
import ru.practicum.dto.user.UserDto;
import ru.practicum.dto.user.UserShortDto;
import ru.practicum.model.User;

import java.util.Objects;

public final class UserMapper {
    private UserMapper() {

    }

    public static User toUser(NewUserRequest requestDto) {
        if (Objects.isNull(requestDto)) return null;

        return User.builder()
                .name(requestDto.getName())
                .email(requestDto.getEmail())
                .build();
    }

    public static UserDto toUserDto(User user) {
        if (Objects.isNull(user)) return null;
        return new UserDto(user.getId(), user.getEmail(), user.getName());
    }

    public static UserShortDto toShortDto(User user) {
        if (Objects.isNull(user)) return null;
        return new UserShortDto(user.getId(), user.getName());
    }
}
