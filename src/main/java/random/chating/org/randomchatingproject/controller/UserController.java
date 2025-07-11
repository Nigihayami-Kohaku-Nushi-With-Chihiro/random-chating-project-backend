package random.chating.org.randomchatingproject.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import random.chating.org.randomchatingproject.dto.AuthRequest;
import random.chating.org.randomchatingproject.dto.AuthResponse;
import random.chating.org.randomchatingproject.dto.RegisterRequest;
import random.chating.org.randomchatingproject.service.AuthService;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final AuthService authService;

    /**
     * 회원가입
     * POST /auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request,
                                                 HttpServletResponse response) {
        log.info("회원가입 요청: username={}, email={}", request.getUsername(), request.getEmail());

        try {
            AuthResponse authResponse = authService.register(request, response);
            log.info("회원가입 성공: {}", request.getUsername());
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.warn("회원가입 실패: {} - {}", request.getUsername(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .token(null)
                            .user(null)
                            .build());
        }
    }

    /**
     * 로그인
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request,
                                              HttpServletResponse response) {
        log.info("로그인 요청: username={}", request.getUsername());

        try {
            AuthResponse authResponse = authService.login(request, response);
            log.info("로그인 성공: {}", request.getUsername());
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.warn("로그인 실패: {} - {}", request.getUsername(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(AuthResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .token(null)
                            .user(null)
                            .build());
        }
    }

    /**
     * 로그아웃
     * POST /auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        log.info("로그아웃 요청");

        try {
            authService.logout(response);
            return ResponseEntity.ok("로그아웃 되었습니다");
        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류: {}", e.getMessage());
            return ResponseEntity.badRequest().body("로그아웃 처리 중 오류가 발생했습니다");
        }
    }
}