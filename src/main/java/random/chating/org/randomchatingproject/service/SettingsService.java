package random.chating.org.randomchatingproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import random.chating.org.randomchatingproject.dto.PasswordChangeRequest;
import random.chating.org.randomchatingproject.dto.EmailChangeRequest;
import random.chating.org.randomchatingproject.dto.AccountSettingsRequest;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.UserSettings;
import random.chating.org.randomchatingproject.repository.UserRepository;
import random.chating.org.randomchatingproject.repository.UserSettingsRepository;
import random.chating.org.randomchatingproject.repository.UserProfileRepository;
import random.chating.org.randomchatingproject.repository.ChatMessageRepository;
import random.chating.org.randomchatingproject.repository.ChatRoomRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SettingsService {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserProfileRepository userProfileRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailgunService mailgunService;

    /**
     * 사용자 설정 조회 (없으면 기본값으로 생성)
     */
    @Transactional(readOnly = true)
    public UserSettings getUserSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElse(null); // null 반환하여 컨트롤러에서 처리
    }

    /**
     * 사용자 설정 확실하게 조회 (없으면 생성)
     */
    @Transactional
    public UserSettings ensureUserSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserSettings newSettings = UserSettings.builder()
                            .userId(userId)
                            .emailNotifications(true)
                            .pushNotifications(false)
                            .marketingNotifications(false)
                            .showOnlineStatus(true)
                            .profileVisible(true)
                            .autoMatching(true)
                            .sameRegionOnly(false)
                            .blockInappropriateContent(true)
                            .autoReportSpam(true)
                            .build();
                    return userSettingsRepository.save(newSettings);
                });
    }

    /**
     * 비밀번호 변경
     */
    public void changePassword(Long userId, PasswordChangeRequest request) {
        log.info("비밀번호 변경 시작: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("현재 비밀번호가 일치하지 않습니다");
        }

        // 새 비밀번호 유효성 검사
        if (request.getNewPassword() == null || request.getNewPassword().length() < 4) {
            throw new RuntimeException("새 비밀번호는 4자 이상이어야 합니다");
        }

        // 현재 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("새 비밀번호는 현재 비밀번호와 달라야 합니다");
        }

        // 비밀번호 업데이트
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("비밀번호 변경 완료: userId={}", userId);
    }

    /**
     * 이메일 변경 요청
     */
    public void requestEmailChange(Long userId, EmailChangeRequest request) {
        log.info("이메일 변경 요청: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다");
        }

        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new RuntimeException("이미 사용 중인 이메일입니다");
        }

        // 현재 이메일과 동일한지 확인
        if (user.getEmail().equals(request.getNewEmail())) {
            throw new RuntimeException("현재 이메일과 동일합니다");
        }

        try {
            // 이메일 변경 인증 메일 발송
            String verificationCode = generateVerificationCode();

            mailgunService.sendMail(
                    request.getNewEmail(),
                    "이메일 변경 인증",
                    "이메일 변경을 완료하려면 다음 인증코드를 입력해주세요: " + verificationCode
            );

            // TODO: 인증 코드를 임시로 저장하고, 사용자가 인증 완료 시 이메일 변경
            log.info("이메일 변경 인증 메일 발송 완료: {} -> {}", user.getEmail(), request.getNewEmail());

        } catch (Exception e) {
            log.error("이메일 변경 인증 메일 발송 실패: {}", e.getMessage());
            throw new RuntimeException("인증 메일 발송에 실패했습니다");
        }
    }

    /**
     * 계정 설정 업데이트
     */
    public void updateAccountSettings(Long userId, AccountSettingsRequest request) {
        log.info("계정 설정 업데이트: userId={}", userId);

        UserSettings settings = ensureUserSettings(userId);

        // 알림 설정 업데이트
        if (request.getEmailNotifications() != null) {
            settings.setEmailNotifications(request.getEmailNotifications());
        }
        if (request.getPushNotifications() != null) {
            settings.setPushNotifications(request.getPushNotifications());
        }
        if (request.getMarketingNotifications() != null) {
            settings.setMarketingNotifications(request.getMarketingNotifications());
        }

        // 개인정보 보호 설정 업데이트
        if (request.getShowOnlineStatus() != null) {
            settings.setShowOnlineStatus(request.getShowOnlineStatus());
        }
        if (request.getProfileVisible() != null) {
            settings.setProfileVisible(request.getProfileVisible());
        }

        userSettingsRepository.save(settings);
        log.info("계정 설정 업데이트 완료: userId={}", userId);
    }

    /**
     * 데이터 다운로드 요청
     */
    public void requestDataDownload(Long userId) {
        log.info("데이터 다운로드 요청: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        try {
            // 사용자 데이터 수집 및 JSON 생성
            String userData = generateUserDataJson(userId);

            // 이메일로 데이터 전송
            mailgunService.sendMail(
                    user.getEmail(),
                    "계정 데이터 다운로드",
                    "요청하신 계정 데이터를 아래에 첨부해드립니다.\n\n" +
                            userData + "\n\n" +
                            "데이터 생성 시간: " + java.time.LocalDateTime.now() + "\n\n" +
                            "※ 이 메일은 개인정보가 포함되어 있으므로 안전하게 보관해주세요."
            );

            log.info("데이터 다운로드 메일 발송 완료: userId={}", userId);

        } catch (Exception e) {
            log.error("데이터 다운로드 실패: {}", e.getMessage());
            throw new RuntimeException("데이터 다운로드 처리 중 오류가 발생했습니다");
        }
    }

    /**
     * 채팅 기록 삭제
     */
    public void clearChatHistory(Long userId) {
        log.info("채팅 기록 삭제 시작: userId={}", userId);

        try {
            // 사용자가 참여한 모든 채팅 메시지 삭제
            chatMessageRepository.deleteBySenderId(userId);

            // 사용자가 참여한 채팅방 정리
            chatRoomRepository.deleteByUser1IdOrUser2Id(userId, userId);

            log.info("채팅 기록 삭제 완료: userId={}", userId);

        } catch (Exception e) {
            log.error("채팅 기록 삭제 실패: {}", e.getMessage());
            throw new RuntimeException("채팅 기록 삭제 중 오류가 발생했습니다");
        }
    }

    /**
     * 계정 비활성화
     */
    public void deactivateAccount(Long userId, HttpServletResponse response) {
        log.info("계정 비활성화: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 계정 비활성화
        user.setEnabled(false);
        userRepository.save(user);

        // 로그아웃 처리 (쿠키 삭제)
        clearAuthCookie(response);

        log.info("계정 비활성화 완료: userId={}", userId);
    }

    /**
     * 계정 삭제
     */
    public void deleteAccount(Long userId, String password, HttpServletResponse response) {
        log.info("계정 삭제 시작: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 비밀번호 확인
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다");
        }

        try {
            // 관련 데이터 삭제
            clearChatHistory(userId);
            userSettingsRepository.deleteByUserId(userId);
            userProfileRepository.deleteByUserId(userId);

            // 사용자 계정 삭제
            userRepository.delete(user);

            // 로그아웃 처리
            clearAuthCookie(response);

            log.info("계정 삭제 완료: userId={}", userId);

        } catch (Exception e) {
            log.error("계정 삭제 실패: {}", e.getMessage());
            throw new RuntimeException("계정 삭제 중 오류가 발생했습니다");
        }
    }

    /**
     * 인증 쿠키 삭제
     */
    private void clearAuthCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("authToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * 6자리 인증 코드 생성
     */
    private String generateVerificationCode() {
        return String.format("%06d", (int) (Math.random() * 1000000));
    }

    /**
     * 사용자 데이터 JSON 생성
     */
    private String generateUserDataJson(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return "{}";

        UserSettings settings = getUserSettings(userId);

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"accountInfo\": {\n");
        json.append("    \"id\": ").append(user.getId()).append(",\n");
        json.append("    \"username\": \"").append(user.getUsername()).append("\",\n");
        json.append("    \"email\": \"").append(user.getEmail()).append("\",\n");
        json.append("    \"gender\": \"").append(user.getGender()).append("\",\n");
        json.append("    \"age\": ").append(user.getAge()).append(",\n");
        json.append("    \"role\": \"").append(user.getRole()).append("\",\n");
        json.append("    \"verified\": ").append(user.isVerified()).append(",\n");
        json.append("    \"enabled\": ").append(user.isEnabled()).append("\n");
        json.append("  }");

        if (settings != null) {
            json.append(",\n");
            json.append("  \"settings\": {\n");
            json.append("    \"emailNotifications\": ").append(settings.getEmailNotifications()).append(",\n");
            json.append("    \"pushNotifications\": ").append(settings.getPushNotifications()).append(",\n");
            json.append("    \"marketingNotifications\": ").append(settings.getMarketingNotifications()).append(",\n");
            json.append("    \"showOnlineStatus\": ").append(settings.getShowOnlineStatus()).append(",\n");
            json.append("    \"profileVisible\": ").append(settings.getProfileVisible()).append(",\n");
            json.append("  }");
        }

        json.append(",\n");
        json.append("  \"exportDate\": \"").append(java.time.LocalDateTime.now()).append("\"\n");
        json.append("}");

        return json.toString();
    }
}