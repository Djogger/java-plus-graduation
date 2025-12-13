package ru.practicum;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.practicum.service.SimilarityProcessor;
import ru.practicum.service.UserActionProcessor;

@Component
@RequiredArgsConstructor
public class AnalyzerRunner implements CommandLineRunner {
    private final UserActionProcessor userActionProcessor;
    private final SimilarityProcessor similarityProcessor;

    @Override
    public void run(String[] args) {
        Thread userActionThread = new Thread(userActionProcessor);

        userActionThread.setName("UserActionHandlerThread");
        userActionThread.start();

        similarityProcessor.start();
    }
}
