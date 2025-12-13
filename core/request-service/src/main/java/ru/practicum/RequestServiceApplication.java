package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import ru.practicum.contract.EventClient;
import ru.practicum.contract.UserClient;

@SpringBootApplication
@EnableFeignClients(clients = {
        UserClient.class,
        EventClient.class
})
public class RequestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RequestServiceApplication.class, args);
    }
}
