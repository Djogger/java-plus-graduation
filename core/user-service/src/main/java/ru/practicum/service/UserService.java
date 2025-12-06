package ru.practicum.service;

import ru.practicum.dto.user.NewUserRequest;
import ru.practicum.dto.user.UserDto;
import ru.practicum.model.User;

import java.util.List;

public interface UserService {
    UserDto createUser(NewUserRequest requestDto);

    List<UserDto> getUsers(List<Long> ids, Integer from, Integer size);

    List<UserDto> getUsers(List<Long> ids);

    UserDto getUser(Long userId);

    User getUserEntity(Long userId);

    void deleteUser(Long userId);
}
