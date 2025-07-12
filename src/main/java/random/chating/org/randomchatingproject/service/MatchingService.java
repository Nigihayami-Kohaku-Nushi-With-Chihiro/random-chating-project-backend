package random.chating.org.randomchatingproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
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

    /**
     * 랜덤 매칭 요청 처리
     */
    public MatchingResult requestMatching(Long userId) {
        log.info("매칭 요청: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 이미 채팅 중인지 확인
        if (activeChats.containsKey(userId)) {
            String existingRoomId = activeChats.get(userId);
            return MatchingResult.builder()
                    .success(true)
                    .matched(true)
                    .roomId(existingRoomId)
                    .message("이미 채팅 중입니다")
                    .build();
        }

        // 이미 대기 중인지 확인
        if (waitingUsers.containsKey(userId)) {
            return MatchingResult.builder()
                    .success(true)
                    .matched(false)
                    .message("이미 매칭 대기 중입니다")
                    .waitingCount(getWaitingCount(user.getGender()))
                    .build();
        }

        MatchingResult result = attemptMatching(user);

        // 실시간 통계 브로드캐스트
        broadcastStatsUpdate();

        if (result.isMatched()) {
            profileService.incrementTotalChats(userId);
        }

        return result;
    }

    /**
     * 매칭 시도 로직
     */
    private MatchingResult attemptMatching(User user) {
        User.Gender userGender = user.getGender();
        User.Gender targetGender = userGender == User.Gender.MALE ? User.Gender.FEMALE : User.Gender.MALE;

        Queue<Long> targetQueue = targetGender == User.Gender.MALE ? maleQueue : femaleQueue;
        Queue<Long> userQueue = userGender == User.Gender.MALE ? maleQueue : femaleQueue;

        Long partnerId = findAvailablePartner(targetQueue);

        if (partnerId != null) {
            // 매칭 성공
            String roomId = createChatRoom(user.getId(), partnerId);

            waitingUsers.remove(partnerId);
            activeChats.put(user.getId(), roomId);
            activeChats.put(partnerId, roomId);

            // 상대방에게 매칭 알림
            notifyMatchFound(partnerId, roomId, user);

            return MatchingResult.builder()
                    .success(true)
                    .matched(true)
                    .roomId(roomId)
                    .partnerId(partnerId)
                    .message("매칭되었습니다!")
                    .build();
        } else {
            // 대기열에 추가
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
     * 사용 가능한 매칭 상대 찾기
     */
    private Long findAvailablePartner(Queue<Long> targetQueue) {
        while (!targetQueue.isEmpty()) {
            Long partnerId = targetQueue.poll();

            if (waitingUsers.containsKey(partnerId)) {
                User partner = userRepository.findById(partnerId).orElse(null);
                if (partner != null && partner.isEnabled()) {
                    return partnerId;
                } else {
                    waitingUsers.remove(partnerId);
                }
            }
        }
        return null;
    }

    /**
     * 채팅방 생성
     */
    private String createChatRoom(Long user1Id, Long user2Id) {
        String roomId = UUID.randomUUID().toString();

        ChatRoom chatRoom = ChatRoom.builder()
                .roomId(roomId)
                .user1Id(user1Id)
                .user2Id(user2Id)
                .status(ChatRoom.RoomStatus.ACTIVE)
                .build();

        chatRoomRepository.save(chatRoom);
        log.info("채팅방 생성: roomId={}, user1={}, user2={}", roomId, user1Id, user2Id);

        return roomId;
    }

    /**
     * 매칭 상대에게 알림
     */
    private void notifyMatchFound(Long userId, String roomId, User partner) {
        try {
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

            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/matching",
                    notification
            );

        } catch (Exception e) {
            log.error("매칭 알림 전송 실패: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 매칭 취소
     */
    public boolean cancelMatching(Long userId) {
        boolean removed = false;

        removed |= maleQueue.remove(userId);
        removed |= femaleQueue.remove(userId);
        removed |= (waitingUsers.remove(userId) != null);

        if (removed) {
            // 실시간 통계 브로드캐스트
            broadcastStatsUpdate();
            log.info("매칭 취소 완료: userId={}", userId);
        }

        return removed;
    }

    /**
     * 채팅 종료
     */
    public boolean endChat(Long userId, String roomId) {
        log.info("채팅 종료 요청: userId={}, roomId={}", userId, roomId);

        String userRoomId = activeChats.remove(userId);

        if (userRoomId == null || !userRoomId.equals(roomId)) {
            return false;
        }

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

        return true;
    }

    /**
     * 실시간 통계 브로드캐스트
     */
    private void broadcastStatsUpdate() {
        try {
            Map<String, Object> stats = getMatchingStats();
            messagingTemplate.convertAndSend("/topic/matching/stats", stats);
        } catch (Exception e) {
            log.error("통계 브로드캐스트 실패: {}", e.getMessage());
        }
    }

    /**
     * 현재 대기 인원수 조회
     */
    public int getWaitingCount(User.Gender gender) {
        Queue<Long> queue = gender == User.Gender.MALE ? maleQueue : femaleQueue;
        return queue.size();
    }

    /**
     * 전체 대기 통계 조회
     */
    public Map<String, Object> getMatchingStats() {
        cleanupOldWaitingUsers();

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
     * 오래된 대기자 정리 (5분 이상)
     */
    private void cleanupOldWaitingUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);

        waitingUsers.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                Long userId = entry.getKey();
                maleQueue.remove(userId);
                femaleQueue.remove(userId);
                log.info("오래된 대기자 정리: userId={}", userId);
                return true;
            }
            return false;
        });
    }

    /**
     * 서버 시작 시 초기화
     */
    public void initialize() {
        maleQueue.clear();
        femaleQueue.clear();
        waitingUsers.clear();
        activeChats.clear();
        log.info("매칭 서비스 초기화 완료");
    }
}