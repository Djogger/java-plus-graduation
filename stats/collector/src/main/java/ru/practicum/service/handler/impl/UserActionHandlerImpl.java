package ru.practicum.service.handler.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.proto.UserActionProto;
import ru.practicum.mapper.AvroMapper;
import ru.practicum.service.UserActionProducer;
import ru.practicum.service.handler.UserActionHandler;

@Service
@RequiredArgsConstructor
public class UserActionHandlerImpl implements UserActionHandler {
    private final UserActionProducer producer;

    @Override
    public void handle(UserActionProto userActionProto) {
        producer.sendUserAction(AvroMapper.toUserActionAvro(userActionProto));
    }

}
