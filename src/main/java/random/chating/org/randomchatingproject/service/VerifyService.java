package random.chating.org.randomchatingproject.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.VerifyMails;
import random.chating.org.randomchatingproject.repository.UserRepository;
import random.chating.org.randomchatingproject.repository.VerifyMailRepository;

import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class VerifyService {

    private final VerifyMailRepository verifyMailRepository;
    private final UserRepository userRepository; // 추가 필요
    private final MailgunService mailgunService;

    @Transactional
    public VerifyResponse verifyMail(String email, String code) {
        Optional<VerifyMails> verifyMail = verifyMailRepository.findByEmail(email);

        if (verifyMail.isPresent()) {
            if (verifyMail.get().getCode().equals(code)) {
                // 인증 성공
                verifyMailRepository.deleteByCode(code);

                // 유저 인증 상태 업데이트
                Optional<User> user = userRepository.findByEmail(email);
                if (user.isPresent()) {
                    user.get().setVerified(true);
                    userRepository.save(user.get());
                }

                return new VerifyResponse(true, "인증 성공");
            } else {
                // 코드 불일치
                return new VerifyResponse(false, "인증코드가 올바르지 않습니다");
            }
        }

        // 인증 메일이 없음
        return new VerifyResponse(false, "인증 요청을 찾을 수 없습니다");
    }

    @Transactional
    public ResponseEntity<VerifyService.VerifyResponse> resendVerifyMail(String email) {
        Optional<VerifyMails> verifyMail = verifyMailRepository.findByEmail(email);

        if (verifyMail.isPresent()) {
            VerifyMails verifyMailsResult = verifyMail.get();

            String newCode = generateSixDigitCode();

            // 기존 코드를 새 코드로 업데이트
            verifyMailsResult.setCode(newCode);
            verifyMailRepository.save(verifyMailsResult);

            // 이메일 전송
            mailgunService.sendMail(email, "🎉 랜덤채팅 회원가입 인증",
                    "회원가입을 환영합니다! 🎊\n\n" +
                            "계정을 활성화하려면 다음 인증코드를 입력해주세요:\n\n" +
                            "📱 인증코드: " + newCode + "\n\n" +
                            "랜덤채팅에서 새로운 만남을 시작해보세요!");

            return ResponseEntity.ok(new VerifyService.VerifyResponse(true, "인증코드가 재전송되었습니다"));
        } else {
            // 인증 요청이 없는 경우
            return ResponseEntity.ok(new VerifyService.VerifyResponse(false, "인증 요청을 찾을 수 없습니다"));
        }
    }

    private String generateSixDigitCode() {
        Random random = new Random();
        int number = random.nextInt(900000) + 100000; // 100000 ~ 999999
        return String.valueOf(number);
    }

    public static class VerifyResponse {
        public boolean success;
        public String message;

        // 이 부분이 문제 - void가 아니라 클래스명과 같아야 함
        public VerifyResponse(boolean success, String message) {  // void 제거하고 클래스명으로 변경
            this.success = success;
            this.message = message;
        }
    }
}