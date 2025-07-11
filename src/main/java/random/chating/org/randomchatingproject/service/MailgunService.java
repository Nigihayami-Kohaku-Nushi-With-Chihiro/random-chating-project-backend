package random.chating.org.randomchatingproject.service;

import org.springframework.stereotype.Service;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.springframework.beans.factory.annotation.Value;
import random.chating.org.randomchatingproject.dto.VerifyMailDto;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.entity.VerifyMails;
import random.chating.org.randomchatingproject.repository.UserRepository;
import random.chating.org.randomchatingproject.repository.VerifyMailRepository;


import java.util.Optional;

@Service
public class MailgunService {

    private final UserRepository userRepository;
    private final VerifyMailRepository verifyMailRepository;

    public MailgunService(UserRepository userRepository,
                          VerifyMailRepository verifyMailRepository) {
        this.userRepository = userRepository;
        this.verifyMailRepository = verifyMailRepository;
    }

    @Value("${mailgun.api.key}")
    private String mailgun_api_key;

    @Value("${mailgun.domain}")
    private String mailgun_domain;

    public void sendMail(String to, String subject, String text) {
        try {
            Unirest.post("https://api.mailgun.net/v3/" + mailgun_domain + "/messages")
                    .basicAuth("api", mailgun_api_key)
                    .queryString("from", "Your Service <postmaster@" + mailgun_domain + ">")
                    .queryString("to", to)
                    .queryString("subject", subject)
                    .queryString("text", text)
                    .asJson();
        } catch (UnirestException e) {
            throw new RuntimeException("메일 전송 실패", e);
        }
    }

    public void verifyMail(VerifyMailDto verifyMailDto) {
        Optional<VerifyMails> verifyMails = verifyMailRepository.findByCode(verifyMailDto.getCode());

        if (verifyMails.isEmpty()) {
            throw new IllegalArgumentException("해당 이메일의 인증 코드가 없습니다.");
        }
        verifyMailRepository.delete(verifyMails.get());

        Optional<User> user = userRepository.findByEmail(verifyMails.get().getEmail());

        User foundUser =  user.get();
        foundUser.setVerified(true);

        userRepository.save(foundUser);
    }
}