package random.chating.org.randomchatingproject.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import random.chating.org.randomchatingproject.dto.ChatMessageDto;
import random.chating.org.randomchatingproject.dto.MatchingRequest;
import random.chating.org.randomchatingproject.dto.MatchingResult;
import random.chating.org.randomchatingproject.entity.ChatMessage;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.repository.UserRepository;
import random.chating.org.randomchatingproject.service.ChatService;
import random.chating.org.randomchatingproject.service.MatchingService;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MatchingService matchingService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    // 메시지 중복 방지를 위한 최근 메시지 캐시
    private final Map<String, Long> recentMessages = new ConcurrentHashMap<>();

    /**
     * 대기 페이지
     */
    @GetMapping("/waiting")
    public String waitingPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) {
            return "redirect:/login";
        }

        Map<String, Object> userStatus = matchingService.getUserStatus(user.getId());

        if ((Boolean) userStatus.get("isInChat")) {
            String roomId = (String) userStatus.get("roomId");
            return "redirect:/chat/" + roomId;
        }

        Map<String, Object> stats = matchingService.getMatchingStats();

        model.addAttribute("user", user);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("gender", user.getGender().name());
        model.addAttribute("age", user.getAge());
        model.addAttribute("isWaiting", userStatus.get("isWaiting"));
        model.addAttribute("maleWaiting", stats.get("maleWaiting"));
        model.addAttribute("femaleWaiting", stats.get("femaleWaiting"));
        model.addAttribute("activeChats", stats.get("activeChats"));

        return "waiting";
    }

    /**
     * 채팅 페이지
     */
    @GetMapping("/chat/{roomId}")
    public String chatPage(@PathVariable String roomId,
                           @AuthenticationPrincipal User user,
                           Model model) {
        log.info("=== 채팅 페이지 접근 ===");
        log.info("roomId: {}", roomId);
        log.info("user: {}", user != null ? user.getUsername() : "null");

        if (user == null) {
            log.error("사용자가 null입니다!");
            return "redirect:/login";
        }

        log.info("사용자 정보: userId={}, username={}", user.getId(), user.getUsername());

        if (!chatService.canUserAccessRoom(user.getId(), roomId)) {
            log.warn("채팅방 접근 권한 없음: userId={}, roomId={}", user.getId(), roomId);
            return "redirect:/";
        }

        log.info("채팅방 접근 권한 확인 완료");

        // 채팅 기록 조회 (사용자 정보 포함)
        var chatHistory = chatService.getChatHistoryWithUserInfo(roomId);
        log.info("채팅 기록 조회 완료: {} 개", chatHistory.size());

        // 채팅 상대방 정보 조회
        User partner = chatService.getChatPartner(roomId, user.getId());
        log.info("상대방 정보: {}", partner != null ? partner.getUsername() : "null");

        // Model에 값 추가하면서 로깅
        model.addAttribute("user", user);
        model.addAttribute("roomId", roomId);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userAvatar", user.getUsername().substring(0, 1).toUpperCase());
        model.addAttribute("chatHistory", chatHistory);

        log.info("Model에 추가된 값들:");
        log.info("- roomId: {}", roomId);
        log.info("- username: {}", user.getUsername());
        log.info("- userAvatar: {}", user.getUsername().substring(0, 1).toUpperCase());

        if (partner != null) {
            model.addAttribute("partnerName", partner.getUsername());
            model.addAttribute("partnerAvatar", partner.getUsername().substring(0, 1).toUpperCase());
            model.addAttribute("partnerAge", partner.getAge());
            model.addAttribute("partnerGender", partner.getGender().name());

            log.info("상대방 정보 추가:");
            log.info("- partnerName: {}", partner.getUsername());
            log.info("- partnerAvatar: {}", partner.getUsername().substring(0, 1).toUpperCase());
        } else {
            log.warn("상대방 정보가 null입니다!");
            // 기본값 설정
            model.addAttribute("partnerName", "상대방");
            model.addAttribute("partnerAvatar", "?");
            model.addAttribute("partnerAge", 0);
            model.addAttribute("partnerGender", "UNKNOWN");
        }

        log.info("=== 채팅 페이지 렌더링 시작 ===");
        return "chat";
    }

    // ========== REST API ==========

    /**
     * 매칭 요청 API
     */
    @PostMapping("/api/matching/start")
    @ResponseBody
    public ResponseEntity<MatchingResult> startMatching(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(MatchingResult.builder()
                            .success(false)
                            .message("로그인이 필요합니다")
                            .build());
        }

        try {
            MatchingResult result = matchingService.requestMatching(user.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("매칭 요청 실패: userId={}, error={}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(MatchingResult.builder()
                            .success(false)
                            .message("매칭 요청 처리 중 오류가 발생했습니다")
                            .build());
        }
    }

    /**
     * 매칭 취소 API
     */
    @PostMapping("/api/matching/cancel")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cancelMatching(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }

        try {
            boolean cancelled = matchingService.cancelMatching(user.getId());
            return ResponseEntity.ok(Map.of("success", true, "message", "매칭이 취소되었습니다"));
        } catch (Exception e) {
            log.error("매칭 취소 실패: userId={}, error={}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "매칭 취소 중 오류가 발생했습니다"));
        }
    }

    /**
     * 채팅 종료 API
     */
    @PostMapping("/api/chat/end")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> endChat(@AuthenticationPrincipal User user,
                                                       @RequestBody Map<String, String> request) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }

        String roomId = request.get("roomId");
        if (roomId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "채팅방 ID가 필요합니다"));
        }

        try {
            boolean ended = matchingService.endChat(user.getId(), roomId);
            return ResponseEntity.ok(Map.of("success", true, "message", "채팅이 종료되었습니다"));
        } catch (Exception e) {
            log.error("채팅 종료 실패: userId={}, roomId={}, error={}", user.getId(), roomId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "채팅 종료 중 오류가 발생했습니다"));
        }
    }

    /**
     * 매칭 상태 조회 API
     */
    @GetMapping("/api/matching/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMatchingStatus(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "로그인이 필요합니다"));
        }

        try {
            Map<String, Object> userStatus = matchingService.getUserStatus(user.getId());
            Map<String, Object> stats = matchingService.getMatchingStats();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "userStatus", userStatus,
                    "stats", stats
            ));
        } catch (Exception e) {
            log.error("매칭 상태 조회 실패: userId={}, error={}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "상태 조회 중 오류가 발생했습니다"));
        }
    }

    // ========== WebSocket 메시지 핸들러 ==========

    /**
     * 채팅 메시지 처리 - 완전 개선된 버전
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDto messageDto, Principal principal,
                            SimpMessageHeaderAccessor headerAccessor) {
        // 중복 방지를 위한 메시지 키 생성
        String messageKey = messageDto.getRoomId() + "_" + messageDto.getMessage() + "_" + messageDto.getSenderUsername();

        // 중복 메시지 체크 (5초 내 같은 메시지)
        Long lastTime = recentMessages.get(messageKey);
        long currentTime = System.currentTimeMillis();

        if (lastTime != null && (currentTime - lastTime) < 5000) {
            log.warn("중복 메시지 감지: messageKey={}", messageKey);
            return;
        }

        recentMessages.put(messageKey, currentTime);

        // 오래된 메시지 키 정리 (1분 이상 된 것들)
        recentMessages.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > 60000);

        try {
            log.info("메시지 처리 시작: roomId={}, message={}, sender={}",
                    messageDto.getRoomId(), messageDto.getMessage(), messageDto.getSenderUsername());

            Long userId = null;
            String username = null;

            // 1순위: Principal에서 사용자 정보 추출
            if (principal != null) {
                try {
                    // Principal.getName()이 userId를 반환하는지 확인
                    if (principal.getName().matches("\\d+")) {
                        userId = Long.parseLong(principal.getName());
                        User user = userRepository.findById(userId).orElse(null);
                        if (user != null) {
                            username = user.getUsername();
                        }
                    } else {
                        // username일 경우 사용자 조회
                        User user = userRepository.findByUsername(principal.getName()).orElse(null);
                        if (user != null) {
                            userId = user.getId();
                            username = user.getUsername();
                        }
                    }
                    log.info("Principal에서 사용자 정보 추출: userId={}, username={}", userId, username);
                } catch (Exception e) {
                    log.warn("Principal 파싱 실패: {}", e.getMessage());
                }
            }

            // 2순위: WebSocket 세션에서 사용자 정보 추출
            if (userId == null && headerAccessor != null) {
                try {
                    Authentication auth = (Authentication) headerAccessor.getUser();
                    if (auth != null && auth.getPrincipal() instanceof User) {
                        User user = (User) auth.getPrincipal();
                        userId = user.getId();
                        username = user.getUsername();
                        log.info("WebSocket 세션에서 사용자 정보 추출: userId={}, username={}", userId, username);
                    }
                } catch (Exception e) {
                    log.warn("WebSocket 세션 정보 추출 실패: {}", e.getMessage());
                }
            }

            // 3순위: 메시지에서 사용자 정보 추출 (백업 방법)
            if (userId == null && messageDto.getSenderUsername() != null) {
                User user = userRepository.findByUsername(messageDto.getSenderUsername()).orElse(null);
                if (user != null) {
                    userId = user.getId();
                    username = user.getUsername();
                    log.info("메시지에서 사용자 정보 추출: userId={}, username={}", userId, username);
                }
            }

            // 4순위: 하드코딩된 fallback (개발용)
            if (userId == null) {
                log.warn("사용자 인증 실패, 메시지에서 username 확인: {}", messageDto.getSenderUsername());

                // 메시지의 senderUsername으로 사용자 찾기
                if (messageDto.getSenderUsername() != null && !messageDto.getSenderUsername().equals("User")) {
                    User user = userRepository.findByUsername(messageDto.getSenderUsername()).orElse(null);
                    if (user != null) {
                        userId = user.getId();
                        username = user.getUsername();
                        log.info("메시지 username으로 사용자 복구: userId={}, username={}", userId, username);
                    }
                }

                // 여전히 없으면 roomId로 사용자 추정 (최후의 수단)
                if (userId == null && messageDto.getRoomId() != null) {
                    try {
                        var chatRoom = chatService.getChatRoom(messageDto.getRoomId());
                        if (chatRoom != null) {
                            // ⭐ 메시지의 senderUsername으로 올바른 사용자 구분
                            String msgUsername = messageDto.getSenderUsername();
                            if (msgUsername != null && !msgUsername.equals("User")) {
                                // user1과 user2 중에서 username이 일치하는 사용자 찾기
                                User user1 = userRepository.findById(chatRoom.getUser1Id()).orElse(null);
                                User user2 = userRepository.findById(chatRoom.getUser2Id()).orElse(null);

                                if (user1 != null && user1.getUsername().equals(msgUsername)) {
                                    userId = user1.getId();
                                    username = user1.getUsername();
                                    log.info("roomId + username으로 user1 매칭: userId={}, username={}", userId, username);
                                } else if (user2 != null && user2.getUsername().equals(msgUsername)) {
                                    userId = user2.getId();
                                    username = user2.getUsername();
                                    log.info("roomId + username으로 user2 매칭: userId={}, username={}", userId, username);
                                } else {
                                    // 마지막 수단으로 user1 사용
                                    userId = chatRoom.getUser1Id();
                                    User user = userRepository.findById(userId).orElse(null);
                                    if (user != null) {
                                        username = user.getUsername();
                                        log.warn("roomId로 user1 기본 선택: userId={}, username={}", userId, username);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("roomId로 사용자 추정 실패: {}", e.getMessage());
                    }
                }
            }

            // 여전히 사용자 정보가 없으면 에러
            if (userId == null) {
                log.error("모든 방법으로 사용자 인증 실패: messageDto={}", messageDto);
                return;
            }

            // 메시지 저장
            ChatMessage savedMessage = chatService.saveMessage(
                    messageDto.getRoomId(),
                    userId,
                    messageDto.getMessage(),
                    ChatMessage.MessageType.TEXT
            );

            // username이 여전히 없다면 DB에서 조회
            if (username == null) {
                User sender = chatService.getUserById(userId);
                if (sender != null) {
                    username = sender.getUsername();
                } else {
                    username = "Unknown";
                }
            }

            // 응답 메시지 구성
            ChatMessageDto response = ChatMessageDto.builder()
                    .type("TEXT")
                    .roomId(messageDto.getRoomId())
                    .message(messageDto.getMessage())
                    .senderUsername(username)
                    .senderAvatar(username.substring(0, 1).toUpperCase())
                    .timestamp(savedMessage.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                    .build();

            // 채팅방에 메시지 브로드캐스트
            messagingTemplate.convertAndSend("/topic/chat/" + messageDto.getRoomId(), response);

            log.info("채팅 메시지 전송 완료: roomId={}, sender={}, message={}",
                    messageDto.getRoomId(), username, messageDto.getMessage());

        } catch (Exception e) {
            log.error("채팅 메시지 처리 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 매칭 요청 처리 (WebSocket)
     */
    @MessageMapping("/matching.request")
    public void handleMatchingRequest(@Payload MatchingRequest request, Principal principal) {
        try {
            Long userId = extractUserIdFromPrincipal(principal);
            if (userId == null) {
                log.warn("인증되지 않은 사용자의 매칭 요청");
                return;
            }

            switch (request.getAction().toUpperCase()) {
                case "START":
                    MatchingResult result = matchingService.requestMatching(userId);
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/matching",
                            result
                    );
                    break;

                case "CANCEL":
                    matchingService.cancelMatching(userId);
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/matching",
                            Map.of("type", "MATCHING_CANCELLED", "message", "매칭이 취소되었습니다")
                    );
                    break;

                case "END":
                    if (request.getRoomId() != null) {
                        matchingService.endChat(userId, request.getRoomId());
                    }
                    break;
            }

        } catch (Exception e) {
            log.error("매칭 요청 처리 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 사용자 입장 알림
     */
    @MessageMapping("/chat.addUser")
    public void addUser(@Payload Map<String, String> payload, Principal principal,
                        SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = extractUserIdFromContext(principal, headerAccessor);
            String roomId = payload.get("roomId");

            if (userId == null || roomId == null) {
                log.warn("사용자 입장 처리 실패: userId={}, roomId={}", userId, roomId);
                return;
            }

            User user = chatService.getUserById(userId);
            if (user == null) {
                log.warn("사용자를 찾을 수 없음: userId={}", userId);
                return;
            }

            // 입장 메시지 저장
            ChatMessage joinMessage = chatService.saveMessage(
                    roomId,
                    userId,
                    user.getUsername() + "님이 입장했습니다.",
                    ChatMessage.MessageType.SYSTEM
            );

            // 시스템 메시지로 입장 알림
            ChatMessageDto joinNotification = ChatMessageDto.builder()
                    .type("SYSTEM")
                    .roomId(roomId)
                    .message(user.getUsername() + "님이 입장했습니다.")
                    .senderUsername("SYSTEM")
                    .senderAvatar("S")
                    .timestamp(joinMessage.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + roomId, joinNotification);
            log.info("사용자 입장 알림 완료: userId={}, username={}, roomId={}",
                    userId, user.getUsername(), roomId);

        } catch (Exception e) {
            log.error("사용자 입장 처리 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 사용자 퇴장 알림
     */
    @MessageMapping("/chat.removeUser")
    public void removeUser(@Payload Map<String, String> payload, Principal principal,
                           SimpMessageHeaderAccessor headerAccessor) {
        try {
            Long userId = extractUserIdFromContext(principal, headerAccessor);
            String roomId = payload.get("roomId");

            if (userId == null || roomId == null) {
                log.warn("사용자 퇴장 처리 실패: userId={}, roomId={}", userId, roomId);
                return;
            }

            User user = chatService.getUserById(userId);
            if (user == null) {
                log.warn("사용자를 찾을 수 없음: userId={}", userId);
                return;
            }

            // 퇴장 메시지 저장
            ChatMessage leaveMessage = chatService.saveMessage(
                    roomId,
                    userId,
                    user.getUsername() + "님이 퇴장했습니다.",
                    ChatMessage.MessageType.SYSTEM
            );

            // 시스템 메시지로 퇴장 알림
            ChatMessageDto leaveNotification = ChatMessageDto.builder()
                    .type("SYSTEM")
                    .roomId(roomId)
                    .message(user.getUsername() + "님이 퇴장했습니다.")
                    .senderUsername("SYSTEM")
                    .senderAvatar("S")
                    .timestamp(leaveMessage.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                    .build();

            messagingTemplate.convertAndSend("/topic/chat/" + roomId, leaveNotification);
            log.info("사용자 퇴장 알림 완료: userId={}, username={}, roomId={}",
                    userId, user.getUsername(), roomId);

        } catch (Exception e) {
            log.error("사용자 퇴장 처리 실패: error={}", e.getMessage(), e);
        }
    }

    // ========== 헬퍼 메소드들 ==========

    /**
     * Principal에서 사용자 ID 추출
     */
    private Long extractUserIdFromPrincipal(Principal principal) {
        if (principal == null) {
            return null;
        }

        try {
            if (principal.getName().matches("\\d+")) {
                return Long.parseLong(principal.getName());
            } else {
                User user = userRepository.findByUsername(principal.getName()).orElse(null);
                return user != null ? user.getId() : null;
            }
        } catch (Exception e) {
            log.debug("Principal에서 사용자 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 다양한 소스에서 사용자 ID 추출하는 헬퍼 메소드
     */
    private Long extractUserIdFromContext(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        // 1순위: Principal에서 추출
        Long userId = extractUserIdFromPrincipal(principal);
        if (userId != null) {
            return userId;
        }

        // 2순위: WebSocket 세션에서 추출
        if (headerAccessor != null) {
            try {
                Authentication auth = (Authentication) headerAccessor.getUser();
                if (auth != null && auth.getPrincipal() instanceof User) {
                    return ((User) auth.getPrincipal()).getId();
                }
            } catch (Exception e) {
                log.debug("WebSocket 세션에서 사용자 ID 추출 실패: {}", e.getMessage());
            }
        }

        return null;
    }
}