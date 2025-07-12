package random.chating.org.randomchatingproject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.UserProfile;
import random.chating.org.randomchatingproject.entity.UserSettings;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUserDto {
    private User user;              // 기본 사용자 정보
    private UserProfile profile;    // 확장 프로필 정보
    private UserSettings settings;  // 사용자 설정

    // 편의 메서드들
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public String getUsername() {
        return user != null ? user.getUsername() : null;
    }

    public String getEmail() {
        return user != null ? user.getEmail() : null;
    }

    public boolean isProfileComplete() {
        return user != null &&
                user.getUsername() != null &&
                user.getAge() != null &&
                profile != null &&
                profile.getBio() != null &&
                !profile.getBio().isEmpty();
    }
}