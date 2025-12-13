package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.model.UserAction;
import ru.practicum.repository.UserActionRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserActionService {
    private final UserActionRepository userActionRepository;

    public void save(UserAction userAction) {
        Optional<UserAction> action =
                userActionRepository.findByUserIdAndEventId(userAction.getUserId(), userAction.getEventId());

        if (action.isEmpty()) {
            userActionRepository.save(userAction);
            return;
        }

        UserAction oldUserAction = action.get();

        if (userAction.getWeight() > oldUserAction.getWeight()) {
            oldUserAction.setWeight(userAction.getWeight());
            userActionRepository.save(oldUserAction);
        }
    }

}
