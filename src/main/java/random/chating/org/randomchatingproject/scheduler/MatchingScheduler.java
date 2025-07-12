package random.chating.org.randomchatingproject.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import random.chating.org.randomchatingproject.service.MatchingService;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScheduler {

    private final MatchingService matchingService;

    /**
     * 30초마다 매칭 대기열 정리
     */
    @Scheduled(fixedRate = 30000)
    public void cleanupMatching() {
        try {
            log.debug("매칭 정리 스케줄러 실행");
            matchingService.periodicCleanup();
        } catch (Exception e) {
            log.error("매칭 정리 스케줄러 오류: {}", e.getMessage(), e);
        }
    }

    /**
     * 5분마다 통계 브로드캐스트
     */
    @Scheduled(fixedRate = 300000)
    public void broadcastStats() {
        try {
            log.debug("통계 브로드캐스트 스케줄러 실행");
            // MatchingService에 통계 브로드캐스트 메소드가 있다면 호출
        } catch (Exception e) {
            log.error("통계 브로드캐스트 스케줄러 오류: {}", e.getMessage(), e);
        }
    }
}