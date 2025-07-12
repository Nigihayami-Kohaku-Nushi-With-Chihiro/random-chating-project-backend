package random.chating.org.randomchatingproject.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import random.chating.org.randomchatingproject.service.MatchingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppInitializer implements CommandLineRunner {

    private final MatchingService matchingService;

    @Override
    public void run(String... args) throws Exception {
        matchingService.initialize();
    }
}