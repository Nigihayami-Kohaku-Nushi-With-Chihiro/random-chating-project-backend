package random.chating.org.randomchatingproject.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import random.chating.org.randomchatingproject.service.CustomUserDetailsService;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        // JWT 필터를 건너뛸 경로들
        if (shouldSkipJwtFilter(requestURI)) {
            log.debug("JWT 필터 건너뛰기: {} {}", method, requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        log.info("=== JWT 필터 처리: {} {} ===", method, requestURI);

        try {
            // 토큰 추출 (헤더 우선, 그 다음 쿠키)
            String token = extractTokenFromRequest(request);
            log.info("추출된 토큰: {}", token != null ? "있음" : "없음");

            if (StringUtils.hasText(token)) {
                log.info("토큰 검증 시작");
                if (jwtProvider.validateToken(token)) {
                    String username = jwtProvider.getUsername(token);
                    log.info("토큰에서 사용자명 추출: {}", username);

                    UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                    log.info("사용자 정보 로드 완료: {}", userDetails.getUsername());

                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.info("SecurityContext에 인증 정보 설정 완료: {}", username);
                } else {
                    log.warn("토큰 검증 실패");
                }
            } else {
                log.info("토큰이 없어서 인증 정보 설정하지 않음");
            }
        } catch (Exception e) {
            log.error("JWT 인증 처리 중 오류: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 요청에서 JWT 토큰 추출 (헤더 우선, 쿠키 백업)
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        // 1순위: Authorization 헤더
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            log.info("Authorization 헤더에서 토큰 추출");
            return bearerToken.substring(7);
        }

        // 2순위: 쿠키
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                log.debug("쿠키 확인: {} = {}", cookie.getName(), cookie.getValue());
                if ("authToken".equals(cookie.getName())) {
                    log.info("authToken 쿠키에서 토큰 추출");
                    return cookie.getValue();
                }
            }
        }

        log.info("토큰을 찾을 수 없음");
        return null;
    }

    /**
     * JWT 필터를 건너뛸 경로인지 확인
     */
    private boolean shouldSkipJwtFilter(String requestURI) {
        // 정적 리소스
        if (requestURI.startsWith("/css/") ||
                requestURI.startsWith("/js/") ||
                requestURI.startsWith("/images/") ||
                requestURI.equals("/favicon.ico") ||
                requestURI.equals("/error")) {
            return true;
        }

        // 브라우저 자동 요청
        if (requestURI.startsWith("/.well-known/") ||
                requestURI.equals("/robots.txt") ||
                requestURI.equals("/sitemap.xml")) {
            return true;
        }

        // 인증 관련 API (로그인, 회원가입)
        if (requestURI.startsWith("/auth/")) {
            return true;
        }

        // 기타 인증 관련 경로
        if (requestURI.startsWith("/user/register") ||
                requestURI.startsWith("/user/verify")) {
            return true;
        }

        // 로그인/회원가입 페이지
        if (requestURI.equals("/login") ||
                requestURI.equals("/register")) {
            return true;
        }

        // WebSocket
        if (requestURI.startsWith("/ws/")) {
            return true;
        }

        return false;
    }
}