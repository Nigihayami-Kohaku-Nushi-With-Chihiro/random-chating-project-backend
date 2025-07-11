package random.chating.org.randomchatingproject.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import random.chating.org.randomchatingproject.service.CustomUserDetailsService;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService; // 오타 수정

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

        log.debug("=== JWT 필터 시작: {} {} ===", method, requestURI);

        // 헤더에서 토큰 추출 (안전하게)
        String token = extractTokenFromRequest(request);

        if (!StringUtils.hasText(token)) {
            log.debug("토큰 없음 - 익명 사용자로 설정");
            // 익명 사용자로 인증 설정
            setAnonymousAuthentication();
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("토큰 발견됨");

        try {
            // 토큰 검증
            if (!jwtProvider.validateToken(token)) {
                log.warn("토큰 검증 실패 - 익명 사용자로 설정");
                setAnonymousAuthentication();
                filterChain.doFilter(request, response);
                return;
            }

            // 토큰에서 사용자명 추출
            String username = jwtProvider.getUsername(token);
            log.debug("토큰에서 사용자명 추출: {}", username);

            // UserDetailsService로 사용자 정보 조회
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            log.debug("사용자 정보 로드 완료: {}", userDetails.getUsername());

            // Spring Security 컨텍스트에 인증 정보 설정
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Spring Security 인증 설정 완료: {}", username);

        } catch (Exception e) {
            log.error("JWT 인증 처리 중 오류: {} - 익명 사용자로 설정", e.getMessage());
            setAnonymousAuthentication();
        }

        log.debug("=== JWT 필터 종료 ===");
        filterChain.doFilter(request, response);
    }

    /**
     * 익명 사용자 인증 설정
     */
    private void setAnonymousAuthentication() {
        Authentication anonymousAuth = new AnonymousAuthenticationToken(
                "anonymous",
                "anonymous",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymousAuth);
        log.debug("익명 사용자 인증 설정 완료");
    }

    /**
     * 요청에서 JWT 토큰 추출 (안전하게)
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " 제거
        }

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

        // 인증 관련 API (로그인, 회원가입) - 중요!
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