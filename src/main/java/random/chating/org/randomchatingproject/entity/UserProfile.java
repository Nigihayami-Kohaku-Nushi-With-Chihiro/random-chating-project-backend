package random.chating.org.randomchatingproject.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // 프로필 정보
    @Column(name = "bio", length = 200)
    private String bio; // 자기소개

    @Column(name = "location", length = 50)
    private String location; // 지역

    @Column(name = "interests", length = 500)
    private String interests;

    @Column(name = "profile_image_url")
    private String profileImageUrl; // 프로필 이미지 URL

    // 통계 정보
    @Column(name = "profile_views")
    @Builder.Default
    private Integer profileViews = 0; // 프로필 조회수

    @Column(name = "total_chats")
    @Builder.Default
    private Integer totalChats = 0; // 총 채팅 수

    // 메타데이터
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 관심사를 리스트로 반환
    public List<String> getInterestsList() {
        if (interests == null || interests.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(interests.split(","));
    }

    // 관심사 리스트를 문자열로 설정
    public void setInterestsList(List<String> interestsList) {
        if (interestsList == null || interestsList.isEmpty()) {
            this.interests = null;
        } else {
            this.interests = String.join(",", interestsList);
        }
    }
}