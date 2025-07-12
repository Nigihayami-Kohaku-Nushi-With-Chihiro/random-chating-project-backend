package random.chating.org.randomchatingproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import random.chating.org.randomchatingproject.dto.MatchingResult;
import random.chating.org.randomchatingproject.entity.ChatRoom;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.repository.ChatRoomRepository;
import random.chating.org.randomchatingproject.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProfileService profileService;

    // 성별별 대기 큐
    private final Queue<Long> maleQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Long> femaleQueue = new ConcurrentLinkedQueue<>();

    // 사용자별 대기 상태 추적
    private final Map<Long, LocalDateTime> waitingUsers = new ConcurrentHashMap<>();

    // 현재 활성 채팅방에 있는 사용자들
    private final Map<Long, String> activeChats = new ConcurrentHashMap<>();

    // 동기화를 위한 락 객체
    private final Object matchingLock = new Object();

    /**
     * 랜덤 매칭 요청 처리 - 동기화 추가
     */
    public MatchingResult requestMatching(Long userId) {
        log.info("매칭 요청: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 동기화 블록으로 race condition 방지
        synchronized (matchingLock) {
            // 이미 채팅 중인지 확인
            if (activeChats.containsKey(userId)) {
                String existingRoomId = activeChats.get(userId);
                log.info("이미 채팅 중인 사용자: userId={}, roomId={}", userId, existingRoomId);
                return MatchingResult.builder()
                        .success(true)
                        .matched(true)
                        .roomId(existingRoomId)
                        .message("이미 채팅 중입니다")
                        .build();
            }

            // 이미 대기 중인지 확인
            if (waitingUsers.containsKey(userId)) {
                log.info("이미 대기 중인 사용자: userId={}", userId);
                return MatchingResult.builder()
                        .success(true)
                        .matched(false)
                        .message("이미 매칭 대기 중입니다")
                        .waitingCount(getWaitingCount(user.getGender()))
                        .build();
            }

            // 대기열 정리 (매칭 전에 정리)
            cleanupQueues();

            MatchingResult result = attemptMatching(user);

            // 실시간 통계 브로드캐스트
            broadcastStatsUpdate();

            if (result.isMatched()) {
                try {
                    profileService.incrementTotalChats(userId);
                } catch (Exception e) {
                    log.warn("채팅 수 증가 실패: userId={}, error={}", userId, e.getMessage());
                }
            }

            return result;
        }
    }

    /**
     * 매칭 시도 로직 - 개선된 버전
     */
    private MatchingResult attemptMatching(User user) {
        User.Gender userGender = user.getGender();
        User.Gender targetGender = userGender == User.Gender.MALE ? User.Gender.FEMALE : User.Gender.MALE;

        Queue<Long> targetQueue = targetGender == User.Gender.MALE ? maleQueue : femaleQueue;
        Queue<Long> userQueue = userGender == User.Gender.MALE ? maleQueue : femaleQueue;

        log.info("매칭 시도: userId={}, gender={}, 상대방 대기열 크기={}",
                user.getId(), userGender, targetQueue.size());

        // 상대방 찾기
        Long partnerId = findAndRemoveAvailablePartner(targetQueue);

        if (partnerId != null) {
            // 매칭 성공
            log.info("매칭 성공: user1={}, user2={}", user.getId(), partnerId);

            String roomId = createChatRoom(user.getId(), partnerId);

            // 대기 상태에서 제거
            waitingUsers.remove(partnerId);
            waitingUsers.remove(user.getId()); // 혹시라도 있다면 제거

            // 활성 채팅에 추가
            activeChats.put(user.getId(), roomId);
            activeChats.put(partnerId, roomId);

            // 상대방 정보 가져오기
            User partner = userRepository.findById(partnerId).orElse(null);

            // 양쪽 모두에게 매칭 알림
            notifyMatchFound(partnerId, roomId, user);
            notifyMatchFound(user.getId(), roomId, partner);

            return MatchingResult.builder()
                    .success(true)
                    .matched(true)
                    .roomId(roomId)
                    .partnerId(partnerId)
                    .message("매칭되었습니다!")
                    .build();
        } else {
            // 대기열에 추가
            log.info("매칭 상대 없음, 대기열 추가: userId={}", user.getId());
            userQueue.offer(user.getId());
            waitingUsers.put(user.getId(), LocalDateTime.now());

            return MatchingResult.builder()
                    .success(true)
                    .matched(false)
                    .message("매칭을 기다리고 있습니다...")
                    .waitingCount(getWaitingCount(userGender))
                    .build();
        }
    }

    /**
     * 사용 가능한 매칭 상대 찾기 및 제거 - 개선된 버전
     */
    private Long findAndRemoveAvailablePartner(Queue<Long> targetQueue) {
        while (!targetQueue.isEmpty()) {
            Long partnerId = targetQueue.poll(); // 큐에서 제거

            // null 체크
            if (partnerId == null) {
                continue;
            }

            // 대기 상태 확인
            if (!waitingUsers.containsKey(partnerId)) {
                log.debug("대기 상태가 아닌 사용자 제거: partnerId={}", partnerId);
                continue;
            }

            // 이미 채팅 중인지 확인
            if (activeChats.containsKey(partnerId)) {
                log.debug("이미 채팅 중인 사용자 제거: partnerId={}", partnerId);
                waitingUsers.remove(partnerId);
                continue;
            }

            // 사용자 존재 및 활성 상태 확인
            User partner = userRepository.findById(partnerId).orElse(null);
            if (partner == null || !partner.isEnabled()) {
                log.debug("비활성 사용자 제거: partnerId={}", partnerId);
                waitingUsers.remove(partnerId);
                continue;
            }

            // 유효한 상대방 발견
            log.info("유효한 매칭 상대 발견: partnerId={}", partnerId);
            return partnerId;
        }

        return null;
    }

    /**
     * 대기열 정리 - 주기적으로 호출
     */
    private void cleanupQueues() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10); // 10분 이상 된 대기자 제거

        // 만료된 대기자들 찾기
        waitingUsers.entrySet().removeIf(entry -> {
            Long userId = entry.getKey();
            LocalDateTime waitTime = entry.getValue();

            if (waitTime.isBefore(cutoff)) {
                log.info("만료된 대기자 제거: userId={}", userId);
                maleQueue.remove(userId);
                femaleQueue.remove(userId);
                return true;
            }

            // 이미 채팅 중인 사용자도 대기열에서 제거
            if (activeChats.containsKey(userId)) {
                log.info("채팅 중인 사용자를 대기열에서 제거: userId={}", userId);
                maleQueue.remove(userId);
                femaleQueue.remove(userId);
                return true;
            }

            return false;
        });
    }

    /**
     * 채팅방 생성
     */
    @Transactional
    private String createChatRoom(Long user1Id, Long user2Id) {
        String roomId = UUID.randomUUID().toString();

        ChatRoom chatRoom = ChatRoom.builder()
                .roomId(roomId)
                .user1Id(user1Id)
                .user2Id(user2Id)
                .status(ChatRoom.RoomStatus.ACTIVE)
                .build();

        chatRoomRepository.save(chatRoom);
        log.info("채팅방 생성 완료: roomId={}, user1={}, user2={}", roomId, user1Id, user2Id);

        return roomId;
    }

    /**
     * 매칭 상대에게 알림 - 개선된 버전
     */
    private void notifyMatchFound(Long userId, String roomId, User partner) {
        try {
            if (partner == null) {
                log.warn("상대방 정보가 없어 알림 전송 건너뜀: userId={}", userId);
                return;
            }

            Map<String, Object> notification = Map.of(
                    "type", "MATCH_FOUND",
                    "roomId", roomId,
                    "partner", Map.of(
                            "username", partner.getUsername(),
                            "age", partner.getAge(),
                            "gender", partner.getGender().name()
                    ),
                    "message", "매칭되었습니다! 채팅을 시작하세요."
            );

            log.info("매칭 알림 전송: userId={}, roomId={}, partner={}",
                    userId, roomId, partner.getUsername());

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/matching",
                    notification
            );

            // 약간의 지연 후 재전송 (WebSocket 연결 이슈 대비)
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/matching",
                            notification
                    );
                    log.info("매칭 알림 재전송 완료: userId={}", userId);
                } catch (Exception e) {
                    log.warn("매칭 알림 재전송 실패: userId={}, error={}", userId, e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            log.error("매칭 알림 전송 실패: userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 매칭 취소
     */
    public boolean cancelMatching(Long userId) {
        synchronized (matchingLock) {
            boolean removed = false;

            removed |= maleQueue.remove(userId);
            removed |= femaleQueue.remove(userId);
            removed |= (waitingUsers.remove(userId) != null);

            if (removed) {
                log.info("매칭 취소 완료: userId={}", userId);
                // 실시간 통계 브로드캐스트
                broadcastStatsUpdate();
            }

            return removed;
        }
    }

    /**
     * 채팅 종료
     */
    public boolean endChat(Long userId, String roomId) {
        log.info("채팅 종료 요청: userId={}, roomId={}", userId, roomId);

        synchronized (matchingLock) {
            String userRoomId = activeChats.remove(userId);

            if (userRoomId == null || !userRoomId.equals(roomId)) {
                log.warn("채팅 종료 실패 - 방 정보 불일치: userId={}, roomId={}", userId, roomId);
                return false;
            }

            try {
                ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElse(null);
                if (chatRoom != null) {
                    Long partnerId = chatRoom.getUser1Id().equals(userId) ?
                            chatRoom.getUser2Id() : chatRoom.getUser1Id();

                    activeChats.remove(partnerId);

                    // 상대방에게 채팅 종료 알림
                    try {
                        Map<String, Object> notification = Map.of(
                                "type", "CHAT_ENDED",
                                "roomId", roomId,
                                "message", "상대방이 채팅을 종료했습니다."
                        );

                        messagingTemplate.convertAndSendToUser(
                                partnerId.toString(),
                                "/queue/chat",
                                notification
                        );

                    } catch (Exception e) {
                        log.error("채팅 종료 알림 전송 실패: partnerId={}, error={}", partnerId, e.getMessage());
                    }

                    chatRoom.setStatus(ChatRoom.RoomStatus.ENDED);
                    chatRoomRepository.save(chatRoom);
                }

                // 실시간 통계 브로드캐스트
                broadcastStatsUpdate();

                log.info("채팅 종료 완료: userId={}, roomId={}", userId, roomId);
                return true;

            } catch (Exception e) {
                log.error("채팅 종료 처리 중 오류: userId={}, roomId={}, error={}", userId, roomId, e.getMessage());
                return false;
            }
        }
    }

    /**
     * 실시간 통계 브로드캐스트
     */
    private void broadcastStatsUpdate() {
        try {
            Map<String, Object> stats = getMatchingStats();
            messagingTemplate.convertAndSend("/topic/matching/stats", stats);
            log.debug("통계 브로드캐스트 완료: {}", stats);
        } catch (Exception e) {
            log.error("통계 브로드캐스트 실패: {}", e.getMessage());
        }
    }

    /**
     * 현재 대기 인원수 조회
     */
    public int getWaitingCount(User.Gender gender) {
        cleanupQueues(); // 조회 전 정리
        Queue<Long> queue = gender == User.Gender.MALE ? maleQueue : femaleQueue;
        return queue.size();
    }

    /**
     * 전체 대기 통계 조회
     */
    public Map<String, Object> getMatchingStats() {
        cleanupQueues(); // 통계 조회 전 정리

        return Map.of(
                "maleWaiting", maleQueue.size(),
                "femaleWaiting", femaleQueue.size(),
                "totalWaiting", waitingUsers.size(),
                "activeChats", activeChats.size() / 2
        );
    }

    /**
     * 사용자 상태 조회
     */
    public Map<String, Object> getUserStatus(Long userId) {
        boolean isWaiting = waitingUsers.containsKey(userId);
        String roomId = activeChats.get(userId);
        boolean isInChat = roomId != null;

        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("isWaiting", isWaiting);
        status.put("isInChat", isInChat);

        if (isWaiting) {
            status.put("waitingSince", waitingUsers.get(userId));
        }

        if (isInChat) {
            status.put("roomId", roomId);
        }

        return status;
    }

    /**
     * 서버 시작 시 초기화
     */
    public void initialize() {
        synchronized (matchingLock) {
            maleQueue.clear();
            femaleQueue.clear();
            waitingUsers.clear();
            activeChats.clear();
            log.info("매칭 서비스 초기화 완료");
        }
    }

    /**
     * 주기적 정리 작업 (스케줄러에서 호출)
     */
    public void periodicCleanup() {
        synchronized (matchingLock) {
            log.info("주기적 정리 작업 시작");
            cleanupQueues();
            broadcastStatsUpdate();
            log.info("주기적 정리 작업 완료");
        }
    }
}