// WebSocketSessionListener.java - 새로 생성할 파일
package random.chating.org.randomchatingproject.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.service.ChatService;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionListener {

    private final ChatService chatService;

    /**
     * WebSocket 연결 이벤트 처리
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        try {
            Authentication authentication = (Authentication) headerAccessor.getUser();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                // 사용자를 온라인 상태로 등록
                chatService.addOnlineUser(userId, sessionId);

                log.info("WebSocket 연결됨: userId={}, username={}, sessionId={}",
                        userId, user.getUsername(), sessionId);
            } else {
                log.info("익명 WebSocket 연결: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.error("WebSocket 연결 처리 중 오류: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * WebSocket 연결 끊김 이벤트 처리 - 핵심 기능
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        try {
            Authentication authentication = (Authentication) headerAccessor.getUser();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                User user = (User) authentication.getPrincipal();
                Long userId = user.getId();

                log.info("WebSocket 연결 끊김 감지: userId={}, username={}, sessionId={}",
                        userId, user.getUsername(), sessionId);

                // 🔥 핵심: 사용자를 오프라인으로 처리하고 상대방에게 알림
                chatService.removeOnlineUser(sessionId);

                log.info("사용자 연결 끊김 처리 완료: userId={}, username={}", userId, user.getUsername());
            } else {
                log.info("익명 WebSocket 연결 끊김: sessionId={}", sessionId);
                // 익명 사용자도 혹시 모르니 처리
                chatService.removeOnlineUser(sessionId);
            }
        } catch (Exception e) {
            log.error("WebSocket 연결 끊김 처리 중 오류: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }
}