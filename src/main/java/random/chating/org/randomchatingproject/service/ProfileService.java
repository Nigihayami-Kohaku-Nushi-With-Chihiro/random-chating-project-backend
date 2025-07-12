package random.chating.org.randomchatingproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import random.chating.org.randomchatingproject.dto.ProfileUpdateRequest;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.UserProfile;
import random.chating.org.randomchatingproject.repository.UserRepository;
import random.chating.org.randomchatingproject.repository.UserProfileRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    /**
     * 프로필 업데이트
     */
    public User updateProfile(Long userId, ProfileUpdateRequest request) {
        log.info("프로필 업데이트 시작: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다"));

        // 닉네임 변경 (username과 다를 수 있음)
        if (request.getNickname() != null && !request.getNickname().trim().isEmpty()) {
            // 닉네임 중복 체크 (현재 사용자 제외)
            if (!user.getUsername().equals(request.getNickname()) &&
                    userRepository.existsByUsername(request.getNickname())) {
                throw new RuntimeException("이미 사용 중인 닉네임입니다");
            }
            user.setUsername(request.getNickname());
        }

        // 나이 업데이트
        if (request.getAge() != null) {
            if (request.getAge() < 18 || request.getAge() > 100) {
                throw new RuntimeException("나이는 18세 이상 100세 이하여야 합니다");
            }
            user.setAge(request.getAge());
        }

        // 사용자 기본 정보 저장
        User savedUser = userRepository.save(user);

        // 확장 프로필 정보 처리 (없으면 자동 생성)
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(UserProfile.builder()
                        .userId(userId)
                        .profileViews(0)
                        .totalChats(0)
                        .build());

        // 프로필 정보 업데이트
        if (request.getBio() != null) {
            if (request.getBio().length() > 200) {
                throw new RuntimeException("자기소개는 200자를 초과할 수 없습니다");
            }
            profile.setBio(request.getBio());
        }

        if (request.getLocation() != null) {
            profile.setLocation(request.getLocation());
        }

        if (request.getInterests() != null) {
            if (request.getInterests().size() > 5) {
                throw new RuntimeException("관심사는 최대 5개까지 선택할 수 있습니다");
            }
            profile.setInterestsList(request.getInterests());
        }

        userProfileRepository.save(profile);

        log.info("프로필 업데이트 완료: userId={}, username={}", userId, savedUser.getUsername());
        return savedUser;
    }

    /**
     * 닉네임 사용 가능 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        if (username.length() < 2 || username.length() > 20) {
            return false;
        }

        return !userRepository.existsByUsername(username);
    }

    /**
     * 사용자 프로필 조회 (없으면 자동 생성)
     */
    @Transactional(readOnly = true)
    public UserProfile getUserProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElse(null); // null 반환으로 컨트롤러에서 처리
    }

    /**
     * 사용자 프로필 확실하게 조회 (없으면 생성)
     */
    @Transactional
    public UserProfile ensureUserProfile(Long userId) {
        return userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfile newProfile = UserProfile.builder()
                            .userId(userId)
                            .profileViews(0)
                            .totalChats(0)
                            .build();
                    return userProfileRepository.save(newProfile);
                });
    }

    /**
     * 프로필 조회수 증가
     */
    @Transactional
    public void incrementProfileViews(Long userId) {
        UserProfile profile = ensureUserProfile(userId);
        profile.setProfileViews(profile.getProfileViews() + 1);
        userProfileRepository.save(profile);
    }

    /**
     * 채팅 수 증가
     */
    @Transactional
    public void incrementTotalChats(Long userId) {
        UserProfile profile = ensureUserProfile(userId);
        profile.setTotalChats(profile.getTotalChats() + 1);
        userProfileRepository.save(profile);
    }

    /**
     * 프로필 통계 조회
     */
    @Transactional(readOnly = true)
    public ProfileStats getProfileStats(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            throw new RuntimeException("사용자를 찾을 수 없습니다");
        }

        UserProfile profile = getUserProfile(userId);

        return ProfileStats.builder()
                .hasProfileImage(profile != null && profile.getProfileImageUrl() != null)
                .interestsCount(profile != null && profile.getInterests() != null ?
                        profile.getInterestsList().size() : 0)
                .profileCompleteness(calculateProfileCompleteness(userId))
                .totalChats(profile != null ? profile.getTotalChats() : 0)
                .profileViews(profile != null ? profile.getProfileViews() : 0)
                .build();
    }

    /**
     * 프로필 완성도 계산 (퍼센트)
     */
    private int calculateProfileCompleteness(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return 0;

        UserProfile profile = getUserProfile(userId);

        int score = 0;
        int totalFields = 8;

        // 기본 정보 체크 (4개 항목)
        if (user.getUsername() != null && !user.getUsername().isEmpty()) score++;
        if (user.getEmail() != null && !user.getEmail().isEmpty()) score++;
        if (user.getAge() != null) score++;
        if (user.getGender() != null) score++;

        // 확장 프로필 체크 (4개 항목)
        if (profile != null) {
            if (profile.getBio() != null && !profile.getBio().isEmpty()) score++;
            if (profile.getLocation() != null && !profile.getLocation().isEmpty()) score++;
            if (profile.getInterests() != null && !profile.getInterests().isEmpty()) score++;
            if (profile.getProfileImageUrl() != null && !profile.getProfileImageUrl().isEmpty()) score++;
        }

        return (score * 100) / totalFields;
    }

    /**
     * 사용자 검색 (닉네임 기반)
     */
    @Transactional(readOnly = true)
    public java.util.List<User> searchUsers(String keyword, Long currentUserId) {
        return userRepository.findAll().stream()
                .filter(user -> !user.getId().equals(currentUserId))
                .filter(user -> user.getUsername().toLowerCase().contains(keyword.toLowerCase()))
                .limit(10)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 프로필 추천 사용자 조회 (나이, 성별 기반)
     */
    @Transactional(readOnly = true)
    public java.util.List<User> getRecommendedUsers(Long userId, int limit) {
        User currentUser = userRepository.findById(userId).orElse(null);
        if (currentUser == null) return java.util.Collections.emptyList();

        // 간단한 추천 로직: 반대 성별, 스마트한 나이대 계산
        User.Gender targetGender = currentUser.getGender() == User.Gender.MALE ?
                User.Gender.FEMALE : User.Gender.MALE;

        // ⭐ 사용자 나이 기반으로 스마트하게 범위 계산
        int userAge = currentUser.getAge() != null ? currentUser.getAge() : 25;
        int minAge = Math.max(18, userAge - 10); // 최소 18세, 본인보다 10살 아래까지
        int maxAge = Math.min(100, userAge + 15); // 최대 100세, 본인보다 15살 위까지

        return userRepository.findByGenderAndAgeBetweenAndEnabledTrue(targetGender, minAge, maxAge)
                .stream()
                .filter(user -> !user.getId().equals(userId))
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    // 프로필 통계용 내부 클래스
    @lombok.Builder
    @lombok.Data
    public static class ProfileStats {
        private boolean hasProfileImage;
        private int interestsCount;
        private int profileCompleteness;
        private int totalChats;
        private int profileViews;
    }
}