package random.chating.org.randomchatingproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import random.chating.org.randomchatingproject.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // ========== 매칭 시스템용 메서드들 ==========

    // 성별별 활성 사용자 수
    long countByGenderAndEnabledTrue(User.Gender gender);

    // 활성 사용자 수
    long countByEnabledTrue();
    long countByEnabledFalse();

    // 성별별 사용자 조회
    List<User> findByGenderAndEnabledTrue(User.Gender gender);

    // 나이대별 조회 (선택사항)
    List<User> findByAgeBetweenAndEnabledTrue(Integer minAge, Integer maxAge);

    // 성별 + 나이대 조회 (선택사항)
    List<User> findByGenderAndAgeBetweenAndEnabledTrue(
            User.Gender gender, Integer minAge, Integer maxAge);
}