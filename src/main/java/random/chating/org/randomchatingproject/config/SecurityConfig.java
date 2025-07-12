// SecurityConfig.java 전체 코드 (기존 파일 완전 교체)

package random.chating.org.randomchatingproject.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import random.chating.org.randomchatingproject.jwt.JwtAuthenticationFilter;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("SecurityConfig 설정 시작");

        http
                // CSRF 비활성화 (JWT 사용하므로)
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 사용 안함 (JWT 사용하므로)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 요청 권한 설정
                .authorizeHttpRequests(authz -> {
                    log.info("권한 설정 적용 중...");
                    authz
                            // 정적 리소스 - 모든 사용자 접근 가능
                            .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                            // 인증 없이 접근 가능한 페이지
                            .requestMatchers("/", "/login", "/register").permitAll()

                            // 인증 없이 접근 가능한 API
                            .requestMatchers("/auth/**").permitAll()

                            // 이메일 인증 관련
                            .requestMatchers("/user/verify/**").permitAll()

                            // WebSocket 연결
                            .requestMatchers("/ws/**").permitAll()

                            // 프로필 및 설정 페이지 - 로그인 필요
                            .requestMatchers("/profile", "/settings").hasAnyRole("USER", "ADMIN")

                            // 프로필 관련 API - 로그인 필요
                            .requestMatchers("/api/profile/**").hasAnyRole("USER", "ADMIN")

                            // 설정 관련 API - 로그인 필요
                            .requestMatchers("/api/settings/**").hasAnyRole("USER", "ADMIN")

                            // 채팅 관련 - 로그인 필요 (추후 구현)
                            .requestMatchers("/chat/**", "/matching/**").hasAnyRole("USER", "ADMIN")

                            // 일반 사용자 권한 필요
                            .requestMatchers("/users/**").hasAnyRole("USER", "ADMIN")

                            // 관리자 권한 필요
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")

                            .requestMatchers("/waiting", "/chat/**").hasAnyRole("USER", "ADMIN")
                            .requestMatchers("/api/matching/**", "/api/chat/**").hasAnyRole("USER", "ADMIN")

                            // 나머지는 인증 필요
                            .anyRequest().authenticated();
                })

                // JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 기본 로그인 폼 비활성화
                .formLogin(AbstractHttpConfigurer::disable)

                // HTTP Basic 인증 비활성화
                .httpBasic(AbstractHttpConfigurer::disable);

        log.info("SecurityConfig 설정 완료");
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}