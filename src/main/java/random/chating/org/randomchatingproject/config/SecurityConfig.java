package random.chating.org.randomchatingproject.config;

import lombok.RequiredArgsConstructor;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화 (JWT 사용하므로)
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 사용 안함 (JWT 사용하므로)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 요청 권한 설정
                .authorizeHttpRequests(authz -> authz
                        // 선택적 인증 경로 (로그인 안 해도 되지만, 로그인했으면 인증 정보 사용)
                        .requestMatchers("/").permitAll()

                        // 완전히 인증 없이 접근 가능한 경로
                        .requestMatchers("/auth/**", "/user/register", "/user/verify").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // 로그인/회원가입 페이지 - 인증 없이 접근 가능
                        .requestMatchers("/login", "/register").permitAll()

                        // 로그인/회원가입 API - 인증 없이 접근 가능해야 함!
                        .requestMatchers("/auth/login", "/auth/register").permitAll()

                        // 일반 사용자 권한 필요
                        .requestMatchers("/chat/**", "/users/**").hasAnyRole("USER", "ADMIN")

                        // 관리자 권한 필요
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                )

                // 익명 사용자 활성화
                .anonymous(anonymous -> anonymous
                        .authorities("ROLE_ANONYMOUS")
                        .principal("anonymous")
                )

                // JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 기본 로그인 폼 비활성화
                .formLogin(AbstractHttpConfigurer::disable)

                // HTTP Basic 인증 비활성화
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}