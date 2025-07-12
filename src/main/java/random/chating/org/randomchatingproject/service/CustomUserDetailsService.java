package random.chating.org.randomchatingproject.service;

import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * JWT 필터에서 호출됨
     * JWT 토큰의 Subject(userId) 또는 username으로 사용자 정보 조회
     */
    @Override
    public UserDetails loadUserByUsername(String usernameOrUserId) throws UsernameNotFoundException {
        log.debug("JWT에서 사용자 정보 로드 시도: {}", usernameOrUserId);

        User user = null;

        // ⭐ 먼저 userId(숫자)로 조회 시도
        try {
            Long userId = Long.parseLong(usernameOrUserId);
            user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                log.debug("userId로 사용자 발견: userId={}, username={}", userId, user.getUsername());
            }
        } catch (NumberFormatException e) {
            // 숫자가 아니면 username으로 조회
            log.debug("숫자가 아니므로 username으로 조회: {}", usernameOrUserId);
        }

        // userId로 찾지 못했으면 username으로 조회
        if (user == null) {
            user = userRepository.findByUsername(usernameOrUserId).orElse(null);
            if (user != null) {
                log.debug("username으로 사용자 발견: {}", user.getUsername());
            }
        }

        // 둘 다 실패하면 예외
        if (user == null) {
            log.warn("사용자를 찾을 수 없습니다: {}", usernameOrUserId);
            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + usernameOrUserId);
        }

        // 계정 상태 확인 및 로깅
        validateAccountStatus(user);

        log.debug("사용자 정보 로드 완료: userId={}, username={}, email={}, gender={}, age={}",
                user.getId(), user.getUsername(), user.getEmail(), user.getGender(), user.getAge());

        // User Entity를 바로 반환 (UserDetails 구현했으므로)
        return user;
    }

    /**
     * 계정 상태 검증 및 로깅
     */
    private void validateAccountStatus(User user) {
        if (!user.isEnabled()) {
            log.warn("비활성화된 계정: {}", user.getUsername());
        }
        if (!user.isAccountNonLocked()) {
            log.warn("잠긴 계정: {}", user.getUsername());
        }
        if (!user.isAccountNonExpired()) {
            log.warn("만료된 계정: {}", user.getUsername());
        }
        if (!user.isCredentialsNonExpired()) {
            log.warn("비밀번호 만료된 계정: {}", user.getUsername());
        }
    }

    /**
     * 사용자 존재 여부 확인
     */
    public boolean existsByUsername(String username) {
        boolean exists = userRepository.existsByUsername(username);
        log.debug("사용자 존재 여부: {} = {}", username, exists);
        return exists;
    }

    /**
     * 이메일 존재 여부 확인
     */
    public boolean existsByEmail(String email) {
        boolean exists = userRepository.existsByEmail(email);
        log.debug("이메일 존재 여부: {} = {}", email, exists);
        return exists;
    }

    // ========== 계정 관리 메서드들 ==========

    /**
     * 로그인 성공 처리
     */
    @Transactional
    public void recordSuccessfulLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            log.info("로그인 성공: {}", username);
            // 필요시 마지막 로그인 시간 업데이트 등
        });
    }

    /**
     * 로그인 실패 처리
     */
    @Transactional
    public void recordFailedLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            log.warn("로그인 실패: {}", username);
            // 필요시 실패 횟수 증가 등
        });
    }

    /**
     * 계정 잠금
     */
    @Transactional
    public void lockAccount(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setAccountNonLocked(false);
            userRepository.save(user);
            log.warn("계정 잠금: {}", username);
        });
    }

    /**
     * 계정 잠금 해제
     */
    @Transactional
    public void unlockAccount(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setAccountNonLocked(true);
            userRepository.save(user);
            log.info("계정 잠금 해제: {}", username);
        });
    }

    /**
     * 계정 비활성화
     */
    @Transactional
    public void disableAccount(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setEnabled(false);
            userRepository.save(user);
            log.info("계정 비활성화: {}", username);
        });
    }

    /**
     * 계정 활성화
     */
    @Transactional
    public void enableAccount(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setEnabled(true);
            userRepository.save(user);
            log.info("계정 활성화: {}", username);
        });
    }

    // ========== 통계 조회 메서드들 ==========

    /**
     * 전체 사용자 수
     */
    public long getTotalUsers() {
        return userRepository.count();
    }

    /**
     * 활성 사용자 수
     */
    public long getActiveUsers() {
        return userRepository.countByEnabledTrue();
    }

    /**
     * 비활성 사용자 수
     */
    public long getDisabledUsers() {
        return userRepository.countByEnabledFalse();
    }
}