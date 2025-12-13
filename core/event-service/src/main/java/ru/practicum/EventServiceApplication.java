package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import ru.practicum.contract.ParticipationRequestClient;
import ru.practicum.contract.UserClient;

@SpringBootApplication
@EnableFeignClients(clients = {
        StatsClient.class,
        UserClient.class,
        ParticipationRequestClient.class
})
public class EventServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
