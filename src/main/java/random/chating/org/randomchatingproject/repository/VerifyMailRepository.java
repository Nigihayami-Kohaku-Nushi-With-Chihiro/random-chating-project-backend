package random.chating.org.randomchatingproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import random.chating.org.randomchatingproject.entity.VerifyMails;

import java.util.Optional;

public interface VerifyMailRepository extends JpaRepository<VerifyMails, Long> {
    Optional<VerifyMails> findByCode(String code);
}
