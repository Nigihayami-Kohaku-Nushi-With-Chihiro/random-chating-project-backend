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
import random.chating.org.randomchatingproject.entity.UserProfile;
import random.chating.org.randomchatingproject.entity.UserSettings;
import random.chating.org.randomchatingproject.entity.VerifyMails;
import random.chating.org.randomchatingproject.jwt.JwtProvider;
import random.chating.org.randomchatingproject.repository.UserRepository;
import random.chating.org.randomchatingproject.repository.UserProfileRepository;
import random.chating.org.randomchatingproject.repository.UserSettingsRepository;
import random.chating.org.randomchatingproject.repository.VerifyMailRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    // 의존성 주입
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final MailgunService mailgunService;
    private final VerifyMailRepository verifyMailRepository;

    /**
     * 회원가입 - 3개 테이블 모두 생성
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

        try {
            // 1️⃣ User 테이블 생성 (기본 계정 정보)
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
            log.info("✅ User 생성 완료: userId={}", savedUser.getId());

            // 2️⃣ UserProfile 테이블 생성 (확장 프로필 정보)
            UserProfile defaultProfile = UserProfile.builder()
                    .userId(savedUser.getId()) // 🔗 User와 연결되는 외래키
                    .bio(null) // 자기소개 (나중에 입력)
                    .location(null) // 지역 (나중에 입력)
                    .interests(null) // 관심사 (나중에 선택)
                    .profileImageUrl(null) // 프로필 이미지 (나중에 업로드)
                    // 통계 초기값
                    .profileViews(0) // 프로필 조회수
                    .totalChats(0) // 총 채팅 수
                    .build();

            userProfileRepository.save(defaultProfile);
            log.info("✅ UserProfile 생성 완료: userId={}", savedUser.getId());

            // 3️⃣ UserSettings 테이블 생성 (사용자 설정)
            UserSettings defaultSettings = UserSettings.builder()
                    .userId(savedUser.getId()) // 🔗 User와 연결되는 외래키
                    // 알림 설정 기본값
                    .emailNotifications(true) // 이메일 알림 ON
                    .pushNotifications(false) // 푸시 알림 OFF (브라우저 권한 필요)
                    .marketingNotifications(false) // 마케팅 알림 OFF (사용자가 직접 선택하도록)
                    // 개인정보 보호 설정 기본값
                    .showOnlineStatus(true) // 온라인 상태 표시 ON
                    .profileVisible(true) // 프로필 공개 ON
                    .build();

            userSettingsRepository.save(defaultSettings);
            log.info("✅ UserSettings 생성 완료: userId={}", savedUser.getId());

            // 4️⃣ 이메일 인증 코드 생성 및 발송
            String verifyCode = generateSixDigitCode();
            VerifyMails verifyMails = VerifyMails.builder()
                    .email(request.getEmail())
                    .code(verifyCode)
                    .build();
            verifyMailRepository.save(verifyMails);

            try {
                mailgunService.sendMail(request.getEmail(), "🎉 랜덤채팅 회원가입 인증",
                        "회원가입을 환영합니다! 🎊\n\n" +
                                "계정을 활성화하려면 다음 인증코드를 입력해주세요:\n\n" +
                                "📱 인증코드: " + verifyCode + "\n\n" +
                                "랜덤채팅에서 새로운 만남을 시작해보세요!");
            } catch (Exception e) {
                log.warn("메일 발송 실패: {}", e.getMessage());
                // 메일 발송 실패해도 회원가입은 계속 진행
            }

            // 5️⃣ JWT 토큰 생성
            String token = jwtProvider.generateToken(savedUser);

            // 6️⃣ 쿠키에 토큰 설정
            setAuthCookie(response, token);

            // 7️⃣ 응답 생성
            UserResponse userResponse = UserResponse.builder()
                    .id(savedUser.getId())
                    .username(savedUser.getUsername())
                    .email(savedUser.getEmail())
                    .gender(savedUser.getGender().name())
                    .age(savedUser.getAge())
                    .role(savedUser.getRole().name())
                    .isAuthenticated(true)
                    .build();

            log.info("🎉 회원가입 완료: userId={}, username={}", savedUser.getId(), savedUser.getUsername());

            return AuthResponse.builder()
                    .success(true)
                    .message("회원가입이 완료되었습니다! 프로필을 완성해보세요.")
                    .token(token)
                    .user(userResponse)
                    .build();

        } catch (Exception e) {
            log.error("회원가입 실패: {}", e.getMessage(), e);
            throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
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
     * 스마트 나이 기반 선호 연령 계산
     */
    private int calculatePreferredMaxAge(int userAge) {
        if (userAge <= 25) {
            return userAge + 10; // 젊은 사용자는 넓은 범위
        } else if (userAge <= 35) {
            return userAge + 8;  // 중간 연령대는 조금 좁게
        } else if (userAge <= 50) {
            return userAge + 5;  // 중년층은 비슷한 연령대
        } else {
            return Math.min(userAge + 3, 100); // 시니어는 좁은 범위
        }
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