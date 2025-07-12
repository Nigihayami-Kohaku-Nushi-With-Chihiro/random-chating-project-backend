package random.chating.org.randomchatingproject.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import random.chating.org.randomchatingproject.dto.EmailChangeRequest;
import random.chating.org.randomchatingproject.dto.AccountSettingsRequest;
import random.chating.org.randomchatingproject.dto.PasswordChangeRequest;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.UserSettings;
import random.chating.org.randomchatingproject.service.SettingsService;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    /**
     * 계정 설정 페이지
     */
    @GetMapping("/settings")
    public String settingsPage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) {
            log.warn("비인증 사용자의 설정 페이지 접근 시도");
            return "redirect:/login";
        }

        log.info("계정 설정 페이지 접근: {}", user.getUsername());

        // 기본 사용자 정보
        model.addAttribute("user", user);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("verified", user.isVerified());

        // 계정 상태 정보
        model.addAttribute("joinDate", "2024.01.15"); // TODO: 실제 가입일로 변경
        model.addAttribute("lastLogin", "방금 전"); // TODO: 실제 마지막 로그인으로 변경
        model.addAttribute("accountStatus", user.isEnabled() ? "활성" : "비활성");

        // 사용자 설정 정보 조회 (없으면 기본값)
        UserSettings settings = settingsService.getUserSettings(user.getId());
        if (settings != null) {
            model.addAttribute("emailNotifications", settings.getEmailNotifications());
            model.addAttribute("pushNotifications", settings.getPushNotifications());
            model.addAttribute("marketingNotifications", settings.getMarketingNotifications());
            model.addAttribute("showOnlineStatus", settings.getShowOnlineStatus());
            model.addAttribute("profileVisible", settings.getProfileVisible());
        } else {
            // 기본값 설정
            model.addAttribute("emailNotifications", true);
            model.addAttribute("pushNotifications", false);
            model.addAttribute("marketingNotifications", false);
            model.addAttribute("showOnlineStatus", true);
            model.addAttribute("profileVisible", true);
            model.addAttribute("saveChatHistory", true);
        }

        return "settings";
    }

    /**
     * 사용자 설정 조회 API
     */
    @GetMapping("/api/settings")
    @ResponseBody
    public ResponseEntity<ApiResponse> getSettings(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            UserSettings settings = settingsService.ensureUserSettings(user.getId());
            return ResponseEntity.ok(new ApiResponse(true, "설정 조회 성공", settings));
        } catch (Exception e) {
            log.error("설정 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "설정 조회에 실패했습니다"));
        }
    }

    /**
     * 비밀번호 변경 API
     */
    @PostMapping("/api/settings/change-password")
    @ResponseBody
    public ResponseEntity<ApiResponse> changePassword(@AuthenticationPrincipal User user,
                                                      @RequestBody PasswordChangeRequest request) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            log.info("비밀번호 변경 요청: {}", user.getUsername());

            settingsService.changePassword(user.getId(), request);

            return ResponseEntity.ok(new ApiResponse(true, "비밀번호가 성공적으로 변경되었습니다"));

        } catch (Exception e) {
            log.error("비밀번호 변경 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * 이메일 변경 요청 API
     */
    @PostMapping("/api/settings/change-email")
    @ResponseBody
    public ResponseEntity<ApiResponse> changeEmail(@AuthenticationPrincipal User user,
                                                   @RequestBody EmailChangeRequest request) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            log.info("이메일 변경 요청: {} -> {}", user.getEmail(), request.getNewEmail());

            settingsService.requestEmailChange(user.getId(), request);

            return ResponseEntity.ok(new ApiResponse(true, "이메일 변경 인증 메일이 발송되었습니다"));

        } catch (Exception e) {
            log.error("이메일 변경 요청 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * 계정 설정 업데이트 API (알림 설정, 개인정보 보호 등)
     */
    @PostMapping("/api/settings/update")
    @ResponseBody
    public ResponseEntity<ApiResponse> updateSettings(@AuthenticationPrincipal User user,
                                                      @RequestBody AccountSettingsRequest request) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            log.info("계정 설정 업데이트: {}", user.getUsername());

            settingsService.updateAccountSettings(user.getId(), request);

            return ResponseEntity.ok(new ApiResponse(true, "설정이 성공적으로 업데이트되었습니다"));

        } catch (Exception e) {
            log.error("설정 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * 데이터 다운로드 요청 API
     */
    @PostMapping("/api/settings/download-data")
    @ResponseBody
    public ResponseEntity<ApiResponse> downloadData(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            log.info("데이터 다운로드 요청: {}", user.getUsername());

            settingsService.requestDataDownload(user.getId());

            return ResponseEntity.ok(new ApiResponse(true, "데이터 다운로드 요청이 처리되었습니다. 이메일을 확인해주세요."));

        } catch (Exception e) {
            log.error("데이터 다운로드 요청 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * 채팅 기록 삭제 API
     */
    @DeleteMapping("/api/settings/clear-chat-history")
    @ResponseBody
    public ResponseEntity<ApiResponse> clearChatHistory(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            log.info("채팅 기록 삭제 요청: {}", user.getUsername());

            settingsService.clearChatHistory(user.getId());

            return ResponseEntity.ok(new ApiResponse(true, "채팅 기록이 성공적으로 삭제되었습니다"));

        } catch (Exception e) {
            log.error("채팅 기록 삭제 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * 계정 비활성화 API
     */
    @PostMapping("/api/settings/deactivate-account")
    @ResponseBody
    public ResponseEntity<ApiResponse> deactivateAccount(@AuthenticationPrincipal User user,
                                                         HttpServletResponse response) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            log.info("계정 비활성화 요청: {}", user.getUsername());

            settingsService.deactivateAccount(user.getId(), response);

            return ResponseEntity.ok(new ApiResponse(true, "계정이 비활성화되었습니다"));

        } catch (Exception e) {
            log.error("계정 비활성화 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * 계정 삭제 API
     */
    @PostMapping("/api/settings/delete-account")
    @ResponseBody
    public ResponseEntity<ApiResponse> deleteAccount(@AuthenticationPrincipal User user,
                                                     @RequestBody Map<String, String> request,
                                                     HttpServletResponse response) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            String password = request.get("password");
            log.info("계정 삭제 요청: {}", user.getUsername());

            settingsService.deleteAccount(user.getId(), password, response);

            return ResponseEntity.ok(new ApiResponse(true, "계정이 성공적으로 삭제되었습니다"));

        } catch (Exception e) {
            log.error("계정 삭제 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    // API 응답용 내부 클래스
    public static class ApiResponse {
        private boolean success;
        private String message;
        private Object data;

        public ApiResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public ApiResponse(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Object getData() { return data; }
    }
}