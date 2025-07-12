package random.chating.org.randomchatingproject.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import random.chating.org.randomchatingproject.dto.ChatMessageDto;
import random.chating.org.randomchatingproject.dto.MatchingRequest;
import random.chating.org.randomchatingproject.dto.MatchingResult;
import random.chating.org.randomchatingproject.entity.ChatMessage;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.service.ChatService;
import random.chating.org.randomchatingproject.service.MatchingService;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final MatchingService matchingService;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

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
        if (user == null) {
            return "redirect:/login";
        }

        if (!chatService.canUserAccessRoom(user.getId(), roomId)) {
            log.warn("채팅방 접근 권한 없음: userId={}, roomId={}", user.getId(), roomId);
            return "redirect:/";
        }

        // 채팅 기록 조회 (사용자 정보 포함)
        var chatHistory = chatService.getChatHistoryWithUserInfo(roomId);

        // 채팅 상대방 정보 조회
        User partner = chatService.getChatPartner(roomId, user.getId());

        model.addAttribute("user", user);
        model.addAttribute("roomId", roomId);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("userAvatar", user.getUsername().substring(0, 1).toUpperCase());
        model.addAttribute("chatHistory", chatHistory);

        if (partner != null) {
            model.addAttribute("partnerName", partner.getUsername());
            model.addAttribute("partnerAvatar", partner.getUsername().substring(0, 1).toUpperCase());
            model.addAttribute("partnerAge", partner.getAge());
            model.addAttribute("partnerGender", partner.getGender().name());
        }

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
     * 채팅 메시지 처리
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessageDto messageDto, Principal principal) {
        try {
            if (principal == null) {
                log.warn("인증되지 않은 사용자의 메시지 전송 시도");
                return;
            }

            Long userId = Long.parseLong(principal.getName());

            // 메시지 저장
            ChatMessage savedMessage = chatService.saveMessage(
                    messageDto.getRoomId(),
                    userId,
                    messageDto.getMessage(),
                    ChatMessage.MessageType.TEXT
            );

            // 사용자 정보 조회
            User sender = chatService.getUserById(userId);

            // 응답 메시지 구성
            ChatMessageDto response = ChatMessageDto.builder()
                    .type("TEXT")
                    .roomId(messageDto.getRoomId())
                    .message(messageDto.getMessage())
                    .senderUsername(sender.getUsername())
                    .senderAvatar(sender.getUsername().substring(0, 1).toUpperCase())
                    .timestamp(savedMessage.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")))
                    .build();

            // 채팅방에 메시지 브로드캐스트
            messagingTemplate.convertAndSend("/topic/chat/" + messageDto.getRoomId(), response);

            log.info("채팅 메시지 전송 완료: roomId={}, sender={}",
                    messageDto.getRoomId(), sender.getUsername());

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
            if (principal == null) {
                log.warn("인증되지 않은 사용자의 매칭 요청");
                return;
            }

            Long userId = Long.parseLong(principal.getName());

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
    public void addUser(@Payload Map<String, String> payload, Principal principal) {
        try {
            if (principal == null) {
                return;
            }

            Long userId = Long.parseLong(principal.getName());
            String roomId = payload.get("roomId");

            if (roomId == null) {
                return;
            }

            User user = chatService.getUserById(userId);
            if (user == null) {
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

        } catch (Exception e) {
            log.error("사용자 입장 처리 실패: error={}", e.getMessage(), e);
        }
    }

    /**
     * 사용자 퇴장 알림
     */
    @MessageMapping("/chat.removeUser")
    public void removeUser(@Payload Map<String, String> payload, Principal principal) {
        try {
            if (principal == null) {
                return;
            }

            Long userId = Long.parseLong(principal.getName());
            String roomId = payload.get("roomId");

            if (roomId == null) {
                return;
            }

            User user = chatService.getUserById(userId);
            if (user == null) {
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

        } catch (Exception e) {
            log.error("사용자 퇴장 처리 실패: error={}", e.getMessage(), e);
        }
    }
}