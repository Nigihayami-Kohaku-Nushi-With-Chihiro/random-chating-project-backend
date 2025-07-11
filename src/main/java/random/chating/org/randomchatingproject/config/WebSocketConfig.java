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

    private final JwtProvider jwtProvider;  // final 키워드 추가!
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();
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

                    // 1순위: Authorization 헤더에서 토큰 확인 (기존 호환성)
                    String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                        token = authorizationHeader.substring(7);
                        log.info("WebSocket Authorization 헤더에서 토큰 추출됨");
                    } else {
                        // 2순위: 쿠키에서 토큰 확인 (새로운 방식)
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
                                String username = jwtProvider.getUsername(token);
                                log.info("WebSocket JWT 토큰 검증 성공, 사용자: {}", username);

                                UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

                                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities()
                                );

                                accessor.setUser(authentication);
                                log.info("WebSocket 인증 설정 완료: {}", username);
                            } else {
                                log.warn("WebSocket JWT 토큰 검증 실패");
                            }
                        } catch (Exception e) {
                            log.error("WebSocket 인증 처리 중 오류: {}", e.getMessage(), e);
                        }
                    } else {
                        log.warn("WebSocket 토큰이 없음");
                    }
                }
                return message;
            }
        });
    }

    // 쿠키에서 토큰 추출하는 헬퍼 메서드 추가
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