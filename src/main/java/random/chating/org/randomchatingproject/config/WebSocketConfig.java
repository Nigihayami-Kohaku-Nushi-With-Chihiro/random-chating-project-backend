package random.chating.org.randomchatingproject.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import random.chating.org.randomchatingproject.jwt.JwtProvider;
import random.chating.org.randomchatingproject.service.CustomUserDetailsService;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // 🔥 이 부분 수정
                .withSockJS();

        // 개발 환경을 위한 추가 엔드포인트 (SockJS 없이)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    log.info("WebSocket CONNECT 요청 받음");

                    String token = null;

                    // 1순위: Authorization 헤더에서 토큰 확인
                    String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                        token = authorizationHeader.substring(7);
                        log.info("WebSocket Authorization 헤더에서 토큰 추출됨");
                    } else {
                        // 2순위: 쿠키에서 토큰 확인
                        String cookieHeader = accessor.getFirstNativeHeader("Cookie");
                        if (cookieHeader != null) {
                            token = extractTokenFromCookie(cookieHeader);
                            if (token != null) {
                                log.info("WebSocket 쿠키에서 토큰 추출됨");
                            }
                        }
                    }

                    if (token != null) {
                        try {
                            if (jwtProvider.validateToken(token)) {
                                Long userId = jwtProvider.getUserIdFromSubject(token);
                                String username = jwtProvider.getUsername(token);

                                log.info("WebSocket JWT 토큰 검증 성공, userId: {}, username: {}", userId, username);

                                UserDetails userDetails = customUserDetailsService.loadUserByUsername(userId.toString());

                                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities()
                                );

                                accessor.setUser(authentication);
                                log.info("WebSocket 인증 설정 완료: userId={}, username={}", userId, username);
                            } else {
                                log.warn("WebSocket JWT 토큰 검증 실패");
                            }
                        } catch (Exception e) {
                            log.error("WebSocket 인증 처리 중 오류: {}", e.getMessage(), e);
                        }
                    } else {
                        log.warn("WebSocket 토큰이 없음 - 익명 연결 허용");
                        // 토큰이 없어도 연결은 허용 (개발 환경)
                    }
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    // 메시지 전송 시 로깅
                    log.debug("WebSocket 메시지 전송: destination={}", accessor.getDestination());
                }

                return message;
            }
        });
    }

    // 쿠키에서 토큰 추출하는 헬퍼 메서드
    private String extractTokenFromCookie(String cookieHeader) {
        if (cookieHeader == null) return null;

        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=");
            if (parts.length == 2 && "authToken".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }
}