package random.chating.org.randomchatingproject.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import random.chating.org.randomchatingproject.dto.ProfileUpdateRequest;
import random.chating.org.randomchatingproject.dto.UserResponse;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.UserProfile;
import random.chating.org.randomchatingproject.service.ProfileService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * 프로필 페이지
     */
    @GetMapping("/profile")
    public String profilePage(@AuthenticationPrincipal User user, Model model) {
        if (user == null) {
            log.warn("비인증 사용자의 프로필 페이지 접근 시도");
            return "redirect:/login";
        }

        log.info("프로필 페이지 접근: {}", user.getUsername());

        // 기본 사용자 정보
        model.addAttribute("user", user);
        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("age", user.getAge());
        model.addAttribute("gender", user.getGender());

        // 확장 프로필 정보 조회 (없으면 기본값)
        UserProfile profile = profileService.getUserProfile(user.getId());
        if (profile != null) {
            model.addAttribute("bio", profile.getBio());
            model.addAttribute("location", profile.getLocation());
            model.addAttribute("interests", profile.getInterestsList());
            model.addAttribute("chatStyle", profile.getChatStyle());
            model.addAttribute("meetingPurpose", profile.getMeetingPurpose());
            model.addAttribute("preferredMinAge", profile.getPreferredMinAge());
            model.addAttribute("preferredMaxAge", profile.getPreferredMaxAge());
            model.addAttribute("profileViews", profile.getProfileViews());
            model.addAttribute("totalChats", profile.getTotalChats());
        } else {
            // 기본값 설정
            model.addAttribute("bio", "");
            model.addAttribute("location", "");
            model.addAttribute("interests", java.util.Collections.emptyList());
            model.addAttribute("chatStyle", "any");
            model.addAttribute("meetingPurpose", "friendship");
            model.addAttribute("preferredMinAge", 18);
            model.addAttribute("preferredMaxAge", user.getAge() != null ? user.getAge() + 10 : 100);
            model.addAttribute("profileViews", 0);
            model.addAttribute("totalChats", 0);
        }

        return "profile";
    }

    /**
     * 프로필 정보 조회 API
     */
    @GetMapping("/api/profile")
    @ResponseBody
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .gender(user.getGender().name())
                .age(user.getAge())
                .role(user.getRole().name())
                .isAuthenticated(true)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 프로필 업데이트 API
     */
    @PostMapping("/api/profile/update")
    @ResponseBody
    public ResponseEntity<ApiResponse> updateProfile(@AuthenticationPrincipal User user,
                                                     @RequestBody ProfileUpdateRequest request) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            log.info("프로필 업데이트 요청: {} -> {}", user.getUsername(), request);

            User updatedUser = profileService.updateProfile(user.getId(), request);

            UserResponse response = UserResponse.builder()
                    .id(updatedUser.getId())
                    .username(updatedUser.getUsername())
                    .email(updatedUser.getEmail())
                    .gender(updatedUser.getGender().name())
                    .age(updatedUser.getAge())
                    .role(updatedUser.getRole().name())
                    .isAuthenticated(true)
                    .build();

            return ResponseEntity.ok(new ApiResponse(true, "프로필이 성공적으로 업데이트되었습니다", response));

        } catch (Exception e) {
            log.error("프로필 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * 닉네임 중복 체크 API
     */
    @GetMapping("/api/profile/check-username")
    @ResponseBody
    public ResponseEntity<ApiResponse> checkUsername(@RequestParam String username,
                                                     @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        // 현재 사용자의 닉네임과 같은 경우는 사용 가능
        if (user.getUsername().equals(username)) {
            return ResponseEntity.ok(new ApiResponse(true, "사용 가능한 닉네임입니다"));
        }

        boolean isAvailable = profileService.isUsernameAvailable(username);

        if (isAvailable) {
            return ResponseEntity.ok(new ApiResponse(true, "사용 가능한 닉네임입니다"));
        } else {
            return ResponseEntity.ok(new ApiResponse(false, "이미 사용 중인 닉네임입니다"));
        }
    }

    /**
     * 프로필 통계 조회 API
     */
    @GetMapping("/api/profile/stats")
    @ResponseBody
    public ResponseEntity<ApiResponse> getProfileStats(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            ProfileService.ProfileStats stats = profileService.getProfileStats(user.getId());
            return ResponseEntity.ok(new ApiResponse(true, "프로필 통계 조회 성공", stats));
        } catch (Exception e) {
            log.error("프로필 통계 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "프로필 통계 조회에 실패했습니다"));
        }
    }

    /**
     * 프로필 조회수 증가 API
     */
    @PostMapping("/api/profile/view")
    @ResponseBody
    public ResponseEntity<ApiResponse> incrementProfileView(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(new ApiResponse(false, "로그인이 필요합니다"));
        }

        try {
            profileService.incrementProfileViews(user.getId());
            return ResponseEntity.ok(new ApiResponse(true, "프로필 조회수가 증가했습니다"));
        } catch (Exception e) {
            log.error("프로필 조회수 증가 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "프로필 조회수 증가에 실패했습니다"));
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