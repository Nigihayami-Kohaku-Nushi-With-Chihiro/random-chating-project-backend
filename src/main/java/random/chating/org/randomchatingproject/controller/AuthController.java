package random.chating.org.randomchatingproject.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import random.chating.org.randomchatingproject.dto.UserResponse;
import random.chating.org.randomchatingproject.entity.User;

@Slf4j
@RestController
public class AuthController {
    @GetMapping("/")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            // 비로그인 상태 - null 사용자 정보 반환
            UserResponse response = UserResponse.builder()
                    .id(null)
                    .username(null)
                    .email(null)
                    .gender(null)
                    .age(null)
                    .role(null)
                    .isAuthenticated(false)  // 이 필드 추가
                    .build();

            return ResponseEntity.ok(response);
        }

        // 로그인 상태
        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .gender(user.getGender().name())
                .age(user.getAge())
                .role(user.getRole().name())
                .isAuthenticated(true)   // 이 필드 추가
                .build();

        return ResponseEntity.ok(response);
    }
}
