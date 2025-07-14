package random.chating.org.randomchatingproject.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import random.chating.org.randomchatingproject.entity.VerifyMails;

import java.util.Optional;

@Transactional
public interface VerifyMailRepository extends JpaRepository<VerifyMails, Long> {

    Optional<VerifyMails> findByCode(String code);

    Optional<VerifyMails> findByEmail(String email);

    void deleteByCode(String code);
}
