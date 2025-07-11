package random.chating.org.randomchatingproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import random.chating.org.randomchatingproject.dto.AuthRequest;
import random.chating.org.randomchatingproject.dto.AuthResponse;
import random.chating.org.randomchatingproject.dto.RegisterRequest;
import random.chating.org.randomchatingproject.dto.UserResponse;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.VerifyMails;
import random.chating.org.randomchatingproject.jwt.JwtProvider;
import random.chating.org.randomchatingproject.repository.UserRepository;
import random.chating.org.randomchatingproject.repository.VerifyMailRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final MailgunService mailgunService;
    private final VerifyMailRepository verifyMailRepository;

    /**
     * 회원가입
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        log.info("회원가입 처리 시작: {}", request.getUsername());

        // 유효성 검증
        validateRegisterRequest(request);

        // 중복 체크
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("이미 존재하는 사용자명입니다");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("이미 존재하는 이메일입니다");
        }

        // 사용자 생성
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .gender(User.Gender.valueOf(request.getGender().toUpperCase()))
                .age(request.getAge())
                .role(User.Role.USER)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .isVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("사용자 생성 완료: {}", savedUser.getUsername());

        // 이메일 인증 코드 생성 및 발송
        String verifyCode = generateSixDigitCode();
        VerifyMails verifyMails = VerifyMails.builder()
                .email(request.getEmail())
                .code(verifyCode)
                .build();
        verifyMailRepository.save(verifyMails);

        try {
            mailgunService.sendMail(request.getEmail(), "랜덤채팅 인증코드",
                    "회원가입을 완료하려면 다음 인증코드를 입력해주세요: " + verifyCode);
        } catch (Exception e) {
            log.warn("메일 발송 실패: {}", e.getMessage());
            // 메일 발송 실패해도 회원가입은 계속 진행
        }

        // JWT 토큰 생성
        String token = jwtProvider.generateToken(savedUser);

        // 쿠키에 토큰 설정
        setAuthCookie(response, token);

        // 응답 생성
        UserResponse userResponse = UserResponse.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .gender(savedUser.getGender().name())
                .age(savedUser.getAge())
                .role(savedUser.getRole().name())
                .isAuthenticated(true)
                .build();

        return AuthResponse.builder()
                .success(true)
                .message("회원가입이 완료되었습니다")
                .token(token)
                .user(userResponse)
                .build();
    }

    /**
     * 로그인
     */
    @Transactional
    public AuthResponse login(AuthRequest request, HttpServletResponse response) {
        log.info("로그인 처리 시작: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> {
                    log.error("사용자를 찾을 수 없음: {}", request.getUsername());
                    return new RuntimeException("존재하지 않는 사용자입니다");
                });

        log.info("사용자 발견: {}", user.getUsername());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.error("비밀번호 불일치: {}", request.getUsername());
            throw new RuntimeException("비밀번호가 일치하지 않습니다");
        }

        // 계정 상태 확인
        if (!user.isEnabled()) {
            throw new RuntimeException("비활성화된 계정입니다");
        }

        if (!user.isAccountNonLocked()) {
            throw new RuntimeException("잠긴 계정입니다");
        }

        // 로그인 성공 처리
        userDetailsService.recordSuccessfulLogin(user.getUsername());

        // JWT 토큰 생성
        String token = jwtProvider.generateToken(user);

        // 쿠키에 토큰 설정
        setAuthCookie(response, token);

        // 응답 생성
        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .gender(user.getGender().name())
                .age(user.getAge())
                .role(user.getRole().name())
                .isAuthenticated(true)
                .build();

        log.info("로그인 성공: {}", user.getUsername());

        return AuthResponse.builder()
                .success(true)
                .message("로그인이 완료되었습니다")
                .token(token)
                .user(userResponse)
                .build();
    }

    /**
     * 로그아웃
     */
    public void logout(HttpServletResponse response) {
        // 쿠키 삭제
        Cookie cookie = new Cookie("authToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // 개발환경에서는 false
        cookie.setPath("/");
        cookie.setMaxAge(0); // 즉시 만료
        response.addCookie(cookie);

        log.info("로그아웃 처리 완료");
    }

    /**
     * 인증 쿠키 설정
     */
    private void setAuthCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("authToken", token);
        cookie.setHttpOnly(true); // XSS 방지
        cookie.setSecure(false); // 개발환경에서는 false (HTTPS가 아니므로)
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 24시간
        response.addCookie(cookie);

        log.debug("인증 쿠키 설정 완료");
    }

    /**
     * 비밀번호 변경
     */
    @Transactional
    public void resetPassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자입니다"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("현재 비밀번호가 일치하지 않습니다");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("비밀번호 변경 완료: {}", user.getUsername());
    }

    /**
     * 사용자명 변경
     */
    @Transactional
    public void updateUsername(Long userId, String newUsername, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자입니다"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다");
        }

        if (userRepository.existsByUsername(newUsername)) {
            throw new RuntimeException("이미 존재하는 사용자명입니다");
        }

        user.setUsername(newUsername);
        userRepository.save(user);

        log.info("사용자명 변경 완료: {} -> {}", user.getUsername(), newUsername);
    }

    /**
     * 회원가입 요청 유효성 검증
     */
    private void validateRegisterRequest(RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("사용자명은 필수입니다");
        }

        if (request.getPassword() == null || request.getPassword().length() < 4) {
            throw new RuntimeException("비밀번호는 4자 이상이어야 합니다");
        }

        if (request.getEmail() == null || !request.getEmail().contains("@")) {
            throw new RuntimeException("올바른 이메일을 입력해주세요");
        }

        if (request.getGender() == null ||
                (!request.getGender().equalsIgnoreCase("MALE") && !request.getGender().equalsIgnoreCase("FEMALE"))) {
            throw new RuntimeException("성별은 MALE 또는 FEMALE이어야 합니다");
        }

        if (request.getAge() == null || request.getAge() < 18 || request.getAge() > 100) {
            throw new RuntimeException("나이는 18세 이상 100세 이하여야 합니다");
        }
    }

    private String generateSixDigitCode() {
        Random random = new Random();
        int number = random.nextInt(900000) + 100000; // 100000 ~ 999999
        return String.valueOf(number);
    }
}